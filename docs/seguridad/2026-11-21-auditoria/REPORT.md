# Reporte de seguridad — Supply-Core

- **Repositorio:** Supply-Core (rama `develop`, commit `2f0ba20`)
- **Perfil de auditoría:** estándar (presupuesto 30 agentes, 28 usados)
- **Fecha:** 2026-11-21
- **Alcance:** backend Spring Boot, frontend Angular, despliegue Docker/nginx. Revisión de código en sandbox; una verificación en vivo bounded contra el stack demo local (red Docker interna, sin red externa, secretos ficticios).
- **Método:** reconocimiento → caza guiada por cobertura (20 unidades) → validación independiente de cada candidato por un verificador distinto → ensamblado de hallazgos. Ningún hallazgo entra sin que un segundo agente lo reproduzca desde el código.

## Resumen ejecutivo

Se confirmaron **10 vulnerabilidades** y quedaron **5 pistas que requieren validación en el despliegue real** (dependen de configuración del operador que no se ve en el código).

De las 10 confirmadas, la más grave (ALTA) es un **salto de aislamiento entre empresas**: un usuario de cualquier empresa (hasta un CAJERO) puede leer los datos de **todas** las empresas a través de la consola de plataforma, usando una ruta con el prefijo codificado (`/api/%70lataforma/empresas`). Esto se demostró contra el stack en ejecución (200 con la lista de todas las empresas frente a 401 en la ruta literal). Es el defecto que rompe la promesa central del producto (multiempresa aislada) y debe corregirse primero.

El resto son, en su mayoría, fallos de autorización/segregación dentro de una misma empresa (costos visibles a cajeros, aprobaciones de supervisor y de compra reutilizables, precios de licitación sellada filtrados) y debilidades de sesión (MFA y revocación que no se reevalúan). No se encontró inyección SQL, deserialización insegura ni ejecución remota de código.

## Hallazgos por severidad

### ALTA

| # | Hallazgo | Dónde | Efecto |
|---|----------|-------|--------|
| 1 | JWT de empresa llega a la consola de plataforma por ruta codificada/matrix | `AuthFilter` + `RolInterceptor` (clasifican sobre `getRequestURI` crudo) | Lectura de **todas** las empresas y sus administradores con un token de cualquier empresa. Demostrado en vivo. |
| 2 | Admin de una empresa modifica el catálogo global `unidad_medida` | `UnidadMedidaController` / `UnidadMedidaService` | Una empresa renombra/desactiva una unidad compartida y **rompe fijación de precios y creación de productos de todas las demás**. |

### MEDIA

| # | Hallazgo | Dónde | Efecto |
|---|----------|-------|--------|
| 3 | Costos de producto expuestos a cualquier rol interno (incl. CAJERO/VENDEDOR) | `ProductoController` GET + `ProductoResponse` | Fuga de `costoReferencia`/`costoPromedio`, datos que la app restringe a dueño/comprador/auditor. |
| 4 | Autorización de supervisor no se ata al monto ni al payload | `AutorizacionService.consumir` | Un cajero reutiliza un PIN aprobado para un GASTO pequeño en un retiro grande → robo de caja que cuadra en cierre ciego. |
| 5 | Nueva versión de orden sube cantidades sin reaprobación | `OrdenCompraService.nuevaVersion` | Un COMPRADOR compromete a la empresa por encima del monto adjudicado sin segundo aprobador; el tope de anticipo sube con el total inflado. |
| 6 | Reintento de extracción IA filtra precios de licitación sellada | `ExtraccionService.reintentar` (falta `exigirSobreAbierto`) | Un comprador lee los precios de la competencia con la RFQ aún ABIERTA. |

### BAJA

| # | Hallazgo | Dónde | Efecto |
|---|----------|-------|--------|
| 7 | Límite de tasa del PIN de supervisor evitable por ruta codificada | `RateLimitFilter` (misma causa que #1) | Se salta el throttle 20/min; mitigado por bloqueo de 5 intentos/15 min por supervisor. |
| 8 | Cambio de rol no revoca sesiones | `UsuarioService.actualizar` | Un admin degradado conserva ADMIN_SISTEMA en su JWT vivo (≤15 min) y puede reautoescalarse. |
| 9 | Refresh no reevalúa el MFA obligatorio de admin | `AuthService.refrescar` | Un usuario promovido a ADMIN_SISTEMA obtiene token de admin sin TOTP vía refresh. |
| 10 | Notificación de revisión filtra los totales de una cotización | `ExtraccionService.guardarResultado` → `avisarRevision` | El comprador ve total impreso y calculado de una cotización antes del cierre. |

### Requieren validación (ver `NEEDS-VALIDATION.md`)

5 pistas dependientes del despliegue: token de recuperación en logs (`LogEmailClient`); expansión no acotada de Excel (POI) y de PDF (PDFBox) que, con `-XX:+ExitOnOutOfMemoryError`, tumbarían el JVM compartido; cubeta de rate-limit compartida detrás del proxy TLS documentado; y el perfil `prod` aceptando los secretos de ejemplo de `.env.example`.

## Causa raíz común (hallazgos #1 y #7)

Los filtros de seguridad clasifican la ruta sobre `request.getRequestURI()` (crudo, con `%NN` y `;matrix`), mientras Spring enruta el handler sobre la ruta **decodificada y normalizada**. Las dos visiones difieren y el token de plataforma / el límite de tasa se aplican a una ruta distinta de la que realmente se despacha. **Corrección única:** clasificar en todos los filtros (`AuthFilter`, `RolInterceptor`, `RateLimitFilter`, `CabecerasSeguridadFilter`) sobre la ruta canonizada (`UrlPathHelper.getPathWithinApplication` / `ServletRequestPathUtils`), y poner una comprobación positiva de token de plataforma en los controladores `/api/plataforma/**`.

## Priorización sugerida

1. **#1** (y de paso #7, misma corrección) — rompe el aislamiento multiempresa. Urgente.
2. **#2** — una empresa puede degradar a todas; mover `unidad_medida` a la consola de plataforma o volverla por empresa.
3. **#4, #5** — pérdida de dinero / segregación de funciones en caja y compras.
4. **#3, #6, #10** — fugas de datos sensibles dentro de la empresa.
5. **#8, #9** — endurecimiento de sesión.
6. Validar las 5 pistas con el dueño del despliegue.

El detalle técnico, traza y remediación con código de cada confirmada está en `FINDINGS-DETAIL.md`. Los datos máquina en `findings.json` (validado contra el esquema). La cobertura revisada en `coverage-ledger.json`.
