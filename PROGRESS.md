# Supply-Core — Progreso del backend

> Última actualización: 2026-10-06 · Estado: **pasos 0 a 16 completos + 11-bis (despachos)**.
> **316 pruebas en verde** (también con la JVM en inglés), Javadoc sin advertencias (`doclint=all`), 15 migraciones Flyway, 36 controllers.
> Detalle vivo para Claude: `.claude/skills/supply-core/references/estado.md` y `plan-backend.md`.

---

## 0. Resumen: en qué vamos

**Hecho (backend, pasos 0–16 + 11-bis).** El ciclo de compra funciona de punta a punta hasta que el proveedor despacha, y los pagos de la orden quedan registrados:
necesidad → solicitud → aprobación → RFQ → cotizaciones (formulario o archivo leído con IA) → análisis y ranking → adjudicación → orden de compra → pagos (anticipos o parciales, con comprobante) → el proveedor acepta, prepara y despacha. Además: usuarios y roles, proveedores, inventario con kardex, documentos, notificaciones, auditoría y despachos de salida.

**Estamos en:** paso 17, recepción.

**Falta del backend (pasos 17–19):**

| Paso | Qué es | Con qué se conecta |
|---|---|---|
| 17 | **Recepción** (UC-13): el almacenista recibe lo despachado; lo aceptado sube el stock, lo faltante deja saldo abierto y el exceso queda en revisión. Al cerrar, se evalúa al proveedor | `MovimientoInventarioService.registrarEntradaCompra`, `EvaluacionProveedorService.registrar`, orden → RECIBIDA_PARCIAL / RECIBIDA_TOTAL / CERRADA |
| 18 | **Reportes y tableros** (UC-15): gasto, inventario y desempeño de proveedores, con filtros por fecha, categoría y proveedor; solo lectura | Todas las tablas anteriores |
| 19 | **Cierre del backend**: prueba de punta a punta del ciclo completo, revisión de seguridad, Swagger completo, limpieza de pendientes técnicos y commit | Todo |

**Después del backend:**
1. **Frontend en Angular** (`frontend/`): pantallas por rol que consumen esta API (login, bandejas de aprobación, RFQ, portal del proveedor, inventario, reportes).
2. **Integración y prueba con IA real** (Ollama o Claude) con cotizaciones reales.
3. **Despliegue** (opcional para el proyecto formativo): perfiles `dev`/`prod`, secretos por variables de entorno.

**Cómo termina:** el proyecto está completo cuando una compra recorre todo el ciclo desde la interfaz —desde que alguien la pide hasta que la mercancía entra al inventario, el pago queda registrado y el proveedor queda evaluado—, cada paso queda auditado, las pruebas pasan y la sustentación se apoya en la guía `docs/Supply-Core-por-dentro.pdf`.

---

## 1. Pasos completados

