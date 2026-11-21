# Supply-Core — Progreso del backend

> Última actualización: 2026-11-19 · Estado: **Ventas y Caja en construcción por specs: hechas la 000 (CI), 001 (catálogo de venta), 002 (clientes) y 003 (cajas y turnos); sigue la 004, la venta (ver sección 7)**. Antes: frontend completo (fases 0 a 8), stack de Docker e informe de compras para el dueño (V25). Antes: **pasos 0 a 18, 18-a a 18-d, SaaS fase 2 y SaaS fase 3 completos + 11-bis (despachos) + giro a SaaS multiempresa: seguridad de sesión (V16), aislamiento por empresa (V17), recepción con calidad (V18), factura con cruce de tres vías (V19) y presupuesto por centro de costo (V20), contratos con orden directa (V21), delegación con SLA de aprobación (V22), reorden sugerida con carga masiva por Excel (sin migración), consola de plataforma con planes y módulos (V23), seguridad restante de SaaS 3: sesiones que se cortan, recuperación de clave y MFA de administradores (V24), exportación a Excel y PDF con archivos legibles, y cruce de totales de la IA con Gemini como proveedor de lectura (sin migración)**.
> **796 pruebas del backend en verde** (1 omitida: la prueba en vivo contra Gemini), Javadoc sin advertencias (`doclint=all`), 33 migraciones Flyway (próxima V34); **166 pruebas del frontend** (Vitest, 29 archivos). CI con GitHub Actions en cada PR (spec 000), con la zona horaria de Bogotá. Specs de ventas en `agents/specs/`: 000 a 003 hechas. Al 2026-11-17 eran 714 pruebas del backend y 24 migraciones.
> Detalle vivo para Claude: `.claude/skills/supply-core/references/estado.md`, `plan-backend.md` y `saas.md`. Reglas no negociables: `CLAUDE.md`.

---

## 0. Resumen: en qué vamos

**Hecho (backend, pasos 0–17-bis + 11-bis).** El ciclo de compra funciona de punta a punta, incluida la entrada de mercancía, la factura del proveedor y el pago final:

necesidad → solicitud → aprobación → RFQ → cotizaciones (formulario o archivo leído con IA) → análisis y ranking → adjudicación → orden de compra → anticipo (con tope) → el proveedor acepta, prepara y despacha → **recepción** (rechazos con motivo, excedente en revisión, solo lo aceptado sube el stock) → **factura del proveedor cruzada con lo recibido** → **pago final contra factura** → cierre con evaluación del proveedor.

Además: usuarios y roles, proveedores, inventario con kardex, documentos, notificaciones, auditoría y despachos de salida.

**Giro a SaaS multiempresa (decidido el 2026-10-23).** El software pasa de «una empresa» a vender el uso a varias empresas (ferretería, eléctricos…), cada una con sus datos aislados, sus usuarios y sus roles; el dueño del software activa a cada empresa y sus módulos. Ya está hecho el aislamiento por empresa y la seguridad de sesión; falta la consola del dueño (fase 2), planes y módulos, el cobro de la suscripción y la operación (fases 3 a 5).

**Estamos en:** paso 19, cierre del backend. SaaS fase 3 (2026-11-10, V24): el JWT lleva una versión de sesión que sube al cambiar la clave o desactivar al usuario (sus sesiones se cortan en la siguiente petición y se revocan sus refresh tokens), recuperación de contraseña con token de un solo uso por correo, MFA TOTP obligatorio para ADMIN_SISTEMA y voluntario para los demás, cabeceras de seguridad, Dependabot y el script de dos usuarios MySQL. La fase 2, consola de plataforma, planes, módulos y `@RequiereModulo`, quedó hecha el 2026-10-28 con la migración V23. Del paso 19 ya están hechos el cruce de totales de la IA y Gemini (2026-11-16); lo que falta está en la sección 5, que sirve para retomar tras una compactación.

**Falta del backend:**

| Paso | Qué es | Con qué se conecta |
|---|---|---|
| 18 | **Reportes y tableros** (UC-15): gasto, inventario y desempeño de proveedores, con filtros por fecha, categoría y proveedor; solo lectura. Incluye estado de cuenta por proveedor (comprado, pagado, saldo) | Todas las tablas anteriores |
| 18-a a 18-d | **Compras avanzadas**: presupuesto por centro de costo, contratos y precios pactados, delegación de aprobación y SLA, reorden sugerida; carga masiva por Excel de productos y precios | Solicitud, RFQ, orden, inventario |
| SaaS 2 ✅ | **Consola de plataforma**: `usuario_plataforma` con MFA, alta de empresa, planes, módulos, `@RequiereModulo`, suspender y reactivar | Todo |
| SaaS 3 ✅ | **Seguridad restante**: MFA de administradores, recuperación de clave, revocar sesiones al cambiar clave o desactivar usuario, cabeceras seguras, Dependabot y script de dos usuarios MySQL (`backend/sql/permisos.sql`, pendiente de aplicar y probar en el ambiente real) | Seguridad |
| 19 | **Cierre del backend**: prueba de punta a punta con varias empresas, revisión de seguridad, Swagger completo, cruce de totales de la IA, permisos de BD con dos usuarios, limpieza de pendientes técnicos y commit | Todo |

**Después del backend:**
1. **Frontend en Angular** (`frontend/`): pantallas por rol que consumen esta API (login con código de empresa, bandejas de aprobación, RFQ, recepción, facturas, portal del proveedor, inventario, reportes, consola de plataforma). Boceto de las pantallas ya mostrado en la sesión del 2026-10-23 (panel de la empresa con módulos bloqueados según el plan, y consola del dueño con empresas y módulos).
2. **SaaS 4**: cobro de la suscripción con pasarela (Wompi, Mercado Pago o Stripe) en sandbox, estados de mora, límites por plan, métricas de uso.
3. **SaaS 5**: Docker Compose, CI/CD, entornos, logs con `empresa_id`, onboarding de una empresa en minutos.
4. **Prueba con IA real con cotizaciones de verdad**: Gemini ya se probó en vivo con un PDF de ejemplo (2026-11-16); falta con documentos reales (Gemini, Ollama o Claude).
5. **Fases futuras**: factura electrónica DIAN (mediante proveedor tecnológico), multimoneda con tasa congelada en el análisis, recepción móvil sin conexión (PWA), exportar la compra validada a la contabilidad (CSV, luego API), importar ventas del POS por CSV.

**Cómo termina:** el proyecto está completo cuando una compra recorre todo el ciclo desde la interfaz —desde que alguien la pide hasta que la mercancía entra al inventario, la factura se cruza, el pago queda registrado y el proveedor queda evaluado—, para varias empresas a la vez sin que una vea a la otra, cada paso queda auditado, las pruebas pasan y la sustentación se apoya en la guía `docs/Supply-Core-por-dentro.pdf` y en `docs/Supply-Core-SaaS-Plan-v1.1.pdf`.

---

## 1. Pasos completados

