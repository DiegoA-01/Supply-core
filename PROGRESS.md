# Supply-Core — Progreso del backend

> Última actualización: 2026-11-05 · Estado: **pasos 0 a 18, 18-a a 18-d y SaaS fase 2 completos + 11-bis (despachos) + giro a SaaS multiempresa: seguridad de sesión (V16), aislamiento por empresa (V17), recepción con calidad (V18), factura con cruce de tres vías (V19) y presupuesto por centro de costo (V20), contratos con orden directa (V21), delegación con SLA de aprobación (V22), reorden sugerida con carga masiva por Excel (sin migración), consola de plataforma con planes y módulos (V23) y exportación a Excel y PDF con archivos legibles (sin migración)**.
> **635 pruebas en verde** (37 de fuga y de extremo a extremo entre empresas contra MySQL real), Javadoc sin advertencias (`doclint=all`), 23 migraciones Flyway, 51 controllers.
> Detalle vivo para Claude: `.claude/skills/supply-core/references/estado.md`, `plan-backend.md` y `saas.md`. Reglas no negociables: `CLAUDE.md`.

---

## 0. Resumen: en qué vamos

**Hecho (backend, pasos 0–17-bis + 11-bis).** El ciclo de compra funciona de punta a punta, incluida la entrada de mercancía, la factura del proveedor y el pago final:

necesidad → solicitud → aprobación → RFQ → cotizaciones (formulario o archivo leído con IA) → análisis y ranking → adjudicación → orden de compra → anticipo (con tope) → el proveedor acepta, prepara y despacha → **recepción** (rechazos con motivo, excedente en revisión, solo lo aceptado sube el stock) → **factura del proveedor cruzada con lo recibido** → **pago final contra factura** → cierre con evaluación del proveedor.

Además: usuarios y roles, proveedores, inventario con kardex, documentos, notificaciones, auditoría y despachos de salida.

**Giro a SaaS multiempresa (decidido el 2026-10-23).** El software pasa de «una empresa» a vender el uso a varias empresas (ferretería, eléctricos…), cada una con sus datos aislados, sus usuarios y sus roles; el dueño del software activa a cada empresa y sus módulos. Ya está hecho el aislamiento por empresa y la seguridad de sesión; falta la consola del dueño (fase 2), planes y módulos, el cobro de la suscripción y la operación (fases 3 a 5).

**Estamos en:** SaaS fase 3, seguridad restante (MFA para administradores de empresa, recuperación de contraseña, revocar sesiones al cambiar la clave o desactivar al usuario, dos usuarios MySQL). La fase 2, consola de plataforma, planes, módulos y `@RequiereModulo`, quedó hecha el 2026-10-28 con la migración V23.

**Falta del backend:**

| Paso | Qué es | Con qué se conecta |
|---|---|---|
| 18 | **Reportes y tableros** (UC-15): gasto, inventario y desempeño de proveedores, con filtros por fecha, categoría y proveedor; solo lectura. Incluye estado de cuenta por proveedor (comprado, pagado, saldo) | Todas las tablas anteriores |
| 18-a a 18-d | **Compras avanzadas**: presupuesto por centro de costo, contratos y precios pactados, delegación de aprobación y SLA, reorden sugerida; carga masiva por Excel de productos y precios | Solicitud, RFQ, orden, inventario |
| SaaS 2 ✅ | **Consola de plataforma**: `usuario_plataforma` con MFA, alta de empresa, planes, módulos, `@RequiereModulo`, suspender y reactivar | Todo |
| SaaS 3 | **Seguridad restante**: MFA, recuperación de clave, revocar sesiones al cambiar clave o desactivar usuario, dos usuarios MySQL, cabeceras seguras, escaneo de dependencias | Seguridad |
| 19 | **Cierre del backend**: prueba de punta a punta con varias empresas, revisión de seguridad, Swagger completo, cruce de totales de la IA, permisos de BD con dos usuarios, limpieza de pendientes técnicos y commit | Todo |