| Paso | Módulo | Qué quedó funcionando |
|---|---|---|
| 0 | `common/`, `config/` | Excepciones con código HTTP, `GlobalExceptionHandler`, `ErrorResponse`, `PageResponse`, consecutivos (`SOL-000001`), CORS, Swagger |
| 1 | `security/` | Login (UC-01) con JWT HS256 (jjwt 0.13.0), filtro propio, `@RequiereRol`, `@UsuarioInterno`, BCrypt 12 — **sin Spring Security** |
| 2 | `usuario/` | CRUD de usuarios, perfil propio, roles; validaciones en los requests |
| 3 | `auditoria/` | Registro de acciones sensibles (éxito y fallo, con IP) + consulta UC-16 |
| 4 | `empresa/` | Empresa única, sedes, centros de costo, políticas de aprobación por rango de monto |
| 5 | `catalogo/` | Categorías, unidades de medida con magnitud y conversión |
| 6 | `inventario/` | Productos (UC-02), bodegas, stock por bodega, movimientos y kardex (UC-03) |
| 7 | `documento/` | Subida y descarga de archivos fuera de la BD con reglas de acceso por entidad |
| 8 | `notificacion/` | Bandeja interna + correo (hoy simulado en log), canales configurables por evento; avisos de proveedor pendiente y stock bajo mínimo |
| 9 | `proveedor/` | Autorregistro público → PENDIENTE → ADMIN_COMPRAS habilita / rechaza / suspende; portal; edición de perfil; filtro por categoría; evaluación UC-14 con `score_actual` |
| 10 | `compra/solicitud/` | Solicitudes de compra (UC-04) con monto estimado y aprobación por política (UC-05): devolver, rechazar, segregación de funciones |
| 11 | `compra/rfq/` | RFQ desde solicitud aprobada (UC-06): invitación automática por categoría con alertas, abrir/cerrar/cancelar, cierre automático por fecha límite, vista del proveedor invitado |
| 11-bis | `despacho/` | Remisión de salida (lo que se vende o entrega): varias líneas, confirmar descuenta el stock todo o nada, anular lo devuelve. Sin precios ni facturación |
| 12 | `compra/cotizacion/` | El proveedor cotiza (UC-07) por formulario o archivo; Java calcula montos y precio normalizado; sobre cerrado hasta que la RFQ cierre |
| 13 | `ia/` + `compra/analisis/` | Lectura de cotizaciones por archivo con IA (ninguna / Ollama local / Claude) con confianza por dato y revisión humana; motor de comparación en Java con pesos versionados, alertas y ranking publicable |
| 14 | `compra/adjudicacion/` | Adjudicación (UC-09) sobre el análisis publicado, justificación si no sigue la recomendación, aprobación por política con segregación; marca ganadora y cierra RFQ/solicitud como ADJUDICADA |
| 15 | `compra/orden/` | Orden de compra (UC-10) inmutable con versiones, en la unidad del inventario; flujo del proveedor (UC-12): aceptar, rechazar, preparar, despachar con evidencia |
| 16 | `pago/` | Pagos de la orden (UC-11) desde que se emite: anticipos y parciales sin superar el total, comprobante (si falta, evidencia pendiente), anulación con motivo, segregación de funciones; el proveedor ve lo pagado y el saldo |

La evaluación UC-14 ya existe; la dispara la recepción (paso 17).

### Migraciones Flyway (`backend/src/main/resources/db/migration/`)
| Versión | Contenido |
|---|---|
| V1 | Esquema completo (46 tablas), corregido respecto al Anexo A del documento |
| V2 | Empresa demo, 9 roles, criterios y pesos de evaluación, unidades base |
| V3 | Tabla `consecutivo` (SOL, RFQ, OC) |
| V4 | `proveedor.estado` (reemplaza `activo`), revisor y motivo; 4 categorías demo |
| V5 | `unidad_medida.magnitud` + `activo`; unidades CAJA24, G, ML, M, CM |
| V6 | `movimiento_inventario.stock_resultante_destino` (traslados) + índices |
| V7 | `config_notificacion`: canales INTERNA/EMAIL de PROVEEDOR_PENDIENTE y STOCK_BAJO_MINIMO |
| V8 | `solicitud_compra`: monto estimado, monto incompleto, política asignada, fecha de envío + eventos de solicitud |
| V9 | `rfq`: fechas de apertura y cierre, índices para el job de cierre y el portal + eventos de RFQ |
| V10 | `despacho` y `despacho_linea`, tipos de movimiento SALIDA_VENTA y ANULACION_DESPACHO, consecutivo DES |
| V11 | `cotizacion_linea`: una línea por producto de la RFQ y CHECKs de valores + evento de cotización recibida |
| V12 | IA y análisis: un procesamiento por cotización, explicación general del ranking, CHECK de confianza, evento de revisión |
| V13 | `adjudicacion`: política que exigió la aprobación, índices y eventos de adjudicación |
| V14 | `orden_compra`: subtotal, impuestos, emisor; IVA por línea; CHECKs, índices y eventos de la orden |
| V15 | `pago`: estado ANULADO con motivo, quién y cuándo; índices y eventos de pago |
| **Próximo libre** | **V16** |

