# Supply-Core — Progreso del backend

> Última actualización: 2026-10-06 · Estado: **pasos 0 a 14 completos + 11-bis (despachos)**.
> **293 pruebas en verde** (también con la JVM en inglés), Javadoc sin advertencias (`doclint=all`), 13 migraciones Flyway, 32 controllers.
> Detalle vivo para Claude: `.claude/skills/supply-core/references/estado.md` y `plan-backend.md`.

---

## 1. Pasos completados

| Paso | Módulo | Qué quedó funcionando |
|---|---|---|
| 0 | `common/`, `config/` | Excepciones con código HTTP, `GlobalExceptionHandler`, `ErrorResponse`, `PageResponse`, consecutivos (`SOL-000001`), CORS, Swagger |
| 1 | `security/` | Login (UC-01) con JWT HS256 (jjwt 0.13.0), filtro propio, `@RequiereRol`, `@UsuarioInterno`, BCrypt 12 — **sin Spring Security** |
| 2 | `usuario/` | CRUD de usuarios, perfil propio, roles; validaciones en los requests |
| 3 | `auditoria/` | Registro de acciones sensibles (éxito y fallo, con IP) + consulta UC-16 |
| 9 | `proveedor/` | Autorregistro público → PENDIENTE → ADMIN_COMPRAS habilita / rechaza / suspende; portal; edición de perfil; filtro por categoría; evaluación UC-14 con `score_actual` |
| 10 | `compra/solicitud/` | Solicitudes de compra (UC-04) con monto estimado y aprobación por política (UC-05): devolver, rechazar, segregación de funciones |
| 11 | `compra/rfq/` | RFQ desde solicitud aprobada (UC-06): invitación automática por categoría con alertas, abrir/cerrar/cancelar, cierre automático por fecha límite, vista del proveedor invitado |
| 11-bis | `despacho/` | Remisión de salida (lo que se vende o entrega): varias líneas, confirmar descuenta el stock todo o nada, anular lo devuelve. Sin precios ni facturación |
| 12 | `compra/cotizacion/` | El proveedor cotiza (UC-07) por formulario o archivo; Java calcula montos y precio normalizado; sobre cerrado hasta que la RFQ cierre |
| 13 | `ia/` + `compra/analisis/` | Lectura de cotizaciones por archivo con IA (ninguna / Ollama local / Claude) con confianza por dato y revisión humana; motor de comparación en Java con pesos versionados, alertas y ranking publicable |
| 14 | `compra/adjudicacion/` | Adjudicación (UC-09) sobre el análisis publicado, justificación si no sigue la recomendación, aprobación por política con segregación; marca ganadora y cierra RFQ/solicitud como ADJUDICADA |
| 4 | `empresa/` | Empresa única, sedes, centros de costo, políticas de aprobación por rango de monto |
| 5 | `catalogo/` | Categorías, unidades de medida con magnitud y conversión |
| 6 | `inventario/` | Productos (UC-02), bodegas, stock por bodega, movimientos y kardex (UC-03) |
| 7 | `documento/` | Subida y descarga de archivos fuera de la BD con reglas de acceso por entidad |
| 8 | `notificacion/` | Bandeja interna + correo (hoy simulado en log), canales configurables por evento; avisos de proveedor pendiente y stock bajo mínimo |

Las cotizaciones del proveedor llegan en el paso 12. La evaluación UC-14 se dispara desde la recepción (paso 17).

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
| **Próximo libre** | **V14** |

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

### Siguiente: paso 15. Pasos 15 a 19 (orden obligatorio, detalle en `plan-backend.md`)
Para notificar en los pasos nuevos: agregar el evento a `EventoNotificacion`, sembrar sus canales en una migración y llamar `notificacionService.notificarRol(...)`.

| Paso | Módulo | Notas para retomarlo |
|---|---|---|
| 15 | `compra/orden` | UC-10/12; inmutable (nuevas versiones); regla de documentos ORDEN_COMPRA |
| 16 | `pago/` | UC-11; regla de documentos PAGO, `comprobante_documento_id` |
| 17 | `recepcion/` | UC-13; al cerrar, `EvaluacionProveedorService.registrar(...)`; `MovimientoInventarioService.registrarEntradaCompra(...)` / `registrarDevolucionProveedor(...)`; regla de documentos RECEPCION |
| 18 | `reporte/` | UC-15 |
| 19 | Cierre | Pruebas de punta a punta, revisión de seguridad, Swagger completo |

### Pendientes técnicos conocidos
- Pasar `@Value` a `@ConfigurationProperties` (CORS y admin) y crear perfiles `dev`/`prod`. En `prod`, exigir `JWT_SECRET`.
- `@DataJpaTest` con Testcontainers cuando haya Docker.
- Correo real: reemplazar `LogEmailClient` por un `EmailClient` SMTP con credenciales en `application-local.yaml`.
- Limitar los intentos del registro público de proveedores (rate limiting por IP) antes de producción.
- Validar la existencia de `proveedorId` al crear usuarios PROVEEDOR desde el admin.
- Frontend Angular: `frontend/` sigue vacío.
- **Commit pendiente:** todo el trabajo está sin commitear en `main` (la rama de VS Code aparece como `feature/modulo1`).

### Cómo retomar
```bash
cd backend
./mvnw test
./mvnw spring-boot:run
```
Para crear el admin la primera vez, define `ADMIN_PASSWORD`. Swagger: `http://localhost:8080/swagger-ui.html`.