| Paso | Módulo | Qué quedó funcionando |
|---|---|---|
| 0 | `common/`, `config/` | Excepciones con código HTTP (409 negocio, 404, 401, 403, 400, 429), `GlobalExceptionHandler`, `ErrorResponse`, `PageResponse`, consecutivos (`SOL-000001`, ahora por empresa), CORS, Swagger |
| 1 | `security/` | Login (UC-01) con JWT HS256 (jjwt 0.13.0), filtro propio, `@RequiereRol`, `@UsuarioInterno`, BCrypt 12 — **sin Spring Security** |
| 2 | `usuario/` | CRUD de usuarios, perfil propio, roles; validaciones en los requests |
| 3 | `auditoria/` | Registro de acciones sensibles (éxito y fallo, con IP) + consulta UC-16, siempre filtrada por la empresa del usuario |
| 4 | `empresa/` | Datos de la empresa (cada una edita la suya), sedes, centros de costo, políticas de aprobación por rango de monto; configuración de tope de anticipo y tolerancia de factura |
| 5 | `catalogo/` | Categorías (por empresa), unidades de medida con magnitud y conversión (globales) |
| 6 | `inventario/` | Productos (UC-02), bodegas, stock por bodega, movimientos y kardex (UC-03) |
| 7 | `documento/` | Subida y descarga de archivos fuera de la BD con reglas de acceso por entidad |
| 8 | `notificacion/` | Bandeja interna + correo (hoy simulado en log), canales configurables por evento y por empresa; avisos de proveedor pendiente y stock bajo mínimo |
| 9 | `proveedor/` | Autorregistro público (por código de empresa) → PENDIENTE → ADMIN_COMPRAS habilita / rechaza / suspende; portal; edición de perfil; filtro por categoría; evaluación UC-14 con `score_actual` |
| 10 | `compra/solicitud/` | Solicitudes de compra (UC-04) con monto estimado y aprobación por política (UC-05): devolver, rechazar, segregación de funciones |
| 11 | `compra/rfq/` | RFQ desde solicitud aprobada (UC-06): invitación automática por categoría con alertas, abrir/cerrar/cancelar, cierre automático por fecha límite (recorre cada empresa), vista del proveedor invitado |
| 11-bis | `despacho/` | Remisión de salida (lo que se vende o entrega): varias líneas, confirmar descuenta el stock todo o nada, anular lo devuelve. Sin precios ni facturación |
| 12 | `compra/cotizacion/` | El proveedor cotiza (UC-07) por formulario o archivo; Java calcula montos y precio normalizado; sobre cerrado hasta que la RFQ cierre |
| 13 | `ia/` + `compra/analisis/` | Lectura de cotizaciones por archivo con IA (ninguna / Ollama local / Claude) con confianza por dato y revisión humana; motor de comparación en Java con pesos versionados, alertas y ranking publicable |
| 14 | `compra/adjudicacion/` | Adjudicación (UC-09) sobre el análisis publicado, justificación si no sigue la recomendación, aprobación por política con segregación; marca ganadora y cierra RFQ/solicitud como ADJUDICADA |
| 15 | `compra/orden/` | Orden de compra (UC-10) inmutable con versiones, en la unidad del inventario; flujo del proveedor (UC-12): aceptar, rechazar, preparar, despachar con evidencia. `EstadoOrden`: RECIBIDA_PARCIAL → CERRADA permitido (cierre con faltante) |
| 16 | `pago/` | Pagos de la orden (UC-11) desde que se emite, comprobante (si falta, evidencia pendiente), anulación con motivo, segregación de funciones; el proveedor ve lo pagado y el saldo. Desde el 17-bis cada pago tiene tipo (ANTICIPO o PAGO_FINAL) |
| 16-S | `security/` (sesión, SaaS) | JWT de 15 min + refresh token de un solo uso con rotación y detección de reuso; bloqueo de cuenta a los 5 fallos; límite de 20 peticiones por minuto por IP a login, refresh y registro; `logout` y `logout-todo`; rol CAJERO (V16) |
| 16-T | `common/tenant/` (SaaS fase 1) | Aislamiento por empresa con `@TenantId` en 22 entidades, `empresa` dentro del JWT, login con código de empresa, correo único por empresa, registro público por código, jobs y `@Async` por empresa, consecutivos por empresa; 15 pruebas de fuga (V17) |
| 17 | `recepcion/` | Recepción de mercancía (UC-13): varias entregas, rechazo con motivo obligatorio, excedente en revisión (compras acepta o devuelve), solo lo aceptado sube el stock, costo de referencia = último precio de la orden, cierre (con motivo si hay faltante) que evalúa al proveedor. `@TenantId` y prueba de fuga (V18) |
| 17-bis | `factura/` + `pago/` | Factura del proveedor y cruce de tres vías con tolerancia configurable por empresa (% o monto fijo); pago con tipo: anticipo con tope y pago final contra factura que cuadre, que descuenta los anticipos; aprobación por compras con segregación. `@TenantId` y prueba de fuga (V19) |
| 18 | `reporte/` | Reportes de solo lectura (UC-15): gasto por proveedor, categoría y mes; inventario con valor y alerta de mínimo; ranking de proveedores; estado de cuenta por proveedor. Consultas agregadas sobre entidades con `@TenantId` (sin migración); Java redondea y totaliza. Prueba de fuga en MySQL real |
| 18-a | `presupuesto/` | Presupuesto por centro de costo y mes (`/api/presupuestos`, ADMIN_COMPRAS lo define; compras, aprobación, administración y auditoría lo consultan). Al enviar una solicitud se suma lo comprometido del mes de su fecha requerida (pendientes, aprobadas, en RFQ, adjudicadas y cerradas) con bloqueo de fila; si lo excede responde 409 `PRESUPUESTO_EXCEDIDO`. Con justificación pasa como excepción por la política de mayor tramo (sin esa política no hay excepción). También bloquea solicitudes duplicadas (409 `SOLICITUD_DUPLICADA`: mismo centro, mismo producto, otra solicitud pendiente o aprobada), con la misma justificación. Todo queda en `SOLICITUD_EXCEPCION`. Sin presupuesto en el mes no hay control. `@TenantId` y prueba de fuga (V20) |
| 18-b | `contrato/` + `compra/orden` | Contratos con proveedores (`/api/contratos`): vigencia, y por producto precio pactado, impuesto y tope de cantidad. Los precios no se editan (se anula y se crea otro); solo se mueve la fecha de fin o se anula. Un proveedor no puede tener dos contratos activos que se crucen para el mismo producto. `GET /api/contratos/ofertas?solicitudId=` valora una solicitud APROBADA contra cada contrato vigente (cubre todo, cabe en el tope, no supera lo aprobado) y solo informa. **Orden directa** `POST /api/ordenes/directa` (COMPRADOR): sin RFQ, al precio pactado, solo si el contrato es activo y vigente hoy, su proveedor sigue HABILITADO, cubre todos los productos de la solicitud, las cantidades caben en lo que queda del tope y el subtotal no supera el monto con que se aprobó la solicitud (y ese monto era completo); quien emite no puede ser el solicitante. La solicitud pasa a ADJUDICADA; cancelar la orden la devuelve a APROBADA y libera el tope. La orden guarda `contrato_id` y `solicitud_id` (sin adjudicación); sus líneas guardan `contrato_precio_id`. Una versión nueva de una orden de contrato solo puede bajar cantidades. `@TenantId` y prueba de fuga (V21) |
| 18-c | `delegacion/` + SLA | **Delegación** (`/api/delegaciones`): quien tiene un rol que aprueba en alguna política activa delega en otro usuario interno activo por fechas; el delegado decide con los roles del delegante (solicitudes y adjudicaciones) y ve esas pendientes en su bandeja. La segregación sigue igual: nadie decide lo que pidió él ni lo que pidió quien le delegó. Cada decisión guarda su `delegacion_id` y la auditoría dice `porDelegacion`. La revoca el delegante o ADMIN_COMPRAS; el vencimiento se calcula con las fechas. **SLA** de solicitudes pendientes: `empresa.sla_aprobacion_horas` (24 por defecto, 0 = sin SLA, editable en `/api/empresa`); `SlaAprobacionJob` (cada 5 min, por empresa) avisa al 80 % al rol aprobador y a sus delegados, y al vencer escala a la política de mayor tramo y avisa al nuevo rol, a sus delegados, al solicitante y, si ya era el nivel más alto, a ADMIN_COMPRAS. Cada paso se hace una vez por envío (`sla_aviso_en`, `escalada_en`; reenviar reinicia). `@TenantId` y prueba de fuga (V22) |
| 18-d | `reorden/` + `inventario` | **Reorden sugerida** (`GET /api/reorden/sugerencias`): para cada producto activo bajo su mínimo, cantidad = máximo − stock total − por recibir de órdenes vivas (pedido menos aceptado; lo rechazado sigue pendiente) − ya pedido en solicitudes pendientes, aprobadas o en RFQ (convertido a la unidad del producto). Sin máximo no se calcula (`sinMaximo`); si lo que viene en camino ya alcanza el máximo, 0 y `yaCubierto`. Como pista muestra el contrato vigente más barato, sin elegir proveedor. `POST /api/reorden/borrador` (SOLICITANTE) crea una solicitud en BORRADOR con esas cantidades: no compra ni aprueba nada y sigue el flujo normal al enviarse. **Carga masiva por Excel** (`POST /api/productos/carga-masiva`, ADMIN_COMPRAS, .xlsx hasta 5 MB y 2.000 filas; `GET /plantilla`): el código identifica el producto, una celda vacía deja el dato como está (un archivo de solo `codigo` y `costo_referencia` actualiza precios), la categoría se crea si no existe, la unidad debe existir y no se cambia si el producto tiene movimientos. Es de todo o nada, con errores por fila (`aplicado=false`), y `soloValidar` revisa sin guardar. Nunca toca el stock. Prueba de fuga con MySQL real |
| SaaS 2 | `plataforma/` + `security` | **Consola del dueño** (`/api/plataforma/**`). `usuario_plataforma` sin empresa, con **MFA obligatorio** (TOTP RFC 6238, compatible con Google Authenticator): `POST /api/plataforma/auth/login` entrega un desafío de 5 min (y, la primera vez, el secreto para vincular la app) y `/auth/mfa` lo canjea por la sesión con el código de 6 dígitos; un código no se acepta dos veces; bloqueo de cuenta y límite de intentos por IP. Su JWT no lleva `emp` y no entra a la API de ninguna empresa, ni el de una empresa a la consola (el `AuthFilter` los separa). **Planes y módulos:** 10 módulos (`Modulos`: INVENTARIO, PROVEEDORES, COMPRAS, RFQ_COTIZACIONES, IA, ORDENES_PAGOS, RECEPCION, DESPACHOS, REPORTES, AUDITORIA) y 3 planes sembrados (BASICO, PROFESIONAL, EMPRESARIAL); se pueden crear planes y cambiar sus módulos. **Alta de empresa** con su administrador inicial y su matriz de pesos, plan, estado inicial (PRUEBA o ACTIVA), cambio de plan y **excepciones de módulo** por empresa (dar o quitar uno aparte del plan). **Suscripción** PRUEBA → ACTIVA → MOROSA (solo lectura) → SUSPENDIDA (sin acceso) → CANCELADA (final): suspender revoca las sesiones renovables y bloquea login y refresh. **`@RequiereModulo`** en 30 controllers (la explicación del ranking es del módulo IA): módulo apagado = 403 `MODULO_NO_CONTRATADO`; el `RolInterceptor` revisa suscripción, módulo y rol en ese orden, con lo recordado 30 s por empresa (`AccesoEmpresaService`, se limpia al instante en la misma instancia). Una empresa sin suscripción no entra. Auditoría global propia (`auditoria_plataforma`, `GET /api/plataforma/auditoria`). La empresa ve lo suyo en `GET /api/empresa/modulos`. Prueba de fuga con MySQL real |

La evaluación UC-14 la dispara el cierre de la recepción (paso 17).

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
| V16 | Seguridad de sesión: tabla `refresh_token` (solo hash SHA-256, familia, usado, revocado), `usuario.intentos_fallidos` y `bloqueado_hasta`, rol CAJERO |
| V17 | Multiempresa: `empresa.codigo_publico` (la sembrada es `demo`); `empresa_id` NOT NULL con FK e índice en 15 tablas (categoría, proveedor, producto, stock, movimiento, rfq, cotización, análisis, adjudicación, orden, pago, evaluación, documento, config y notificación) más `consecutivo`; `auditoria.empresa_id` opcional; únicos por empresa (código de producto, NIT, nombre de categoría, código de centro de costo, consecutivos, versión de orden, evento/canal, **correo de usuario**); `consecutivo` con PK (empresa_id, prefijo). Los datos existentes pasaron a la primera empresa |
| V18 | Recepción con calidad: `recepcion.empresa_id`; `recepcion_linea` con `cantidad_aceptada`, `cantidad_excedente`, `motivo_rechazo` (DANADO, NO_CUMPLE_ESPECIFICACION, VENCIDO, CANTIDAD_ERRADA, OTRO), resolución del excedente, estado EXCEDENTE_DEVUELTO y CHECKs; eventos RECEPCION_REGISTRADA / EXCEDENTE / CERRADA por empresa |
| V19 | Factura y cruce de tres vías: tabla `factura_proveedor` (esperado y diferencia guardados, estados CUADRADA / EN_REVISION / APROBADA / ANULADA, `numero_activo` generado que permite volver a registrar un número anulado, CHECKs); `pago.tipo` (ANTICIPO/PAGO_FINAL, lo anterior quedó como ANTICIPO); `empresa.tope_anticipo_pct` (50), `tolerancia_factura_pct` (1) y `tolerancia_factura_monto` (0); eventos FACTURA_EN_REVISION / DECIDIDA por empresa |
| V20 | Presupuesto por centro de costo: tabla `presupuesto` (centro, año, mes, monto; único por empresa, centro y mes; CHECK de valores); `solicitud_compra.excede_presupuesto` y `justificacion_excepcion` (CHECK: si excede, exige justificación); índice por centro, fecha requerida y estado para sumar lo comprometido |
| V21 | Contratos: `contrato_proveedor` (vigencia, estado ACTIVO/ANULADO, número único por empresa, CHECKs) y `contrato_precio` (precio, impuesto, tope, único por contrato y producto); `orden_compra.adjudicacion_id` pasa a NULL con `contrato_id` y `solicitud_id` y un CHECK que exige adjudicación o contrato más solicitud; `orden_compra_linea.contrato_precio_id` |
| V22 | Delegación y SLA: `delegacion_aprobacion` (delegante, delegado, fechas, ACTIVA/REVOCADA, CHECKs); `aprobacion_solicitud.delegacion_id` y `adjudicacion.delegacion_id`; `empresa.sla_aprobacion_horas` (0 a 720, 24 por defecto); `solicitud_compra.sla_aviso_en` y `escalada_en` con índice; eventos SOLICITUD_SLA_POR_VENCER, SOLICITUD_ESCALADA y DELEGACION_ASIGNADA sembrados por empresa |
| V23 | Plataforma, planes y módulos: `usuario_plataforma` (MFA: secreto, activo y último paso usado; bloqueo), `modulo`, `plan`, `plan_modulo`, `empresa_modulo` (excepciones por empresa), `suscripcion` (una por empresa, estado ENUM), `auditoria_plataforma`; siembra los 10 módulos y los planes BASICO, PROFESIONAL y EMPRESARIAL, y deja a las empresas que ya existen en EMPRESARIAL y ACTIVA |
| V24 | Seguridad fase 3: `usuario.version_sesion` (sube al cambiar la clave o desactivar; el JWT la lleva en el claim `sv`), `usuario.mfa_secreto`, `mfa_activo` y `mfa_ultimo_paso` (TOTP), y tabla `recuperacion_password` (empresa, usuario, hash del token, vencimiento, usado en; único por hash) |
| V25 | Informe de compras y aprobación del dueño: `solicitud_compra.aprueba_dueno` (si quien pide aprueba con el rol de su política, la decide `ADMIN_SISTEMA`) y tabla `informe_compras` (PEDIDO/ENVIADO, periodo, mensaje del pedido, análisis, foto fija en JSON, quién pidió, quién envió y cuándo lo leyó; CHECK de periodo) |
| **Próximo libre** | **V26** |