---

## 2. Archivos principales y su responsabilidad

Todo el código está en `backend/src/main/java/com/proyecto/supply_core/`. Cada módulo tiene `entity/ repository/ dto/ service/ controller/` (+ `enums/`, `validation/` cuando aplica).

### Transversal
| Archivo | Responsabilidad |
|---|---|
| `common/exception/GlobalExceptionHandler` | Traduce todo error a `ErrorResponse {codigo, mensaje, detalles[], timestamp}`: 400 validación, 401, 403, 404, 405, 409 negocio, 413 archivo grande, 415, 500 |
| `common/exception/*Exception` | `NegocioException` 409 (con código), `NoEncontradoException` 404, `NoAutenticadoException` 401, `AccesoDenegadoException` 403, `ParametroInvalidoException` 400 |
| `common/util/Ordenamiento` | Lista blanca de campos ordenables (evita 500 y ordenar por datos sensibles) |
| `common/util/ConsecutivoGenerator` | Consecutivos atómicos con `UPDATE … LAST_INSERT_ID()` |
| `common/util/EmpresaUnica` | Id de la única empresa (V2) |
| `config/WebConfig`, `OpenApiConfig` | CORS para Angular, interceptor de roles, Swagger con "Authorize" |

### Seguridad y usuarios
| Archivo | Responsabilidad |
|---|---|
| `security/jwt/JwtUtil` | Emite y valida el JWT (emisor `supply-core`, expira 8 h, reloj inyectable para pruebas) |
| `security/filter/AuthFilter` | Exige `Bearer` en `/api/**` salvo `/api/auth/login` y `/api/portal/registro/**` |
| `security/interceptor/RolInterceptor` | Aplica `@RequiereRol` (alguno de los roles) y `@UsuarioInterno` (cualquiera menos PROVEEDOR) |
| `security/service/AuthService` | Login: mismo mensaje y mismo tiempo para correo inexistente o clave errada; informa el estado del proveedor; audita |
| `security/context/SesionActual` | Usuario de la petición desde cualquier Service |
| `usuario/service/UsuarioService` | Alta, edición, activar/desactivar, contraseñas; nunca se queda sin ADMIN_SISTEMA |
| `usuario/validation/*` | `@CorreoUnico`, `@RolesExistentes`, `@ProveedorConsistente`, `ReglasPassword` |

### Negocio
| Archivo | Responsabilidad |
|---|---|
| `auditoria/service/AuditoriaService` | `exito()` se une a la transacción; `fallo()` usa `REQUIRES_NEW` (queda aunque la operación se revierta) |
| `proveedor/service/ProveedorService` | Registro público, habilitar, rechazar y suspender con transiciones válidas (`EstadoProveedor.puedePasarA`) |
| `empresa/service/PoliticaAprobacionService` | Rangos `[desde, hasta)` sin superposición; `aplicable(tipo, monto)` para los pasos 10 y 14 |
| `catalogo/service/UnidadMedidaService` | `convertir(cantidad, origen, destino)` solo dentro de la misma magnitud, 4 decimales |
| `inventario/service/MovimientoInventarioService` | **Único** que modifica el stock (ver decisiones) |
| `inventario/service/ProductoService` | Catálogo de productos, stock total, alertas bajo mínimo |
| `documento/service/DocumentoService` | Valida, guarda, lista y descarga archivos; delega el acceso a la regla de cada entidad |
| `documento/storage/LocalStorageClient` | Guarda en `backend/almacenamiento/` (detrás de la interfaz `StorageClient`) |
| `proveedor/service/ProveedorReglaDocumentos` | Primera `ReglaAccesoDocumentos` (documentos del proveedor) |
| `notificacion/service/NotificacionService` | `notificar(usuario)` / `notificarRol(rol)`: guarda la interna en la transacción y envía el correo después del commit; bandeja y configuración |
| `notificacion/email/EmailClient` | Interfaz de correo; `LogEmailClient` solo escribe en el log (cambiar por SMTP sin tocar los Services) |