**Después del backend:**
1. **Frontend en Angular** (`frontend/`): pantallas por rol que consumen esta API (login con código de empresa, bandejas de aprobación, RFQ, recepción, facturas, portal del proveedor, inventario, reportes, consola de plataforma). Boceto de las pantallas ya mostrado en la sesión del 2026-10-23 (panel de la empresa con módulos bloqueados según el plan, y consola del dueño con empresas y módulos).
2. **SaaS 4**: cobro de la suscripción con pasarela (Wompi, Mercado Pago o Stripe) en sandbox, estados de mora, límites por plan, métricas de uso.
3. **SaaS 5**: Docker Compose, CI/CD, entornos, logs con `empresa_id`, onboarding de una empresa en minutos.
4. **Integración y prueba con IA real** (Ollama o Claude) con cotizaciones reales.
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
| **Próximo libre** | **V24** |

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
| 2 | Consola de plataforma: `usuario_plataforma` + MFA, alta de empresa, planes y módulos, `@RequiereModulo`, suspender y reactivar, auditoría global | ⏳ |
| 3 | Seguridad restante: MFA, recuperación de clave con token de un solo uso, revocar sesiones al cambiar clave o desactivar usuario, dos usuarios MySQL, cabeceras seguras, escaneo de dependencias | ⏳ |
| 4 | Suscripción y cobro en sandbox, estados de mora, límites por plan, métricas de uso | ⏳ |
| 5 | Docker Compose, CI/CD, entornos, logs con `empresa_id`, onboarding de una empresa en minutos | ⏳ |
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
| 3 | Un PDF podría engañar la extracción de la IA | Mitad hecha: confianza < 0.80 ⇒ revisión, no se publica con lecturas pendientes, salida con esquema JSON y sin herramientas, Java calcula el dinero | Falta el cruce de totales: la IA extrae el total impreso (como texto) y Java lo compara con Σ líneas; diferencia > 1 % ⇒ REQUIERE_REVISION con alerta `TOTAL_NO_CUADRA` | Paso 19 |
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
- Revocar los refresh tokens al cambiar o restablecer la contraseña y al desactivar un usuario (hoy el corte llega al vencer el JWT, máximo 15 min). MFA para admins.
- Rate limit en memoria: con varias instancias usar Redis; detrás de un proxy configurar la IP real (`server.forward-headers-strategy`).
- Archivos subidos en carpeta por empresa (`LocalStorageClient`).
- Dar a CAJERO acceso a los endpoints de despacho y decidir si puede anular.
- Segregación de funciones en la recepción (quien emitió la orden no recibe), si se quiere.
- Validar la existencia de `proveedorId` al crear usuarios PROVEEDOR desde el admin.
- IA real sin probar: instalar Ollama (`ollama pull qwen2.5vl:3b`) o poner `ANTHROPIC_API_KEY` y probar con una cotización real. Leer también las facturas con IA (idea 17-bis, no hecha).
- Orden: decidir si la evidencia de despacho es obligatoria y si se expone `GET /api/ordenes/{id}/versiones` (`findByConsecutivoOrderByVersionAsc` ya lo usan los pagos y las facturas).
- Pagos: si una orden con pagos se cancela o el proveedor la rechaza, el saldo a favor solo queda visible en el resumen; no hay devolución (fuera de alcance).
- Factura: una sola vigente por orden; considerar facturación por entregas si algún cliente la pide.
- Despacho: carga del cierre de caja desde Excel/CSV, si la ferretería usa otro programa de caja.
- Frontend Angular: `frontend/` sigue vacío.
- **Seguridad personal:** en la sesión del 2026-10-23 la contraseña local de MySQL apareció en pantalla; cámbiala si compartes la conversación o el repositorio.
- **Commit pendiente:** todo el trabajo desde el paso 16 está sin commitear (la rama actual es `feature/modulo1`; hay más de 100 archivos nuevos o modificados, incluidos los PDF de `docs/`).

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
- **Frontend Angular:** sin empezar (`frontend/` vacío). Se hará después de SaaS 3 y del paso 19. Hay una skill de diseño de interfaces (`design-taste-frontend`) disponible para esa etapa.