---

## 2. Archivos principales y su responsabilidad

Todo el código está en `backend/src/main/java/com/proyecto/supply_core/`. Cada módulo tiene `entity/ repository/ dto/ service/ controller/` (+ `enums/`, `validation/` cuando aplica).

### Transversal
| Archivo | Responsabilidad |
|---|---|
| `common/exception/GlobalExceptionHandler` | Traduce todo error a `ErrorResponse {codigo, mensaje, detalles[], timestamp}`: 400 validación, 401, 403, 404, 405, 409 negocio, 413 archivo grande, 415, 429 demasiadas peticiones, 500 |
| `common/exception/*Exception` | `NegocioException` 409 (con código), `NoEncontradoException` 404, `NoAutenticadoException` 401, `AccesoDenegadoException` 403, `ParametroInvalidoException` 400, `DemasiadasPeticionesException` 429 |
| `common/util/Ordenamiento` | Lista blanca de campos ordenables (evita 500 y ordenar por datos sensibles) |
| `common/util/ConsecutivoGenerator` | Consecutivos atómicos por empresa con `INSERT … ON DUPLICATE KEY UPDATE ultimo = LAST_INSERT_ID(ultimo + 1)` (la fila se crea sola) |
| `common/tenant/EmpresaContexto` | Empresa del hilo actual: `requerida()`, `opcional()`, `como(empresa, …)`, `sinEmpresa(…)` (modo raíz), `propagar(tarea)` |
| `common/tenant/EmpresaResolver` | Le entrega la empresa a Hibernate (`@TenantId`); sin empresa ni modo raíz abrir una sesión falla; solo durante el arranque permite modo raíz |
| `common/tenant/EmpresaTaskDecorator` | Las tareas `@Async` heredan la empresa del hilo que las lanzó |
| `config/WebConfig`, `OpenApiConfig`, `SchedulingConfig` | CORS para Angular, interceptor de roles, Swagger con «Authorize», tareas programadas |

### Seguridad y usuarios
| Archivo | Responsabilidad |
|---|---|
| `security/jwt/JwtUtil` | Emite y valida el JWT (emisor `supply-core`, expira en 15 min, claim `emp` obligatorio, reloj inyectable para pruebas) |
| `security/jwt/SesionProperties` | `app.sesion.*`: días del refresh, intentos máximos, minutos de bloqueo, límite por minuto |
| `security/filter/AuthFilter` | Exige `Bearer` en `/api/**`, fija la empresa del JWT como contexto y la limpia al terminar; rutas públicas (`/api/auth/login`, `/api/auth/refresh`) en modo sin empresa; `/api/portal/registro/{codigo}/**` resuelve la empresa por su código |
| `security/filter/RateLimitFilter` | 20 POST por minuto por IP a login, refresh y registro; responde 429 con `Retry-After` (en memoria) |
| `security/interceptor/RolInterceptor` | Aplica `@RequiereRol` (alguno de los roles) y `@UsuarioInterno` (cualquiera menos PROVEEDOR) |
| `security/service/AuthService` | Login por empresa: mismo mensaje y mismo tiempo para correo inexistente, clave errada, cuenta bloqueada o empresa desconocida; bloqueo a los 5 fallos; renovación de sesión que revalida usuario y proveedor; cierre de una o todas las sesiones; audita |
| `security/service/RefreshTokenService` | Emite, rota y revoca refresh tokens (256 bits, solo el hash en BD, un solo uso, revoca toda la sesión si se reutiliza uno); limpieza nocturna |
| `security/context/SesionActual` | Usuario de la petición (con su `empresaId`) desde cualquier Service |
| `usuario/service/UsuarioService` | Alta, edición, activar/desactivar, contraseñas; nunca se queda sin ADMIN_SISTEMA (por empresa) |
| `usuario/validation/*` | `@CorreoUnico`, `@RolesExistentes`, `@ProveedorConsistente`, `ReglasPassword` |
| `usuario/config/DataInitializer` | Crea el admin inicial de la primera empresa si falta (con `ADMIN_PASSWORD`) |

### Negocio
| Archivo | Responsabilidad |
|---|---|
| `auditoria/service/AuditoriaService` | `exito()` se une a la transacción; `fallo()` usa `REQUIRES_NEW`; guarda la empresa (la del contexto o la del usuario) y la consulta filtra por empresa |
| `proveedor/service/ProveedorService` | Registro público, habilitar, rechazar y suspender con transiciones válidas (`EstadoProveedor.puedePasarA`) |
| `empresa/service/EmpresaService` | Cada empresa consulta y edita la suya, incluida la configuración de anticipo y tolerancia |
| `empresa/service/PoliticaAprobacionService` | Rangos `[desde, hasta)` sin superposición; `aplicable(tipo, monto)` para los pasos 10 y 14 |
| `catalogo/service/UnidadMedidaService` | `convertir(cantidad, origen, destino)` solo dentro de la misma magnitud, 4 decimales |
| `inventario/service/MovimientoInventarioService` | **Único** que modifica el stock (ver decisiones); su SQL nativo lleva `empresa_id` |
| `inventario/service/ProductoService` | Catálogo de productos, stock total, alertas bajo mínimo |
| `documento/service/DocumentoService` | Valida, guarda, lista y descarga archivos; delega el acceso a la regla de cada entidad |
| `documento/storage/LocalStorageClient` | Guarda en `backend/almacenamiento/` (detrás de la interfaz `StorageClient`) |
| `proveedor/service/ProveedorReglaDocumentos` | Primera `ReglaAccesoDocumentos` (documentos del proveedor) |
| `notificacion/service/NotificacionService` | `notificar(usuario)` / `notificarRol(rol)` (solo usuarios de la misma empresa): guarda la interna en la transacción y envía el correo después del commit |
| `exportacion/service/ExportacionService` | Exporta stock, kardex y solicitudes a Excel y la orden de compra a PDF reutilizando los Services de cada pantalla (mismos permisos y empresa); tope de 10.000 filas; descarga o correo al usuario de la sesión; audita cada exportación |
| `notificacion/email/EmailClient` | Interfaz de correo; `LogEmailClient` solo escribe en el log (cambiar por SMTP sin tocar los Services) |

### Compras, recepción, factura e IA
| Archivo | Responsabilidad |
|---|---|
| `proveedor/service/EvaluacionProveedorService` | UC-14: `registrar(proveedor, orden, indicadores)` y recálculo de `score_actual`; lo llama el cierre de la recepción |
| `compra/solicitud/service/SolicitudService` | Crear, editar, enviar (monto estimado + política) y cancelar solicitudes |
| `compra/solicitud/service/AprobacionSolicitudService` | Aprobar, rechazar o devolver con segregación de funciones; bandeja del aprobador |
| `compra/rfq/service/RfqService` + `RfqCierreJob` | RFQ desde solicitud aprobada, invitación por categoría, abrir/cerrar/cancelar; el job recorre cada empresa con su propio contexto |
| `compra/cotizacion/service/CotizacionService` | El proveedor cotiza por formulario o archivo; sobre cerrado mientras la RFQ esté abierta |
| `compra/cotizacion/service/CalculadoraCotizacion` | Neto, impuestos, total y precio normalizado (solo Java calcula dinero) |
| `ia/client/IAClient` (+ `SinIAClient`, `OllamaIAClient`, `ClaudeIAClient`) | Lectura de cotizaciones y redacción de explicaciones, elegida por `app.ia.proveedor` |
| `ia/service/ExtraccionService` + `ProcesadorExtraccion` | Lectura asíncrona después del commit (con la empresa del hilo que la lanzó), confianza por dato, confirmación, digitación y reintento |
| `compra/analisis/service/MotorComparacion` | Java puro: puntajes por criterio, score ponderado, ranking y alertas |
| `compra/analisis/service/AnalisisService` / `PesosService` | Generar y publicar el análisis con snapshot de pesos; pesos versionados que suman 100 |
| `compra/adjudicacion/service/AdjudicacionService` | Adjudicar sobre el análisis publicado, aprobación por política, efectos sobre cotizaciones, RFQ y solicitud |
| `compra/orden/service/OrdenCompraService` / `OrdenProveedorService` | Emitir, versionar y cancelar órdenes; aceptar, rechazar, preparar y despachar (proveedor). `bloquear`, `historial` y `exigirTransicion` son públicos para recepción y factura |
| `despacho/service/DespachoService` | Remisión de salida (venta o entrega): confirma todo o nada y anula devolviendo stock |
| `pago/service/PagoService` + `PagoReglaDocumentos` | Registrar, adjuntar comprobante y anular pagos; saldo por orden (todas sus versiones); segregación de funciones; reglas del tipo: anticipo con tope, pago final contra factura |
| `recepcion/service/RecepcionService` | Registrar recepciones (varias por orden), cerrar con evaluación del proveedor y resolver el excedente; sube el stock solo con lo aceptado y actualiza el costo de referencia |
| `recepcion/service/CalculadoraRecepcion` | Java puro: reparte lo recibido en aceptado, excedente y faltante; indicadores de evaluación (puntualidad, cumplimiento, incidencias, calidad) |
| `recepcion/service/RecepcionReglaDocumentos` | Evidencia fotográfica de la recepción (sube almacén o compras; ven internos y el proveedor de la orden) |
| `factura/service/FacturaService` | Registrar factura y cruzarla, volver a cruzar, aprobar (con segregación), anular, adjuntar PDF y `exigirPagable` para el pago final |
| `factura/service/CalculadoraFactura` | Java puro: lo esperado (cantidad aceptada × precio + impuesto por línea) y si el total cuadra dentro de la tolerancia (% o monto fijo) |
| `factura/service/FacturaReglaDocumentos` | PDF de la factura (sube el administrativo; ven internos y el proveedor de la orden) |