### Compras e IA
| Archivo | Responsabilidad |
|---|---|
| `proveedor/service/EvaluacionProveedorService` | UC-14: `registrar(proveedor, orden, indicadores)` y recálculo de `score_actual` (lo llamará la recepción) |
| `compra/solicitud/service/SolicitudService` | Crear, editar, enviar (monto estimado + política) y cancelar solicitudes |
| `compra/solicitud/service/AprobacionSolicitudService` | Aprobar, rechazar o devolver con segregación de funciones; bandeja del aprobador |
| `compra/rfq/service/RfqService` + `RfqCierreJob` | RFQ desde solicitud aprobada, invitación por categoría, abrir/cerrar/cancelar, cierre automático por fecha |
| `compra/cotizacion/service/CotizacionService` | El proveedor cotiza por formulario o archivo; sobre cerrado mientras la RFQ esté abierta |
| `compra/cotizacion/service/CalculadoraCotizacion` | Neto, impuestos, total y precio normalizado (solo Java calcula dinero) |
| `ia/client/IAClient` (+ `SinIAClient`, `OllamaIAClient`, `ClaudeIAClient`) | Lectura de cotizaciones y redacción de explicaciones, elegida por `app.ia.proveedor` |
| `ia/service/ExtraccionService` + `ProcesadorExtraccion` | Lectura asíncrona después del commit, confianza por dato, confirmación, digitación y reintento |
| `compra/analisis/service/MotorComparacion` | Java puro: puntajes por criterio, score ponderado, ranking y alertas |
| `compra/analisis/service/AnalisisService` / `PesosService` | Generar y publicar el análisis con snapshot de pesos; pesos versionados que suman 100 |
| `compra/adjudicacion/service/AdjudicacionService` | Adjudicar sobre el análisis publicado, aprobación por política, efectos sobre cotizaciones, RFQ y solicitud |
| `compra/orden/service/OrdenCompraService` / `OrdenProveedorService` | Emitir, versionar y cancelar órdenes; aceptar, rechazar, preparar y despachar (proveedor) |
| `despacho/service/DespachoService` | Remisión de salida (venta o entrega): confirma todo o nada y anula devolviendo stock |
| `pago/service/PagoService` + `PagoReglaDocumentos` | Registrar, adjuntar comprobante y anular pagos; saldo por orden (todas sus versiones); segregación de funciones |

### Configuración y pruebas
| Archivo | Responsabilidad |
|---|---|
| `backend/src/main/resources/application.yaml` | Configuración común (sin secretos) |
| `application-local.yaml` | Contraseña de MySQL y secretos locales — **en .gitignore** (plantilla: `application-local.example.yaml`) |
| `src/test/.../common/web/WebTestBase` | Base de las pruebas `@WebMvcTest`: token real con `bearer("ROL")`, repositorios simulados |
| `CLAUDE.md` + `.claude/skills/supply-core/` | Contexto del proyecto para Claude (reglas, modelo de datos, estado) |

---

## 3. Decisiones de diseño clave

**Seguridad**
- JWT propio con jjwt, sin Spring Security (decisión del usuario). Un solo login: el proveedor es un `usuario` con `proveedor_id` y rol PROVEEDOR, y solo entra si su proveedor está HABILITADO.
- El JWT no guarda estado: un usuario desactivado conserva su token hasta que vence (8 h).
- Secretos solo en `application-local.yaml` o en variables de entorno. Si falta `JWT_SECRET`, en desarrollo se genera uno aleatorio.

**Datos y reglas**
- El esquema lo maneja solo Flyway (`ddl-auto: none`). Nunca se edita un `V*` ya aplicado.
- Nada se borra: usuarios, productos, sedes, bodegas, categorías y unidades se desactivan; la auditoría, los movimientos y los documentos son `@Immutable`.
- Las validaciones van en los requests, con anotaciones propias y mensajes en español explícitos (no dependen del idioma de la JVM). En el Service quedan solo las reglas que dependen del estado.
- Las FK entre módulos se guardan como `Long` (`usuario.centroCostoId`, `proveedorId`), y su existencia se valida con anotaciones (`@CentroCostoActivo`, `@SedeActiva`...).