### Configuración y pruebas
| Archivo | Responsabilidad |
|---|---|
| `backend/src/main/resources/application.yaml` | Configuración común (sin secretos): JWT de 15 min, `app.sesion.*`, CORS, IA, almacenamiento |
| `application-local.yaml` | Contraseña de MySQL y secretos locales — **en .gitignore** (plantilla: `application-local.example.yaml`) |
| `src/test/resources/application-test.yaml` | Perfil de las pruebas contra MySQL real: base separada `supply_core_test`, Flyway permitido a limpiar, sin admin inicial ni jobs |
| `src/test/.../common/web/WebTestBase` | Base de las pruebas `@WebMvcTest`: token real con `bearer("ROL")` y `bearerProveedor(id)`, repositorios simulados (incluye `EmpresaRepository`) |
| `src/test/.../common/tenant/EmpresaDePrueba` | Extensión JUnit que fija la empresa 1 en el contexto para las pruebas de servicios |
| `src/test/.../common/tenant/AislamientoEmpresasTest` | 36 pruebas de fuga y de extremo a extremo contra MySQL real: dos empresas con los mismos códigos, NIT y correos; repositorios y API completa |
| `.gitignore` (raíz) | Ignora `graphify-out/` |
| `CLAUDE.md` + `.claude/skills/supply-core/` | Contexto del proyecto para Claude (reglas, modelo de datos, estado, SaaS) |
| `docs/Supply-Core-SaaS-Plan-v1.1.pdf` | Arquitectura SaaS, reglas de negocio, seguridad y plan (10 páginas). `Supply-Core-SaaS-Plan.pdf` es la v1.0, desactualizada y borrable |

---

## 3. Decisiones de diseño clave

**Seguridad y sesión**
- JWT propio con jjwt, sin Spring Security (decisión del usuario). Un solo login: el proveedor es un `usuario` con `proveedor_id` y rol PROVEEDOR, y solo entra si su proveedor está HABILITADO.
- **JWT de 15 minutos** más **refresh token opaco de 256 bits, de un solo uso** (7 días). En la BD solo queda el hash. Si se presenta un refresh ya usado o revocado se revoca toda la sesión y se audita `REFRESH_REUTILIZADO`. Al renovar se revalida usuario activo y proveedor habilitado: un usuario desactivado pierde el acceso como máximo en 15 minutos.
- **Bloqueo de cuenta**: 5 fallos seguidos bloquean 15 minutos; bloqueada responde el mismo «Credenciales inválidas» (no revela que el correo existe). `login` y `refrescar` usan `noRollbackFor` para que el contador y la revocación queden guardados.
- **Límite por IP**: 20 POST por minuto a login, refresh y registro (429 con `Retry-After`). Está en memoria: con varias instancias haría falta Redis; detrás de un proxy hay que configurar la IP real.
- Un token sin el claim `emp` (anterior a SaaS) ya no sirve.
- Secretos solo en `application-local.yaml` o en variables de entorno. Si falta `JWT_SECRET`, en desarrollo se genera uno aleatorio.

**Multiempresa (SaaS, fase 1)**
- Una base de datos compartida con `empresa_id` en cada tabla de negocio. El `empresa_id` sale del JWT, nunca del request. Hibernate agrega `empresa_id = ?` a toda consulta y carga por id de las entidades con `@TenantId`, y asigna la empresa al guardar; nadie la escribe ni la filtra a mano.
- Sin empresa en el contexto no se abre una sesión de BD (el modo raíz existe solo para resolver la empresa por código, el refresh y listar empresas en un job; durante el arranque se permite para que Spring Data valide sus consultas).
- Las tablas hijas (líneas, historiales, detalles) heredan la empresa de su padre. `unidad_medida`, `criterio_evaluacion`, `rol` y `permiso` son globales.
- **Login**: `{empresa, correo, password}` con el código público de la empresa. El correo es único por empresa. Una empresa inexistente responde igual que una clave mala.
- **Registro público de proveedores**: `/api/portal/registro/{codigoEmpresa}` (y `/categorias`). El `AuthFilter` resuelve el código y fija la empresa antes de validar el cuerpo, así NIT, correo y categorías se validan dentro de esa empresa. Código desconocido: 404.
- Consecutivos y SQL nativo/JDBC llevan `empresa_id` explícito. El cierre automático de RFQ recorre cada empresa; las tareas `@Async` heredan la empresa.
- Los proveedores son por empresa. Los roles son un catálogo global (incluye CAJERO) que el admin de cada empresa asigna a sus usuarios.
- Reglas SaaS completas (tres niveles de poder, doble permiso módulo + rol, suscripción, habeas data) y plan por fases: `references/saas.md` y reglas 7–11 de `CLAUDE.md`.
- Toda entidad o consulta nueva de negocio debe llevar `@TenantId` y sumar su prueba en `AislamientoEmpresasTest`.

**Datos y reglas**
- El esquema lo maneja solo Flyway (`ddl-auto: none`). Nunca se edita un `V*` ya aplicado.
- Nada se borra: usuarios, productos, sedes, bodegas, categorías y unidades se desactivan; la auditoría, los movimientos y los documentos son `@Immutable`.
- Las validaciones van en los requests, con anotaciones propias y mensajes en español explícitos (no dependen del idioma de la JVM). En el Service quedan solo las reglas que dependen del estado.
- Las FK entre módulos se guardan como `Long` (`usuario.centroCostoId`, `proveedorId`), y su existencia se valida con anotaciones (`@CentroCostoActivo`, `@SedeActiva`...).

**Inventario (regla que no se negocia)**
- El stock solo cambia por `MovimientoInventarioService`: `INSERT … ON DUPLICATE KEY` + `SELECT … FOR UPDATE`. En los traslados se bloquea en orden de id para evitar interbloqueos, y el stock nunca queda negativo. Lo verifiqué con 20 salidas concurrentes sobre un stock de 10: 10 aceptadas y 10 rechazadas.
- Por la API solo se registran SALIDA, AJUSTES (con motivo) y TRASLADO. ENTRADA_COMPRA solo llega desde la recepción. No existe un endpoint para editar el stock.
- Las unidades son inmutables (código, magnitud y factor). La unidad de un producto no cambia si ya tiene movimientos.

**Documentos**
- Los archivos van fuera de la BD; en la BD solo queda la metadata.
- La clave en disco la genera el sistema (`AAAA/MM/<UUID>.ext`): el nombre del usuario nunca forma parte de la ruta, lo que evita `../`.
- El tipo se detecta por extensión **y** por la firma real (magic bytes). Máximo 10 MB.
- Si la transacción se revierte, el archivo se borra. Las descargas salen como adjunto con `nosniff`. No hay borrado.
- Cada módulo define su `ReglaAccesoDocumentos` (proveedor, cotización, orden, pago, recepción, factura); si una entidad no tiene regla, responde 409. Pendiente: guardar los archivos en carpeta por empresa (hoy el acceso ya está aislado por la BD).

**Solicitudes y aprobación**
- Monto estimado = Σ cantidad (convertida a la unidad del producto) × `costo_referencia`. Se calcula al enviar. La recepción mantiene el costo de referencia al día.
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

**Recepción (paso 17)**
- Se recibe una orden DESPACHADA o RECIBIDA_PARCIAL; puede haber varias recepciones. Por línea se informa lo recibido y lo rechazado (motivo obligatorio); Java reparte: **aceptada** = lo bueno hasta completar el pedido (única cantidad que sube el stock), **excedente** = lo bueno que sobra (línea EN_REVISION, no suma stock) y **faltante** = lo que aún falta.
- **Lo rechazado nunca entra al stock**: queda como devolución pendiente al proveedor, con su motivo. No se genera un movimiento de «devolución» porque esa mercancía nunca ingresó (`registrarDevolucionProveedor` queda para mercancía que ya estaba en el stock).
- El excedente lo resuelve COMPRADOR o ADMIN_COMPRAS: aceptar (entra al stock) o devolver (EXCEDENTE_DEVUELTO), con motivo.
- Cada recepción actualiza `producto.costo_referencia` con el último precio de la orden.
- La orden pasa a RECIBIDA_PARCIAL o RECIBIDA_TOTAL. El cierre (ALMACENISTA o ADMIN_COMPRAS) exige que no haya excedente sin resolver; si hay faltante exige motivo (RECIBIDA_PARCIAL → CERRADA); al cerrar se evalúa al proveedor.
- Indicadores UC-14 calculados en Java: puntualidad (pierde 10 puntos por día de atraso), cumplimiento de cantidad, incidencias y calidad; precio y respuesta quedan sin datos todavía.
- Pendiente a decidir: no hay segregación en la recepción (quien emitió la orden también podría recibirla).

**Pagos, factura y cruce de tres vías (pasos 16 y 17-bis)**
- El sistema solo registra lo que ya se pagó por fuera: no hay pasarela ni contabilidad.
- Todo pago tiene tipo. **ANTICIPO**: se paga desde que la orden se emite; la suma de anticipos (todas las versiones) no supera `tope_anticipo_pct` (50 % por defecto) del total vigente. **PAGO_FINAL**: solo con la orden recibida y una factura CUADRADA o APROBADA; no supera lo que falta por pagar de la factura (descuenta los anticipos). En ambos casos la suma de pagos nunca supera el total vigente de la orden, y una versión nueva no puede quedar por debajo de lo ya pagado.
- **Factura**: la registra ADMINISTRATIVO sobre una orden con recepciones. Una factura vigente por orden; número único por proveedor mientras no esté anulada. Java calcula lo esperado (cantidad aceptada × precio de la orden + impuesto por línea) y compara el total: dentro de la tolerancia de la empresa (porcentaje del esperado **o** monto fijo, lo que cubra) queda CUADRADA; si no, EN_REVISION y avisa a compras. Sin nada aceptado nunca cuadra.
- Una factura EN_REVISION la aprueba ADMIN_COMPRAS con motivo; **quien la registró no puede aprobarla**. Se puede volver a cruzar si la recepción cambió; si estaba CUADRADA y ya no cuadra, el pago final se bloquea hasta volver a cruzarla. Una factura equivocada se anula con motivo (no si hay un pago final registrado) y su número se puede volver a registrar.
- Límite conocido: una sola factura vigente por orden; si el proveedor factura por entregas debe emitir una que cubra lo recibido.
- Segregación de funciones en el pago: no registra el pago quien pidió la compra, quien la aprobó, quien adjudicó ni quien emitió la orden.
- Un pago no se edita ni se borra: el comprobante se adjunta una sola vez (sin él queda con evidencia pendiente) y un error se anula con motivo.

**Despacho**
- Las ventas o entregas salen con un documento de varias líneas; confirmar descuenta todo o nada en orden de producto, y anular devuelve el stock. El rol CAJERO existe pero aún no tiene acceso a estos endpoints.

**Notificaciones**
- Cada evento/canal se activa o desactiva en `config_notificacion` por empresa (sin fila = activo). Solo se notifica a usuarios activos de la misma empresa.
- El correo nunca sale si la operación se revierte, y si el correo falla la operación sigue (queda en log).
- El aviso de stock sale solo cuando el total **cruza** el mínimo, no en cada salida posterior.
- Cada usuario solo ve y marca sus notificaciones (una ajena responde 404).
- Eventos nuevos: RECEPCION_REGISTRADA, RECEPCION_EXCEDENTE, RECEPCION_CERRADA, FACTURA_EN_REVISION y FACTURA_DECIDIDA.