**Inventario (regla que no se negocia)**
- El stock solo cambia por `MovimientoInventarioService`: `INSERT … ON DUPLICATE KEY` + `SELECT … FOR UPDATE`. En los traslados se bloquea en orden de id para evitar interbloqueos, y el stock nunca queda negativo. Lo verifiqué con 20 salidas concurrentes sobre un stock de 10: 10 aceptadas y 10 rechazadas.
- Por la API solo se registran SALIDA, AJUSTES (con motivo) y TRASLADO. ENTRADA_COMPRA y DEVOLUCION_PROVEEDOR solo llegan desde la recepción (paso 17). No existe un endpoint para editar el stock.
- Las unidades son inmutables (código, magnitud y factor). La unidad de un producto no cambia si ya tiene movimientos.

**Documentos**
- Los archivos van fuera de la BD; en la BD solo queda la metadata.
- La clave en disco la genera el sistema (`AAAA/MM/<UUID>.ext`): el nombre del usuario nunca forma parte de la ruta, lo que evita `../`.
- El tipo se detecta por extensión **y** por la firma real (magic bytes). Máximo 10 MB.
- Si la transacción se revierte, el archivo se borra. Las descargas salen como adjunto con `nosniff`. No hay borrado.
- Cada módulo define su `ReglaAccesoDocumentos`; si una entidad no tiene regla, responde 409.

**Solicitudes y aprobación**
- Monto estimado = Σ cantidad (convertida a la unidad del producto) × `costo_referencia`. Se calcula al enviar.
- Si algún producto no tiene costo de referencia, se exige la política de mayor tramo (nada pasa sin control). Sin políticas configuradas, la solicitud queda APROBADA directa.
- La política se guarda en la solicitud al enviarla: si después cambia, no afecta las solicitudes ya enviadas.
- Decide solo el rol de esa política y nunca el propio solicitante. La fila se bloquea para que dos aprobadores no decidan a la vez.

**RFQ y cotizaciones**
- Sin invitados explícitos se invita a todos los proveedores HABILITADOS de las categorías; si una categoría no tiene ninguno, se alerta.
- Sobre cerrado: compras ve quién cotizó, pero no los precios ni los archivos hasta que la RFQ cierre.
- Una cotización por proveedor y RFQ; después de enviada no se edita. La RFQ se bloquea al cotizar para no cruzarse con el cierre automático.
- `Rfq.lineas` es un `Set` ordenado: con varios invitados, una `List` se duplicaba al cargarse junto con ellos (bug corregido en el paso 12).

**IA y análisis**
- La IA es intercambiable (ninguna, Ollama local o Claude) y solo lee o redacta; Java interpreta los números y calcula todo el dinero.
- Un dato crítico con confianza menor a 0.80 exige confirmación humana; un renglón ilegible no se inventa. Publicar el análisis se bloquea mientras haya lecturas pendientes.
- La lectura corre en segundo plano después del commit y sin transacción abierta durante la llamada a la IA.
- Cada análisis guarda la versión de pesos que usó; los proveedores sin historial se puntúan neutros (50) con una alerta. Las alertas nunca excluyen ofertas.

**Adjudicación y orden**
- No seguir la recomendación exige justificación; la política ADJUDICACION decide si hace falta aprobación, y nadie aprueba lo que adjudicó.
- La orden va en la unidad del producto (la del inventario) y conserva el neto exacto de la cotización.
- La orden es inmutable: un cambio crea la versión siguiente (mismo consecutivo) y la anterior queda REEMPLAZADA; los precios no cambian.