**Auditoría**
- Los éxitos se registran junto con la operación; los fallos en una transacción aparte. Nunca se guardan contraseñas. Las acciones se agregan a `AccionAuditoria` y `EntidadAuditada`.
- Acciones nuevas: LOGOUT, REFRESH_REUTILIZADO, CUENTA_BLOQUEADA, RECEPCION_REGISTRAR, RECEPCION_RESOLVER_EXCESO, RECEPCION_CERRAR, FACTURA_REGISTRAR, FACTURA_RECRUZAR, FACTURA_APROBAR, FACTURA_ANULAR, FACTURA_ARCHIVO.
- Cada registro lleva la empresa del usuario (nula si no se conoce, p. ej. login con una empresa desconocida); la consulta UC-16 solo ve la de su empresa.

**Calidad**
- Javadoc en toda clase y método (incluidos los privados); `doclint=all` sin advertencias.
- Cada módulo tiene pruebas de Service (Mockito) y de controller (`@WebMvcTest`).
- Pruebas de fuga y de extremo a extremo contra MySQL real en la base separada `supply_core_test` (`AislamientoEmpresasTest`, 36 pruebas): se niega a borrar cualquier base que no sea esa. Cubren lectura por id, listados, SQL nativo, login, refresh, registro público, auditoría, recepción completa, factura y pago final, la configuración por empresa, los reportes, el presupuesto (control de tope y de duplicadas con la base real) los contratos (solape, tope y orden directa con la base real) la delegación con el SLA (decidir por delegación, segregación, revocación, aviso y escalamiento por empresa), la reorden sugerida y la carga masiva por Excel (mismos códigos en dos empresas sin chocar), y la consola de plataforma (MFA real, tokens que no se mezclan, alta de empresa, módulos, mora y suspensión).

---

## 4. Lo que sigue

### Siguiente: paso 18 y compras avanzadas (orden obligatorio, detalle en `plan-backend.md`)
Para notificar en los pasos nuevos: agregar el evento a `EventoNotificacion`, sembrar sus canales **para cada empresa** en una migración (`INSERT … SELECT` desde `empresa`) y llamar `notificacionService.notificarRol(...)`.

| Paso | Módulo | Notas para retomarlo |
|---|---|---|
| 17 | `recepcion/` | ✅ Hecho (2026-10-23, V18) |
| 17-bis | `pago/` + `factura/` | ✅ Hecho (2026-10-23, V19) |
| 18 | `reporte/` | ✅ Hecho (2026-10-27, sin migración): `/api/reportes/gasto`, `/inventario`, `/proveedores`, `/proveedores/{id}/estado-cuenta` |
| 18-a | `presupuesto/` | ✅ Hecho (2026-10-28, V20): `/api/presupuestos`; 409 `PRESUPUESTO_EXCEDIDO` y `SOLICITUD_DUPLICADA`; excepción con justificación y auditoría |
| 18-b | `contrato/` | ✅ Hecho (2026-10-28, V21): `/api/contratos`, ofertas y orden directa `/api/ordenes/directa`; nunca se aplica un precio vencido. Pendiente de este paso: escalas de precio por cantidad, alerta en el comparador si una cotización supera el precio pactado y RFQ con precio de referencia precargado |
| 18-c | `delegacion/` + SLA | ✅ Hecho (2026-10-28, V22): `/api/delegaciones`, `empresa.sla_aprobacion_horas` y `SlaAprobacionJob`. Pendiente de este paso: SLA para adjudicaciones (hoy solo solicitudes) |
| 18-d | reorden | ✅ Hecho (2026-10-28, sin migración): `/api/reorden/sugerencias`, `/api/reorden/borrador` y `/api/productos/carga-masiva`. Pendiente de este paso: sugerir proveedor con escalas de precio por cantidad |
| SaaS 2 | plataforma | ✅ Hecho (2026-10-28, V23): `/api/plataforma/**` con MFA, alta de empresa con su admin, planes, módulos, `@RequiereModulo`, suspender y reactivar. Pendiente: panel de métricas de uso y límites por plan (SaaS 4) |
| 19 | Cierre | Pruebas de punta a punta con varias empresas, revisión de seguridad, Swagger completo; puntos 2 y 3 del plan de mejoras |

### Plan SaaS por fases (`references/saas.md`)
| Fase | Contenido | Estado |
|---|---|---|
| 0 | Decisiones: BD compartida con `empresa_id`, proveedor por empresa, planes y módulos | ✅ |
| Sesión | JWT 15 min, refresh rotativo, bloqueo de cuenta, límite por IP, CAJERO (V16) | ✅ 2026-10-23 |
| 1 | Aislamiento por empresa: `empresa_id` en el JWT y filtro central, consecutivos por empresa, únicos compuestos, pruebas de fuga (V17) | ✅ 2026-10-23. Falta: carpeta de archivos por empresa |
| 2 | Consola de plataforma: `usuario_plataforma` + MFA, alta de empresa, planes y módulos, `@RequiereModulo`, suspender y reactivar, auditoría global | ✅ 2026-10-28 (V23) |
| 3 | Seguridad restante: MFA, recuperación de clave con token de un solo uso, revocar sesiones al cambiar clave o desactivar usuario, dos usuarios MySQL, cabeceras seguras, escaneo de dependencias | ✅ 2026-11-10 (V24). Falta probar `permisos.sql` en un servidor real |
| 4 | Suscripción y cobro en sandbox, estados de mora, límites por plan, métricas de uso | ⏳ |
| 5 | Docker Compose, CI/CD, entornos, logs con `empresa_id`, onboarding de una empresa en minutos | En parte: Docker Compose y perfil `prod` ✅ 2026-11-17; el CI es la fase F0 de la spec de ventas (sección 6) |
| Futuro | DIAN, multimoneda, PWA de recepción sin conexión, exportar a contabilidad, importar ventas del POS | ⏳ |

Modelo de datos previsto: `plan`, `modulo`, `plan_modulo`, `empresa_modulo`, `suscripcion`, `usuario_plataforma`. Estados de suscripción: PRUEBA, ACTIVA, MOROSA, SUSPENDIDA, CANCELADA.

### Referencias de mercado y qué se adopta (2026-10-23)
Tin Pos (POS para ferreterías), Alegra, Precoro, Odoo Compras, Tradogram y Cotiss. Se adopta, sin perder el foco de compras antes de la factura: costo actualizado al recibir (hecho), carga masiva por Excel, estado de cuenta por proveedor, bloqueo de solicitudes duplicadas y agrupar solicitudes en una orden, precios por proveedor con escalas, reorden que sugiere proveedor, lectura de facturas con la misma IA de las cotizaciones, exportar la compra validada a la contabilidad, imágenes de producto. No se construye: POS, caja, contabilidad, comisiones ni factura de venta. Posicionamiento: «el cerebro de compras detrás del POS». Detalle en `saas.md`.

### Plan de mejoras (revisión externa del 2026-10-09)
Se revisaron 7 observaciones contra el código. Resultado y decisión:

| # | Observación | Estado real | Qué se hace | Cuándo |
|---|---|---|---|---|
| 1 | Se paga antes de recibir; no hay factura ni cruce de tres vías | ✅ Hecho en el 17-bis (2026-10-23). Antes: se pagaba desde EMITIDA sin distinguir anticipo | `pago.tipo` = ANTICIPO / PAGO_FINAL. Anticipo con tope configurable por empresa. Tabla `factura_proveedor`. PAGO_FINAL solo con la orden recibida y una factura que cuadre dentro de la tolerancia (si no cuadra, queda en revisión). El pago final descuenta los anticipos | Paso 17-bis ✅ |
| 2 | La auditoría solo es inmutable por la aplicación | Pendiente | Dos usuarios MySQL: `supply_migrator` (dueño, solo Flyway vía `spring.flyway.user`) y `supply_app` sin UPDATE/DELETE en `auditoria`, `movimiento_inventario`, `aprobacion_solicitud`, `orden_compra_estado_historial`, `evaluacion_proveedor`, `documento`. Script en `docs/sql/permisos.sql` (no es migración: depende del ambiente). Límite a explicar: root aún puede modificar; blindarlo exige encadenar hashes (fuera de alcance) | Paso 19 |
| 3 | Un PDF podría engañar la extracción de la IA | Hecha (2026-11-16): confianza < 0.80 ⇒ revisión, no se publica con lecturas pendientes, salida con esquema JSON y sin herramientas, Java calcula el dinero | Cruce de totales hecho: la IA extrae el total impreso (como texto) y Java lo compara con Σ líneas; diferencia > 1 % ⇒ REQUIERE_REVISION con alerta `TOTAL_NO_CUADRA` | Paso 19 |
| 4 | No se menciona IVA ni moneda | Ya existe: `impuesto_pct` por línea en cotización y orden (V14); `empresa.moneda_base` y `cotizacion.moneda` = COP | Solo documentar: moneda única COP (ofertas comparables). Multimoneda queda como fase futura | Documento |
| 5 | Referencias polimórficas sin FK | Ya existe el índice compuesto: `idx_documento_entidad (entidad_tipo, entidad_id)` e `idx_auditoria_entidad (entidad, entidad_id)` en V1 | Solo explicar: una FK por entidad serían 10+ columnas casi siempre nulas; el código valida existencia (regla por módulo), nada se borra y el índice mantiene rápidas las consultas | Documento |
| 6 | Cierre automático de RFQ con dos instancias | Ya protegido: `RfqService.cerrarVencidas` bloquea cada RFQ (`FOR UPDATE`) y vuelve a verificar ABIERTA y vencida antes de cerrar | Solo explicar; con varias instancias reales se agregaría ShedLock por eficiencia, no por correctitud | Documento |
| 7 | Documento de exposición desordenado | Pendiente (el PDF es del paso 11) | Una sola numeración (pasos 0–19, el flujo muestra el paso real); quitar la Ñ suelta de la página 6 (sale al regenerar); tabla de los roles (ahora 10 con CAJERO); partir el diagrama ER en tres (organización e inventario, ciclo de compra, cierre) | Al final, junto con el regenerado del PDF |

Orden final: 17 ✅ → 17-bis ✅ → 18 → 18-a a 18-d → SaaS 2 → SaaS 3 → 19 (incluye 2 y 3) → documento (incluye 4, 5, 6 y 7) → frontend → SaaS 4 y 5. No se rehace nada de lo construido; solo se agregan piezas.

### Pendientes técnicos conocidos
- Pasar `@Value` a `@ConfigurationProperties` (CORS y admin) y crear perfiles `dev`/`prod`. En `prod`, exigir `JWT_SECRET`.
- `@DataJpaTest` con Testcontainers cuando haya Docker (hoy las pruebas contra BD usan el MySQL local, base `supply_core_test`).
- Exportación parte 2: reportes de gasto y proveedores en Excel/PDF, PDF de la orden para el proveedor desde su portal.
- Correo real: reemplazar `LogEmailClient` por un `EmailClient` SMTP con credenciales en `application-local.yaml`.
- Seguridad fase 3: probar `backend/sql/permisos.sql` en el ambiente real (dos usuarios MySQL), y la caché de sesión (`app.sesion.cache-segundos`, 15 s) es por instancia: con varias el corte llega al vencer esa memoria. Escaneo de dependencias: solo Dependabot; falta una corrida de OWASP Dependency-Check con clave de la NVD. Si un ADMIN_SISTEMA pierde su teléfono: otro administrador reinicia su MFA (`PATCH /api/usuarios/{id}/mfa/reiniciar`); si es el único, falta una ruta de la consola de plataforma para reiniciarlo.
- Rate limit en memoria: con varias instancias usar Redis; detrás de un proxy configurar la IP real (`server.forward-headers-strategy`).
- Archivos subidos en carpeta por empresa (`LocalStorageClient`).
- Dar a CAJERO acceso a los endpoints de despacho y decidir si puede anular.
- Segregación de funciones en la recepción (quien emitió la orden no recibe), si se quiere.
- Validar la existencia de `proveedorId` al crear usuarios PROVEEDOR desde el admin.
- IA real: Gemini probado en vivo con un PDF de ejemplo (`GEMINI_API_KEY`, `IA_PROVEEDOR=gemini`); falta probar con cotizaciones reales y, si se quiere, Ollama (`ollama pull qwen2.5vl:3b`) o `ANTHROPIC_API_KEY`. Leer también las facturas con IA (idea 17-bis, no hecha).
- Orden: decidir si la evidencia de despacho es obligatoria y si se expone `GET /api/ordenes/{id}/versiones` (`findByConsecutivoOrderByVersionAsc` ya lo usan los pagos y las facturas).
- Pagos: si una orden con pagos se cancela o el proveedor la rechaza, el saldo a favor solo queda visible en el resumen; no hay devolución (fuera de alcance).
- Factura: una sola vigente por orden; considerar facturación por entregas si algún cliente la pide.
- Despacho: carga del cierre de caja desde Excel/CSV, si la ferretería usa otro programa de caja.
- Frontend Angular: 10 vistas del mockup (`feature/frontend-initial`) + usuarios, solicitudes, perfil, RFQ nueva, factura y consola de planes y auditoría (`feature/frontend-vistas`) + fases del `PLAN_FRONTEND.md` (develop) + datos de la empresa, plan de reabastecimiento por plazo de entrega y tablero del dueño (`feature/reabastecimiento`); lo que falta está en `references/frontend.md` › Pendiente.
- **Seguridad personal:** en la sesión del 2026-10-23 la contraseña local de MySQL apareció en pantalla; cámbiala si compartes la conversación o el repositorio.

### Cómo retomar
```bash
cd backend
./mvnw test
./mvnw spring-boot:run
```
- Para crear el admin la primera vez, define `ADMIN_PASSWORD`; pertenece a la primera empresa (código público `demo`). El login se hace con `{empresa: "demo", correo, password}`.
- Variables de entorno nuevas (todas opcionales): `JWT_EXPIRACION_MINUTOS` (15), `REFRESH_DIAS` (7), `LOGIN_MAX_INTENTOS` (5), `LOGIN_BLOQUEO_MINUTOS` (15), `RATE_LIMITE_POR_MINUTO` (20).
- Las pruebas de fuga usan la base `supply_core_test` (se recrea sola) con la misma contraseña de MySQL de `application-local.yaml`; correrlas con `./mvnw test -Dtest=AislamientoEmpresasTest`.
- Swagger: `http://localhost:8080/swagger-ui.html`.

---

## Diseño y marca (fuera del backend)

- **Mockup de la aplicación** (2026-10-27): lienzo de 10 pantallas publicado como Artifact (10 pantallas: ingreso, inicio, solicitudes con aprobación, comparador con IA, orden con cruce de tres vías, recepción con calidad, inventario con reorden, estado de cuenta de reportes, consola del dueño y portal del proveedor). Es solo diseño: usa los colores base `#0F766E` (verde azulado), `#0B1F2E` (azul noche) y `#F59E0B` (ámbar). Las pantallas del presupuesto, contratos, delegación y consola con módulos que hizo el backend después no están dibujadas todavía.
- **Logo: Cajabot** (2026-10-28): mascota elegida por el usuario (una caja con cara, antena y herramienta). Archivos en `branding/`: `cajabot-icono.svg` y `.png`, `cajabot-icono-oscuro.svg`, `cajabot-logo-vertical.svg` y `.png`, `cajabot-logo-horizontal.svg`. Pendiente: convertir el nombre a curvas o fijar la tipografía final, probar el favicon a 16 px y exportar PNG de 512, 192 y 32, y poner el logo en el mockup. Los archivos `supply-core-*.svg` (tuerca con tornillo) son un boceto descartado y se pueden borrar.
- **Frontend Angular:** empezado el 2026-11-05 con las 10 vistas del mockup (Angular 22 + Material, conectado a la API real). El equipo decidió adelantarlo a SaaS 3 y al paso 19. Hay una skill de diseño de interfaces (`design-taste-frontend`) disponible para esa etapa.

---

## 5. Sesión del 2026-11-10 al 2026-11-17: qué se hizo y qué sigue (para retomar tras una compactación)