**Pagos**
- El sistema solo registra lo que ya se pagó por fuera: no hay pasarela ni contabilidad.
- Se paga desde que la orden se emite (anticipos y pagos parciales) y siempre sobre la versión vigente. La suma de los pagos de todas las versiones nunca supera el total vigente, y una versión nueva no puede quedar por debajo de lo ya pagado.
- Segregación de funciones: no registra el pago quien pidió la compra, quien la aprobó, quien adjudicó ni quien emitió la orden.
- Un pago no se edita ni se borra: el comprobante se adjunta una sola vez (sin él queda con evidencia pendiente) y un error se anula con motivo.

**Despacho**
- Las ventas o entregas salen con un documento de varias líneas; confirmar descuenta todo o nada en orden de producto, y anular devuelve el stock.

**Notificaciones**
- Cada evento/canal se activa o desactiva en `config_notificacion` (sin fila = activo). Solo se notifica a usuarios activos.
- El correo nunca sale si la operación se revierte, y si el correo falla la operación sigue (queda en log).
- El aviso de stock sale solo cuando el total **cruza** el mínimo, no en cada salida posterior.
- Cada usuario solo ve y marca sus notificaciones (una ajena responde 404).

**Auditoría**
- Los éxitos se registran junto con la operación; los fallos en una transacción aparte. Nunca se guardan contraseñas. Las acciones se agregan a `AccionAuditoria` y `EntidadAuditada`.

**Calidad**
- Javadoc en toda clase y método (incluidos los privados).
- Cada módulo tiene pruebas de Service (Mockito) y de controller (`@WebMvcTest`).
- Prueba E2E contra el MySQL local al cerrar cada paso, limpiando la base al terminar.

---

## 4. Lo que sigue

### Siguiente: paso 17. Pasos 17 a 19 (orden obligatorio, detalle en `plan-backend.md`)
Para notificar en los pasos nuevos: agregar el evento a `EventoNotificacion`, sembrar sus canales en una migración y llamar `notificacionService.notificarRol(...)`.

| Paso | Módulo | Notas para retomarlo |
|---|---|---|
| 17 | `recepcion/` | UC-13; al cerrar, `EvaluacionProveedorService.registrar(...)`; `MovimientoInventarioService.registrarEntradaCompra(...)` / `registrarDevolucionProveedor(...)`; regla de documentos RECEPCION |
| 18 | `reporte/` | UC-15 |
| 19 | Cierre | Pruebas de punta a punta, revisión de seguridad, Swagger completo |

### Pendientes técnicos conocidos
- Pasar `@Value` a `@ConfigurationProperties` (CORS y admin) y crear perfiles `dev`/`prod`. En `prod`, exigir `JWT_SECRET`.
- `@DataJpaTest` con Testcontainers cuando haya Docker.
- Correo real: reemplazar `LogEmailClient` por un `EmailClient` SMTP con credenciales en `application-local.yaml`.
- Limitar los intentos del registro público de proveedores (rate limiting por IP) antes de producción.
- Validar la existencia de `proveedorId` al crear usuarios PROVEEDOR desde el admin.
- IA real sin probar: instalar Ollama (`ollama pull qwen2.5vl:3b`) o poner `ANTHROPIC_API_KEY` y probar con una cotización real.
- Orden: decidir si la evidencia de despacho es obligatoria y si se expone `GET /api/ordenes/{id}/versiones` (`findByConsecutivoOrderByVersionAsc` ya lo usan los pagos).
- Pagos: si una orden con pagos se cancela o el proveedor la rechaza, el saldo a favor solo queda visible en el resumen; no hay devolución (fuera de alcance).
- Despacho: carga del cierre de caja desde Excel/CSV (opción C), si la ferretería usa otro programa de caja.
- Frontend Angular: `frontend/` sigue vacío.
- **Commit pendiente:** todo el trabajo está sin commitear en `main` (la rama de VS Code aparece como `feature/modulo1`).

### Cómo retomar
```bash
cd backend
./mvnw test
./mvnw spring-boot:run
```
Para crear el admin la primera vez, define `ADMIN_PASSWORD`. Swagger: `http://localhost:8080/swagger-ui.html`.