### Qué se hizo, en orden
1. **SaaS fase 3** (commit `08cea53`, V24). Versión de sesión en el JWT (`sv`): cambiar la clave o desactivar al usuario corta su sesión en la siguiente petición (`SesionUsuarioService`, memoria de 15 s). Recuperación de clave con token de un solo uso (`/api/auth/recuperar` y `/restablecer`). MFA TOTP de usuarios de empresa: obligatorio para `ADMIN_SISTEMA`, voluntario para los demás (`/api/auth/mfa`, `/mfa/configurar`, `/mfa/activar`, `PATCH /api/usuarios/{id}/mfa/reiniciar`). Cabeceras seguras, Dependabot y `backend/sql/permisos.sql` (dos usuarios MySQL; **sin probar en un servidor real**).
2. **Frontend en la rama.** Se trajo `develop` (frontend inicial de Martin, Angular 22) a `feature/modulo1`, y luego `feature/frontend-vistas` de Martin (usuarios, solicitudes, perfil, RFQ nueva, factura, planes y auditoría de la consola). Pull Requests #3, #4, #5 y #6 ya mezclados en `develop`.
3. **App en local.** Backend con perfil `demo` (base `supply_core_demo`, puerto 8080) y frontend `npm start` (4200). Se crearon el administrador de la empresa `demo` y el dueño de la plataforma con las claves que el usuario puso en `application-local.yaml` (nunca en el repositorio). Ayudas locales sin versionar: `.claude/launch.json`, `.claude/run-backend.cmd`, `.claude/run-frontend.cmd`.
4. **Código de ingreso libre** (commit `0b5d0f2`, PR #4). El código público de la empresa acepta espacios, mayúsculas, tildes y números (3 a 40 caracteres; sin `/ \ ? # %`); la lista de empresas de la consola muestra solo el nombre y el detalle muestra todo, incluido el administrador (nombre y correo).
5. **Cruce de totales de la IA y Gemini** (commit `6d7481b`, PR #6). La IA copia el total impreso (`totalImpreso`) y `CruceTotal` lo compara en Java con el calculado; más de 1 % de diferencia deja la cotización en `REQUIERE_REVISION` con la alerta `TOTAL_NO_CUADRA`. `GeminiIAClient` (`app.ia.proveedor=gemini`, clave en `GEMINI_API_KEY`, modelo `gemini-2.5-flash`) lee PDF e imágenes con visión y Excel y Word convertidos a texto en Java; el Word (docx) también se acepta al subir archivos. Probado contra la API real con un PDF de ejemplo.
6. **Herramientas.** `graphify` actualizado (5140 nodos, con `manifest.json`: la próxima vez es incremental), `codebase-memory-mcp` indexado, mockup rediseñado en el Artifact (paleta negro, blanco, gris y naranja `#D93A0B`, panel izquierdo de vidrio con forma de burbuja 3D, hover de agua en los botones principales).

### Estado del repositorio
- `develop` = `origin/develop` (`19e0d74`); `feature/modulo1` = `6d7481b`; `main` sigue en el commit inicial (nada de esto está en `main`).
- 714 pruebas del backend en verde (1 omitida: la prueba en vivo contra Gemini, solo con `GEMINI_PRUEBA_VIVA=1`), 41 de fuga entre empresas, doclint sin advertencias; 15 pruebas del frontend.
- Cómo correr en local: `cd backend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=demo` (con `IA_PROVEEDOR=gemini` para leer archivos con IA) y `cd frontend && npm start`. Login de empresa en `http://localhost:4200/ingreso` con código `demo`; consola del dueño en `/plataforma/ingreso` (pide MFA).

### Qué falta (en este orden)
1. **Paso 19, cierre del backend:** prueba de punta a punta con dos empresas a la vez; revisión de seguridad; Swagger completo; correo real por SMTP (hoy `LogEmailClient`); `@Value` a `@ConfigurationProperties` y perfiles `dev` y `prod` (en `prod`, `JWT_SECRET` obligatorio); carpeta de archivos por empresa en `LocalStorageClient`; aplicar y probar `permisos.sql`; corrida de OWASP Dependency-Check (clave de la NVD); ruta de la consola para reiniciar el MFA de un administrador único; cambiar la clave local de MySQL.
2. **IA con cotizaciones reales:** probar Gemini (y Ollama o Claude) con documentos de la ferretería, no solo con el PDF de ejemplo. Con clave del nivel gratuito Google puede usar lo enviado para mejorar sus productos: para datos reales, activar facturación. La suscripción Gemini Plus de la app no da acceso a la API.
3. **Frontend:** mostrar en el análisis la alerta `TOTAL_NO_CUADRA` y el dato `totalImpreso` (ya vienen en `ProcesamientoResponse.alertas` y `campos`); pantalla para editar el código de ingreso de una empresa; lo demás está en `references/frontend.md` › Pendiente. Regenerar los tipos con `npm run api:tipos` después de cada cambio de DTO.
4. **Documento de exposición:** regenerar el PDF con la numeración 0–19 y el diagrama ER partido en tres (ver el plan de mejoras, punto 7).
5. **SaaS 4 y 5:** cobro de la suscripción en sandbox, Docker Compose, CI/CD.
6. **Cuando el backend y el frontend estén cerrados:** mezclar `develop` en `main`.

### Trampas conocidas de este entorno
- La hora del PC debe estar sincronizada: el MFA (TOTP) tolera solo unos 30 s, y cinco fallos bloquean la cuenta 15 minutos mostrando "Credenciales inválidas" (el mensaje no distingue el bloqueo a propósito).
- En Windows: usar `py` (no `python`); los heredocs con apóstrofes rompen el shell, mejor escribir el script a un archivo; `docs/` está bloqueado para lectura y escritura.
- El perfil `demo` tiene el MFA de administrador apagado y toma el proveedor de IA de `IA_PROVEEDOR`; el perfil `test` apaga los jobs y la memoria de sesión.

---

## 6. Sesión del 2026-11-17 al 2026-11-19: qué se hizo y qué sigue (para retomar tras una compactación)

Solo lo que no estaba en las secciones anteriores. Todo quedó mezclado en `develop` por PR (#7 a #23); `main` sigue sin estos cambios.

### Qué se hizo, en orden

**Frontend completo (fases 0 a 8 de `PLAN_FRONTEND.md`, PR #7, #8, #15 a #18).**
1. **Fases 1 y 2:** revisión de la lectura con IA (con la alerta `TOTAL_NO_CUADRA` y el total impreso), exportaciones por correo, ingreso con MFA, recuperar contraseña, MFA en el perfil y la pantalla de avisos y correos.
2. **Fase 3, inventario:** movimientos de stock, kardex, ajustes y traslados.
3. **Rotación de productos** (pedido aparte): reporte nuevo en el backend (`RotacionController` y `RotacionService`, `GET /api/reportes/rotacion`) y tablero en `/app/inventario/rotacion`. La salida cuenta `SALIDA_CONSUMO` + `SALIDA_VENTA` − `ANULACION_DESPACHO` (sin ajustes ni traslados). Clases: ALTA si la cobertura es de 30 días o menos, BAJA si pasa de 90, "por agotarse" si es de 15 o menos. Periodo de 90 días por defecto, máximo 366. Con prueba de fuga.
4. **Fases 4 y 5:** despachos y pagos con anulación y comprobante; adjudicaciones, delegaciones (endpoint nuevo `GET /api/delegaciones/candidatos`), orden directa por contrato y acciones sobre las órdenes.
5. **Fase 6:** configuración de la empresa con un índice en `/app/configuracion`, catálogos simples con una pantalla genérica, reglas de compra y ficha de proveedores.
6. **Fase 7, portal del proveedor:** registro público, perfil con calificaciones y recepciones en sus órdenes. Endpoint nuevo `GET /api/portal/proveedor/categorias`, porque el proveedor no puede leer `/api/categorias`.
7. **Fase 8:** auditoría de la empresa con filtros, períodos rápidos y detalle, con los textos de las 96 acciones y 28 entidades auditadas.

**Docker (PR #8 y #19).**
- `docker-compose.yml` con `db` (MySQL 8.4), `api` (perfil `prod`) y `web` (nginx). Solo nginx publica puerto: `WEB_PORT`, 8088 por defecto.
- Imágenes multi-etapa, sin root, con sistema de archivos de solo lectura y `cap_drop: ALL`. MySQL y el backend viven en redes internas.
- Perfil `application-prod.yaml` sin valores por defecto para secretos: sin `DB_URL`, `DB_USER`, `DB_PASSWORD` o `JWT_SECRET` no arranca. Swagger apagado y solo `/actuator/health` expuesto.
- nginx sobrescribe `X-Forwarded-For` y Spring usa `forward-headers-strategy: native`, así el límite por IP no se puede esquivar.
- La CSP exigió `optimization.styles.inlineCritical: false` en Angular. Guía en `DOCKER.md`; `.gitattributes` fuerza LF en Dockerfile, nginx y compose.

**Datos de demostración y ajustes de uso (PR #19 y #20).**
1. **`scripts/sembrar_demo.py`:** siembra por la API pública una empresa `Ferreteria Demo` con todos los roles, productos, existencias, políticas y un proveedor. Las claves aleatorias y las llaves MFA van a `credenciales-docker.local.txt` (ignorado por git).
2. **Arreglo del ingreso con segundo factor:** al formulario del código le faltaba `FormsModule` y el navegador lo enviaba como GET, volviendo al inicio.
3. **Menú lateral:** sin "Ver como dueño" para nadie de la empresa, sin el enlace al dueño en el ingreso y sin la barra de scroll.
4. **Campana:** sección "Por decidir" para aceptar o rechazar proveedores nuevos; cada aviso lleva a su pantalla.
5. **Proveedores:** pantalla responsive con container query, y la ficha muestra "Cómo entra al portal" con el código de empresa. `EmpresaResponse` ahora trae `codigoPublico`.
6. **Selector de productos en la solicitud:** catálogo al hacer clic, búsqueda por código o nombre, "parecidos" si varias palabras no coinciden, y alta del producto desde ahí para quien administra compras.
7. **Portal del proveedor:** el archivo subido queda a la vista y la cotización se puede enviar solo con el archivo.
8. **Flujo de la ferretería en la demo:**
   - El bodeguero también es solicitante.
   - El jefe de compras es administrador de compras, comprador y solicitante.
   - La política de solicitudes la aprueba `ADMIN_COMPRAS`.
   - El dueño solo administra y no recibe avisos de compras.
9. **Cotización de ejemplo** en `ejemplos/cotizacion-electro-sur.png` para probar el portal.

**Informe de compras para el dueño (PR #20 y #21, V25).**
- **El flujo:** el dueño (`ADMIN_SISTEMA`) lo pide con un periodo y un mensaje. El jefe de compras (`ADMIN_COMPRAS`) revisa los datos en vivo, escribe su análisis y lo envía, y al dueño le llega "sin leer".
- **Qué trae:** solicitudes con sus productos, cantidades y cuenta (cantidad × costo = subtotal), RFQ, cotizaciones recibidas, adjudicaciones (con si se apartó de la recomendación y por qué) y el gasto del periodo.
- **Si el monto cuadra:** Java rehace la cuenta con los costos de hoy y la compara con el monto estimado guardado al enviar: "Cuadra", "No cuadra" o "no se puede comprobar".
- **Foto fija:** al enviarse queda guardada en JSON y no cambia aunque cambien los datos.
- **Rutas:** `/api/informes-compras` (listar, detalle, vista previa, pedir, enviar, leído). Avisos `INFORME_PEDIDO` e `INFORME_RECIBIDO`, auditoría `INFORME_PEDIR` e `INFORME_ENVIAR`, pantalla `/app/informes`, `InformeComprasServiceTest` y prueba de fuga.
- **Aprobación por el dueño:** si quien pide una solicitud tiene el rol aprobador de su política (el jefe de compras), la decide el dueño. Se calcula al enviarla (`aprueba_dueno`); la bandeja, la decisión y el SLA lo respetan.

**Spec del módulo de Ventas y Caja (PR #22 y #23).** Se escribió en tres partes y después se pasó al formato de Spec Driven Development (2026-11-19, ver más abajo).

**Specs con Spec Driven Development (2026-11-19).** `AGENTS.md` en la raíz y la carpeta `agents/` con el contexto fijo (`product.md`, `tech-stack.md`, `architecture.md`) y una carpeta por spec en `agents/specs/NNN-nombre/` con `spec.md`, `plan.md` y `tasks.md`. La spec de ventas quedó repartida en las specs 000 (CI) a 010 (endurecimiento), una por fase F0 a F10, sin perder contenido; los archivos `SPEC_VENTAS*.md` se borraron. Se alineó con la rama `feature/mejoras-negocio` (de Advilmar24, mezclada después en el PR #27): esa rama usa V26 a V30 y ya construyó gran parte de la F1 (precios, impuestos y listas bajo `/api/ventas/`), así que ventas sigue en V31 y las decisiones D2 y D8 quedan tomadas. Las migraciones se reservan en `agents/specs/README.md`.

**Herramientas y forma de trabajo.**
- **Flujo de git:**
  - Se trabaja desde `develop`; cada tarea sale en una rama nueva desde `develop` actualizado.
  - El dueño del repositorio crea o aprueba el PR y hace el merge.
  - Antes de cada PR se simula la mezcla con `develop` para avisar si hay conflictos.
- **Dependabot:** los PR #10 a #14 se revisan y se cierran a mano.
- **`graphify`:** actualizado el 2026-11-19 (6.519 nodos, 247 comunidades). No se extrajeron `docs/` ni las imágenes de `branding/`.

### Estado del repositorio
- `develop` = `origin/develop` (`c6c0c55`, merge del PR #23); `main` sigue en el commit inicial.
- **Backend:** 743 pruebas en verde (1 omitida), 45 de fuga entre empresas, 25 migraciones (V26 a V30 reservadas por `feature/mejoras-negocio`; ventas sigue en V31).
- **Frontend:** 127 pruebas en verde.
- **Docker:** `http://localhost:8088`, con la empresa `Ferreteria Demo` sembrada. Credenciales en `credenciales-docker.local.txt`, nunca en el repositorio.
- **Local sin Docker:** backend en el puerto 8081, porque el 8080 lo ocupa un relé de WSL; `frontend/proxy.conf.json` apunta a 8081 y ese cambio no se sube.

### Qué falta y cómo lo pensamos hacer (en este orden)

> **Actualizado en la sección 7.** Los puntos 1 a 3 ya están hechos y el 4 va por la mitad (003 hecha). La lista se deja como estaba ese día para conservar la historia.

1. **Mezclar `feature/mejoras-negocio`** a `develop` (V26 a V30) después de confirmar que sus pruebas pasan, y aprobar las decisiones pendientes (D1, D3 a D7 y D9) en `agents/specs/README.md`. D2 y D8 ya quedaron tomadas y `CLAUDE.md` ya incluye Ventas y Caja en el alcance.
2. **Spec 000, CI con GitHub Actions** (rama `feature/ci-github-actions`, detalle en `agents/specs/000-ci-github-actions/`):
   - `.github/workflows/ci.yml` con `backend` (Java 21, MySQL 8 de servicio, `./mvnw verify`, Javadoc con doclint, JaCoCo), `frontend` (`npm ci`, pruebas y build de producción) y `docker` (construir las imágenes y escanearlas con Trivy).
   - Dependency Review en cada PR y CodeQL semanal, sin secretos reales.
   - Después, el dueño del repositorio activa la protección de `develop` y `main` para exigir el CI antes de mezclar.
3. **Specs 001 y 002** (F1 y F2): completar el catálogo de venta (módulos `CAJA` y `CARTERA`, roles nuevos, un solo precio vigente garantizado por la base, precio por unidad, carga de precios por Excel, búsqueda para vender) y clientes con autorización de datos. Desde V31.
4. **Specs 003 a 006** (F3 a F6), con las decisiones D1, D3, D6 y D7:
   - Cajas y turnos con cierre ciego y PIN de supervisor.
   - La venta en una sola transacción (stock, caja e idempotencia).
   - POS con lector y tiquete de 80 mm.
   - Anulación y devolución.
5. **Specs 007 a 010** (F7 a F10), con la decisión D4:
   - Cartera.
   - Costo promedio y margen.
   - Reportes, tablero y cuadre diario automático.
   - Alertas y pruebas de punta a punta con Playwright en el CI.
6. **Pendientes del frontend:**
   - Fase 9: nombre del solicitante en la bandeja, conteo de usuarios por empresa en la consola, variación frente al mes anterior.
   - Fase 10: pruebas de punta a punta, accesibilidad y build sin advertencias.
   - Editar el código de ingreso de una empresa, que primero necesita la ruta en el backend.
   - Filtro por bodega y Excel en la rotación.
7. **Paso 19, cierre del backend** (sigue lo de la sección 5):
   - Prueba de punta a punta con dos empresas.
   - Correo real por SMTP.
   - Carpeta de archivos por empresa.
   - Probar `permisos.sql` en un servidor real.
   - OWASP Dependency-Check.
   - Ruta de la consola para reiniciar el MFA del único administrador.

   El perfil `prod` con `JWT_SECRET` obligatorio ya quedó hecho con Docker.
8. **IA con cotizaciones reales:** probar Gemini con documentos de la ferretería. En Docker la IA está apagada (`IA_PROVEEDOR=ninguno`); para activarla se pone `IA_PROVEEDOR=gemini` y la clave en el `.env`, sabiendo que los documentos salen hacia Google.
9. **Demo:** los productos de la demo actual quedaron en "CAJA24" por un error ya corregido en el script. Al sembrar de nuevo quedan en "UND".
10. **SaaS 4:** cobro de la suscripción en sandbox, límites por plan y métricas de uso.
11. **Documento de exposición:** regenerarlo con la numeración 0–19 y el diagrama ER en tres partes. **Mezclar `develop` en `main`** cuando el backend y el frontend estén cerrados.

### Trampas nuevas de este entorno
- **Docker Desktop:** debe estar encendido; si no, la app de `http://localhost:8088` no responde. Al encenderlo, los contenedores vuelven a subir solos.
- **CLI de GitHub:** no está instalado (`gh`). Los PR se crean desde la página de GitHub con la sesión del usuario.
- **Finales de línea:** muchos archivos usan CRLF y `sed` no casa los cambios de varias líneas. Mejor editar con un script de Python que normalice los saltos de línea.
- **Límite de ingreso:** después de muchos ingresos seguidos, `/api/auth/login` responde 429 por un minuto. Es el rate limit funcionando.

---

## 7. Specs de Ventas y Caja: qué está hecho y qué falta (corte del 2026-11-19, después del PR #30)

Las specs viven en `agents/specs/NNN-nombre/` (`spec.md`, `plan.md`, `tasks.md`); el índice, las decisiones y la reserva
de migraciones están en `agents/specs/README.md`. Flujo: spec aprobada → plan → tasks una a una con sus pruebas → criterios
de aceptación verificados → rama propia desde `develop` → PR que crea y mezcla el dueño del repositorio.

### Estado del repositorio
- `develop` = `origin/develop` (`cc5cca4`, merge del PR #30); `main` sigue en el commit inicial.
- **Backend:** 796 pruebas en verde (1 omitida, la de Gemini en vivo), Javadoc sin advertencias, 33 migraciones
  (próxima libre **V34**).
- **Frontend:** 166 pruebas en verde (29 archivos); build de producción sin advertencias.
- **CI:** cada PR corre `backend`, `frontend`, `docker` (Trivy) y Dependency Review; CodeQL los lunes.

### Avance por spec

| Spec | Qué entrega | Migración | PR | Estado |
|---|---|---|---|---|
| 000 CI | GitHub Actions: backend con MySQL 8.4, frontend, imágenes con Trivy, Dependency Review, CodeQL | — | #26 (+ zona horaria en #29) | **Hecha** |
| 001 Catálogo de venta | Módulos `VENTAS`, `CAJA`, `CARTERA`; roles `SUPERVISOR_VENTAS` y `VENDEDOR`; impuestos, listas y precios con historial; precio por unidad; carga de precios por Excel; búsqueda para vender | V29 + V31 | #27 + #28 | **Hecha** |
| 002 Clientes | Clientes con consumidor final, documento validado (NIT con dígito DIAN), autorización de datos, cupo solo para dueño o supervisor, exportación | V32 | #29 | **Hecha** |
| 003 Cajas y turnos | Cajas, turnos, libro de caja, PIN de supervisor, autorizaciones de un solo uso, cierre ciego, aprobación de diferencias | V33 | #30 | **Hecha** |
| 004 La venta | Venta en una sola transacción: precio vigente, descuentos con límite por rol, impuestos, pagos mixtos, cambio, redondeo, stock, caja, idempotencia, pedido de venta | V34 | (rama `feature/ventas-pos`) | **Hecha** (2026-11-20, falta PR) |
| 005 POS | POS con lector y atajos, cobro, tiquete de 80 mm, "Mi turno" (con la foto del recibo del gasto), historial de ventas | — | (rama `feature/ventas-pos`) | **Hecha** (2026-11-20, falta PR) |
| 006 Anulación y devolución | Anular en el turno, devolver con estado del producto y reembolso, rotación que las descuenta | V35 | (rama `feature/ventas-pos`) | **Hecha** (2026-11-20, falta PR) |
| 007 Cartera | Venta a crédito con cupo y mora, abonos en la caja, edades, saldo a favor | V37 | (rama `feature/ventas-pos`) | **Hecha** (2026-11-20, falta PR) |
| 008 Costo promedio | Costo promedio ponderado en la recepción, costo en la línea de venta, margen real | V36 | (rama `feature/ventas-pos`) | **Hecha** (2026-11-20, falta PR) |
| 009 Reportes y cuadre | Reportes de ventas y caja, bloque de ventas en el tablero, cuadre diario automático | — | — | Propuesta |
| 010 Endurecimiento | Alertas por cajero, Playwright en el CI, prueba de carga de 50 ventas por minuto | V39 | (rama `feature/ventas-endurecimiento`) | **Hecha** (2026-11-20, falta PR y ver el `e2e` en GitHub) |

### Lo que dejó cada spec hecha (lo esencial para retomar)
- **000:** `.github/workflows/ci.yml`, `dependency-review.yml` y `codeql.yml`; JaCoCo en el `pom.xml`. Trivy obligó a fijar
  Tomcat 11.0.29 (`tomcat.version`) y a pasar la imagen `web` a `nginx-unprivileged:1.31-alpine` con `apk upgrade`.
  El job `backend` y su MySQL corren con `TZ=America/Bogota`, igual que Docker.
- **001:** paquete `venta/` (`PreciosService`, `PrecioCargaService`, `VentaProductoService`). Un solo precio vigente por
  producto, lista y unidad garantizado con la columna generada `vigente_unico`; dos personas a la vez ⇒ una gana y la
  otra recibe 409 `PRECIO_CONCURRENTE`. Rutas bajo `/api/ventas/` (no las de la spec original).
- **002:** paquete `cliente/`. El consumidor final (CC `222222222222`) se crea solo en la primera consulta para las
  empresas nuevas: la consola de plataforma no toca tablas de negocio. `GET /api/ventas/listas` lo leen también caja y
  venta (solo nombres).
- **003:** paquete `caja/` con `CalculoCaja`. El cajero no ve ventas ni esperado con el turno abierto, solo `debeRetirar`.
  La tolerancia se configura en `/api/cajas/configuracion`. `AutorizacionPin` (frontend) queda listo para el POS y la
  anulación.

### Decisiones

| # | Tema | Estado |
|---|---|---|
| D2 | Precio de mostrador con IVA incluido | Tomada |
| D5 | Roles `SUPERVISOR_VENTAS` y `VENDEDOR` | Tomada |
| D6 | Tolerancia de diferencia de caja $2.000, configurable | Tomada |
| D7 | Una caja, una bodega | Tomada |
| D8 | Precios los fija el dueño, delegable a compras | Tomada |
| D9 | Precio por caja y por unidad | Tomada |
| **D1** | ¿Vender sin stock? Recomendado: **no** | **Pendiente: hace falta para la 004** |
| **D3** | ¿Redondear el efectivo a $50? Recomendado: **sí**, guardando la diferencia | **Pendiente: hace falta para la 004** |
| **D4** | ¿Crédito en la primera versión? Recomendado: **no**, llega en la 007 | **Pendiente: hace falta para la 007** |

### Pendientes que dejaron las specs hechas
1. **Regenerar los tipos del frontend** con `npm run api:tipos` (desde la 001 se agregan a mano en `esquema.ts`, porque el
   backend local no arrancó desde el panel).
2. **Volver a sembrar la demo** desde cero (`docker compose down -v`, `up -d --build` y `scripts/sembrar_demo.py`): el
   script ya crea cajero, vendedor, supervisor de ventas, precios (uno por docena), dos clientes, una caja y el PIN del
   supervisor, pero no se ha ejecutado con esos cambios.
3. **Revisar en el navegador** las pantallas nuevas: precios (unidad y Excel), `/app/clientes` y `/app/caja`. Hasta ahora
   solo tienen pruebas Vitest.
4. **Protección de ramas en GitHub** (la activa el dueño del repositorio, guía en `DOCKER.md`) y confirmar la primera
   ejecución semanal de CodeQL.
5. **Decidir si el usuario tendrá sede asignada**; mientras tanto el cajero ve todas las cajas activas.

### Qué sigue, en orden
1. **Spec 004, la venta** (rama `feature/ventas-pos-backend`, migración V34): tomar D1 y D3, aprobar la spec, y construir
   `venta`, `venta_linea`, `venta_pago`, el pedido de venta y los límites de descuento por rol; la venta en una sola
   transacción con bloqueo de stock en orden de id, idempotencia por `Idempotency-Key`, autorizaciones de la 003 y
   pruebas de concurrencia y de propiedades de dinero.
2. **Spec 005, el POS:** pantalla con lector y atajos, cobro con pagos mixtos, tiquete de 80 mm, "Mi turno" con la foto
   del recibo del gasto y `POST /api/ventas/cotizar`. Usa `ClienteFormulario` (002) y `AutorizacionPin` (003).
3. **Spec 006, anulación y devolución:** tipos `ANULACION_VENTA` y `DEVOLUCION_CLIENTE`, saldo a favor, bodega de averías.
   Con ella el negocio ya puede **vender y cuadrar caja**.
4. **Specs 007 a 010:** cartera (con D4), costo promedio y margen real, reportes y cuadre diario, y endurecimiento con
   Playwright en el CI.
5. **Lo que sigue de la sección 6:** pendientes del frontend (fases 9 y 10), paso 19 del backend, IA con cotizaciones
   reales, SaaS 4, documento de exposición y mezcla de `develop` a `main`.

### Trampas nuevas de este entorno
- **Zona horaria del CI:** en UTC, entre las 19:00 y las 24:00 UTC, las fechas que guarda MySQL se leían en el día
  siguiente y fallaban las pruebas por periodo. Se corrigió con `TZ=America/Bogota` en el CI. Para reproducir el CI en
  local: Docker Desktop encendido, un MySQL 8.4 en el puerto 3307 y `SPRING_DATASOURCE_URL`, `DB_USER` y `DB_PASSWORD` como
  variables de entorno.
- **Panel del navegador:** no arranca el backend local (`preview_start` sale sin mostrar el error) y no tiene la sesión de
  GitHub; los registros del CI y la creación de PR se ven desde el navegador del usuario.
- **Límite de intentos:** `/api/autorizaciones` comparte el límite por IP de `RateLimitFilter`; en pruebas se sube con
  `app.sesion.limite-por-minuto`.
