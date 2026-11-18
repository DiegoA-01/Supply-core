# SaaS multiempresa — reglas, roles y plan (decisión del usuario, 2026-10-23)

Supply-Core pasa de "una empresa" a **SaaS**: el dueño del software (usuario de plataforma) vende el uso a varias empresas (ferretería, eléctricos...). Cada empresa tiene sus datos aislados, sus usuarios y solo los módulos que el dueño le activa.

## Reglas SaaS (no negociables)
1. **Aislamiento por empresa.** Toda tabla de negocio lleva `empresa_id`. El `empresa_id` sale del JWT, nunca del request ni de un parámetro. Una consulta sin filtro de empresa es un bug de seguridad. Cada módulo nuevo se prueba con "fuga entre empresas" (la empresa A pide un dato de la B ⇒ 404).
2. **Tres niveles de poder, sin mezclar.**
   - *Plataforma* (dueño, tabla `usuario_plataforma`, login aparte, MFA obligatorio): crea empresas, asigna plan y módulos, suspende, ve métricas de uso. **No ve datos de negocio** de una empresa; si hace soporte, queda auditado.
   - *Administrador de la empresa* (rol `ADMIN_SISTEMA`, ahora "Administrador de la empresa"): crea y desactiva usuarios de **su** empresa y les asigna roles. No puede salir de su empresa ni activar módulos.
   - *Usuarios de la empresa*: solo lo que su rol permite.
3. **Doble permiso.** Para entrar a un endpoint deben cumplirse las dos: el **módulo** está activo para la empresa (`@RequiereModulo`, lo decide el dueño) y el **rol** del usuario lo permite (`@RequiereRol`, lo decide el admin de la empresa). Módulo apagado ⇒ 403 `MODULO_NO_CONTRATADO`.
4. **Suscripción.** Estados PRUEBA → ACTIVA → MOROSA (solo lectura) → SUSPENDIDA (sin acceso) → CANCELADA. Suspender corta también las sesiones renovables (refresh).
5. **Consecutivos, archivos, códigos y correos únicos por empresa** (SOL, RFQ, OC, DES; carpeta de archivos `empresa/{id}/...`; código de producto; NIT).
6. **Proveedores son por empresa** (cada cliente tiene su propia lista y su propia aprobación). Un directorio global de proveedores queda como mejora futura.
7. **Cobro de la suscripción** con pasarela (Wompi, Mercado Pago o Stripe) en sandbox primero: webhooks firmados e idempotentes. La pasarela cobra **la suscripción**; el pago a proveedores sigue siendo solo registro (sin pasarela).
8. **Habeas data (Ley 1581).** Política de privacidad, consentimiento, retención y borrado/anonimización al cancelar la suscripción.

## Roles dentro de una empresa
Los roles son un **catálogo global** (plantillas en la tabla `rol`); cada admin de empresa los asigna a sus propios usuarios. Un usuario puede tener varios roles.

| Rol | Quién es en una ferretería | Qué hace |
|---|---|---|
| ADMIN_SISTEMA | Dueño o gerente de la empresa | Usuarios, roles, sedes, catálogos, configuración. No decide compras |
| ADMIN_COMPRAS | Jefe de compras | Políticas de aprobación, proveedores, categorías |
| SOLICITANTE | Cualquier empleado | Pide productos |
| COMPRADOR | Comprador | RFQ, análisis, adjudicación, orden |
| APROBADOR | Jefe de área | Aprueba solicitudes y adjudicaciones según límite |
| ALMACENISTA | **Bodeguero** | Recepción, movimientos de inventario, despachos |
| **CAJERO** (V16) | **Cajero** | Registra ventas y entregas en caja (despachos de salida). Sin acceso a compras ni pagos |
| ADMINISTRATIVO | Contabilidad / tesorería | Registra pagos y facturas |
| AUDITOR | Revisor o gerencia | Solo lectura y auditoría |
| PROVEEDOR | Proveedor externo | Portal: cotiza, confirma, despacha |

El dueño de la plataforma **no** es un rol de empresa: vive en `usuario_plataforma`.
Pendiente: dar a CAJERO acceso a los endpoints de despacho (hoy solo ALMACENISTA) y definir si un cajero puede anular.

## Modelo de datos nuevo (por crear)
`plan`, `modulo`, `plan_modulo` (módulos del plan), `empresa_modulo` (excepciones por empresa), `suscripcion` (empresa, plan, estado, fechas, límites de usuarios/almacenamiento/análisis IA), `usuario_plataforma`. Módulos: INVENTARIO, PROVEEDORES, COMPRAS (solicitudes + aprobación), RFQ_COTIZACIONES, IA, ORDENES_PAGOS, RECEPCION, DESPACHOS, REPORTES, AUDITORIA.

## Plan de trabajo
| Fase | Contenido | Estado |
|---|---|---|
| 0 | Decisiones: BD compartida con `empresa_id` (elegido), proveedor por empresa, planes y módulos | ✅ en esta página |
| 3-parcial | **Seguridad de sesión** (adelantada): JWT 15 min + refresh con rotación y detección de reuso, bloqueo de cuenta, rate limit por IP, `logout`/`logout-todo` | ✅ 2026-10-23 (V16) |
| 1 | Cimientos multiempresa: `empresa_id` en todas las tablas, filtro central por tenant, `empresa_id` en el JWT, reemplazar `EmpresaUnica`, consecutivos por empresa, índices/únicos compuestos, pruebas de fuga | ✅ 2026-10-23 (V17). Falta: archivos en carpeta por empresa |
| 2 | Consola de plataforma: `usuario_plataforma` + MFA, alta de empresa (crea admin inicial), planes/módulos, `@RequiereModulo`, suspender/reactivar, auditoría global | ✅ 2026-10-28 (V23) |
| 3 | Seguridad restante: MFA para admins, recuperación de contraseña con token de un solo uso, revocar sesiones al cambiar clave o desactivar usuario, dos usuarios MySQL (dueño de Flyway y app sin UPDATE/DELETE en tablas inmutables), cabeceras seguras, escaneo de dependencias | ⏳ |
| 4 | Negocio SaaS: suscripción y cobro en sandbox, estados de mora, límites por plan, métricas de uso | ⏳ |
| 5 | Operación: Docker Compose, CI/CD, entornos, logs con `empresa_id`, onboarding en minutos | ⏳ |

Orden recomendado: terminar 17 → 17-bis → 18, luego Fase 1 y 2, y recién ahí el paso 19 (cierre), para que las pruebas de punta a punta cubran multiempresa.

## Seguridad de sesión ya implementada (V16)
- Access token (JWT) de **15 min** (`JWT_EXPIRACION_MINUTOS`).
- Refresh token opaco de 256 bits, **7 días** (`REFRESH_DIAS`), de **un solo uso**: cada `POST /api/auth/refresh` entrega JWT y refresh nuevos. Solo se guarda su hash SHA-256 en `refresh_token`.
- Si un refresh ya usado o revocado se presenta otra vez (posible robo) se **revoca toda la sesión** (familia) y se audita `REFRESH_REUTILIZADO`.
- Al renovar se revalida usuario activo y proveedor habilitado: un usuario desactivado pierde acceso como máximo en 15 min.
- `POST /api/auth/logout` (una sesión) y `/api/auth/logout-todo` (todos los dispositivos), auditados como `LOGOUT`.
- **Bloqueo de cuenta**: 5 fallos seguidos (`LOGIN_MAX_INTENTOS`) bloquean 15 min (`LOGIN_BLOQUEO_MINUTOS`). Bloqueada responde el mismo "Credenciales inválidas" (no revela que el correo existe). Se audita `CUENTA_BLOQUEADA`.
- **Rate limit** por IP: 20 POST/min (`RATE_LIMITE_POR_MINUTO`) a `/api/auth/login`, `/api/auth/refresh` y `/api/portal/registro/**` ⇒ 429 `DEMASIADAS_PETICIONES`. En memoria: con varias instancias haría falta Redis; detrás de un proxy hay que configurar la IP real.
- Limpieza nocturna de refresh vencidos.

## Reglas de negocio para competir (decisión 2026-10-23)
Todas son **por empresa** (llevan `empresa_id`) y se activan como módulo del plan cuando aplica. Detalle de pasos en `plan-backend.md`.

| # | Regla | Cómo funciona | Dónde encaja |
|---|---|---|---|
| 1 | **Presupuesto por centro de costo** | `presupuesto_centro_costo` (centro, año, mes o año completo, monto). Al enviar una solicitud, Java suma lo ya comprometido (solicitudes aprobadas + órdenes emitidas, menos lo anulado) y compara con el presupuesto. Si el monto estimado lo excede ⇒ la solicitud no pasa a aprobación y responde 409 `PRESUPUESTO_EXCEDIDO` con lo disponible; una excepción la autoriza un rol con justificación y queda auditada. Sin presupuesto definido ⇒ no se controla (configurable por empresa) | Paso 18-a, ligado a solicitud (10) y orden (15) |
| 2 | **Contratos y precios acordados** | `contrato_proveedor` + `contrato_precio` (proveedor, producto, precio, vigencia desde/hasta, cantidad máxima). Al crear una RFQ de un producto con contrato vigente, el sistema ofrece **orden directa al precio pactado** o RFQ con el precio de referencia. Java valida vigencia y tope; la persona decide. Un precio vencido nunca se aplica solo | Paso 18-b, ligado a RFQ (11) y orden (15) |
| 3 | **Delegación y escalamiento (SLA)** | `delegacion_aprobacion` (de usuario, a usuario, desde, hasta, motivo): mientras esté vigente el delegado puede decidir lo del titular y queda registrado "decidió X por Y". **SLA**: cada política define horas máximas; un job (como `RfqCierreJob`) avisa al aprobador al 80 %, y al vencer escala al rol superior definido. La segregación de funciones sigue valiendo: nadie aprueba lo suyo, ni delegado | Paso 18-c, ligado a políticas (4) y aprobaciones (10, 14) |
| 4 | **Factura y cruce de tres vías** ✅ (2026-10-23) | Hecho (17-bis, V19). Se agrega: la tolerancia es **configurable por empresa** (`empresa.tolerancia_factura_pct`, por defecto 1 %) y puede ser por monto fijo o porcentaje. Si la factura no cuadra con orden y recepción ⇒ queda EN_REVISION y no se puede pagar el saldo final | Paso 17-bis |
| 5 | **Calidad en la recepción** | Cada línea rechazada exige **motivo** (catálogo: dañado, no cumple especificación, vencido, cantidad errada, otro) y foto opcional. Lo rechazado genera `registrarDevolucionProveedor` y una incidencia. El score del proveedor (UC-14) toma los rechazos como indicador de calidad. El proveedor ve el motivo en su portal | Paso 17 |
| 6 | **Reorden automática** | Cuando el stock total cruza el mínimo (ya existe el aviso), el sistema **sugiere** una solicitud con cantidad = máximo − stock − pendiente por recibir. Solo sugiere: la persona la revisa y la envía. No crea compras sola (regla 1 de oro) | Paso 18-d, ligado a inventario (6) y notificaciones (8) |
| 7 | **Factura electrónica DIAN** | **Fase futura** (después del backend actual). Aplica a lo que la empresa cliente **emite**; hay que integrarse con un proveedor tecnológico autorizado (no se firma directo ante la DIAN). Dejar hoy los campos listos (NIT, régimen, resolución de numeración) para no migrar después | Fase 6 SaaS |
| 8 | **Moneda y tasa de cambio** | **Fase futura**. Hoy todo es COP. Cambia el modelo: `moneda` y `tasa` guardadas en cotización, orden y pago; la comparación de ofertas se normaliza a la moneda base de la empresa con la tasa del día, congelada en el análisis (como los pesos). La tasa la ingresa una persona o un servicio, nunca la IA | Fase 7 SaaS |

Orden recomendado: 17 (con calidad) → 17-bis (tolerancia configurable) → 18 reportes → 18-a presupuesto → 18-b contratos → 18-c delegación y SLA → 18-d reorden → fases 1 y 2 de SaaS → 19 cierre. DIAN y multimoneda después del cierre.

## Referencias de mercado (2026-10-23)
Fuentes: páginas comerciales y reseñas; los precios y funciones pueden haber cambiado. No se verificó la API de Alegra ni Siigo.
- **Tin Pos** (POS para ferreterías, $39.900/mes; $69.900 con factura electrónica; plan gratis de 25 productos): vende la venta; en compras solo registra entradas. Complemento, no competidor directo.
- **Alegra** (contabilidad y POS): compras = factura de proveedor posterior; recalcula costo promedio al recibir.
- **Precoro** (compras para pymes): presupuesto en vivo por centro de costo, bloqueo de solicitudes duplicadas, agrupar solicitudes en una orden, cruce de tres vías con OCR.
- **Odoo Compras**: precios por proveedor con escalas, órdenes abiertas (contratos), reabastecimiento que elige proveedor, indicadores de proveedor.
- **Tradogram / Cotiss**: RFQ simple para equipos pequeños.

Qué se adopta sin perder el foco (compras antes de la factura):
1. La recepción actualiza el costo de referencia (último o promedio): mejora monto estimado, presupuesto y aprobaciones (paso 17 → 18-a).
2. Carga y actualización masiva por Excel de productos, categorías y precios (antes de 18-d).
3. Estado de cuenta por proveedor (comprado, pagado, saldo; solo lectura) (paso 18).
4. Bloqueo de solicitudes duplicadas y agrupar solicitudes aprobadas en una sola orden (18-a).
5. Precios por proveedor con escalas por cantidad, y reorden que sugiere proveedor sin decidir por la persona (18-b y 18-d).
6. Lectura de facturas con la misma IA de las cotizaciones (17-bis).
7. Exportar la compra validada a la contabilidad (CSV primero, API después) (fase 4 SaaS).
8. Imágenes de producto; recepción en celular sin conexión (PWA) como fase posterior.
No se construye: POS, caja, contabilidad, comisiones, factura de venta.

## Aislamiento por empresa ya implementado (fase 1, V17)
- **Filtro central:** `@TenantId` (Hibernate) en `Usuario`, `Sede`, `CentroCosto`, `PoliticaAprobacion`, `ConfigPesos`, `Bodega`, `CategoriaProducto`, `Producto`, `StockProductoBodega`, `MovimientoInventario`, `Proveedor`, `EvaluacionProveedor`, `SolicitudCompra`, `Rfq`, `Cotizacion`, `Analisis`, `Adjudicacion`, `OrdenCompra`, `Pago`, `Despacho`, `Documento`, `ConfigNotificacion` y `Notificacion`. Hibernate agrega `empresa_id = ?` a toda consulta, carga por id y escritura, y asigna la empresa al guardar. Nadie escribe ni filtra `empresaId` a mano.
- **Contexto:** `EmpresaContexto` (hilo actual). El `AuthFilter` fija la empresa del claim `emp` del JWT y la limpia al terminar. `EmpresaResolver` se la entrega a Hibernate; si el hilo no tiene empresa, abrir una sesión falla (nunca ve datos de todas). Modo raíz (`sinEmpresa`) solo para flujos públicos o globales: resolver la empresa por código, el refresh de sesión y listar empresas en un job.
- **Hijas:** líneas, historiales y detalles no repiten `empresa_id`; se llega a ellas por su padre, que sí está filtrado. `unidad_medida`, `criterio_evaluacion`, `rol` y `permiso` son globales.
- **Login:** `POST /api/auth/login {empresa, correo, password}`. El correo es único por empresa. Una empresa inexistente responde igual que una clave mala. El JWT lleva `emp`; un token sin ese claim ya no sirve.
- **Registro público de proveedores:** `/api/portal/registro/{codigoEmpresa}` (y `/categorias`). El `AuthFilter` resuelve el código y fija la empresa antes de validar el cuerpo, así NIT, correo y categorías se validan dentro de esa empresa. Código desconocido: 404. La empresa tiene `codigo_publico` único (la sembrada es `demo`).
- **Consecutivos** (SOL, RFQ, OC, DES) por empresa: la fila se crea sola la primera vez. **SQL nativo** (stock) y JDBC llevan `empresa_id` explícito.
- **Jobs y async:** `RfqCierreJob` recorre cada empresa con su contexto; las tareas `@Async` heredan la empresa del hilo que las lanzó (`EmpresaTaskDecorator`); el job de limpieza de refresh trabaja sin empresa.
- **Únicos por empresa:** código de producto, NIT de proveedor, nombre de categoría, código de centro de costo, correo de usuario, consecutivos, versión de orden, evento/canal de notificación.
- **Auditoría:** `auditoria.empresa_id` opcional (un login con empresa desconocida no tiene empresa); la consulta UC-16 siempre filtra por la empresa del usuario.
- **Pruebas de fuga:** `AislamientoEmpresasTest` (18 pruebas, MySQL real, base `supply_core_test`): dos empresas con los mismos códigos, NIT y correos; repositorios y API completa, incluida la recepción de extremo a extremo (el orden SQL de la prueba siembra órdenes con las llaves foráneas apagadas solo durante el INSERT).
## Consola de plataforma ya implementada (fase 2, V23)
- **Dueño:** `usuario_plataforma` (sin `empresa_id`). Se crea al arrancar con `PLATAFORMA_ADMIN_CORREO` y `PLATAFORMA_ADMIN_PASSWORD` (12 a 72 caracteres) si todavía no hay ninguno; la contraseña no va en el código. No hay endpoint para crear más dueños (queda para después).
- **Login en dos pasos** (`/api/plataforma/auth/login` y `/mfa`, rutas públicas con rate limit por IP): contraseña, desafío de 5 min y código TOTP de 6 dígitos. La primera vez el login entrega el secreto y la URI `otpauth://` para vincular la app; el primer código válido confirma el MFA. Un código no se acepta dos veces (`mfa_ultimo_paso`). Correo inexistente, clave mala, cuenta bloqueada o inactiva responden igual. 5 fallos (contraseña o código) bloquean 15 min, como en las empresas.
- **Tokens separados:** el JWT de plataforma lleva `plat=true` y no lleva `emp`; el de empresa exige `emp`. Ninguno sirve en la API del otro, y el desafío de MFA no sirve en ninguna. El `AuthFilter` fija el contexto sin empresa en `/api/plataforma/**`: esos servicios solo tocan empresa, suscripción, plan, módulo y auditoría de plataforma, nunca tablas de negocio; la única escritura dentro de una empresa es su administrador inicial y su matriz de pesos, hechas con `EmpresaContexto.como`.
- **Módulos:** `Modulos` (constantes) y tabla `modulo`. `@RequiereModulo(Modulos.X)` en 30 controllers; el método reemplaza a la clase (el resumen con IA de `/api/rfqs/{id}/analisis/explicar` es IA aunque el análisis sea RFQ_COTIZACIONES). Sin módulo: categorías, unidades, usuarios, roles, perfil, empresa, sedes, centros de costo, políticas, notificaciones, documentos y autenticación (son la base de toda empresa).
- **Planes sembrados:** BASICO (inventario, proveedores, compras, cotizaciones, órdenes y pagos, recepción), PROFESIONAL (+ despachos, reportes, auditoría) y EMPRESARIAL (+ IA). Las empresas anteriores quedaron en EMPRESARIAL y ACTIVA. La empresa efectiva = módulos del plan, más las excepciones con `habilitado=true`, menos las de `habilitado=false`.
- **Suscripción:** `PRUEBA`/`ACTIVA` leen y escriben; `MOROSA` solo GET (403 `SUSCRIPCION_MOROSA`); `SUSPENDIDA`/`CANCELADA` no entran (403 `SUSCRIPCION_SUSPENDIDA`, también en login y refresh, y solo a quien acertó la clave). Suspender o cancelar revoca los refresh tokens de la empresa (SQL nativo, porque la plataforma no tiene empresa en el contexto). Transiciones en `EstadoSuscripcion.puedePasarA`; MOROSA, SUSPENDIDA y CANCELADA piden motivo; CANCELADA es final; no se vuelve a PRUEBA.
- **`AccesoEmpresaService`:** el interceptor lo consulta en cada petición; recuerda 30 s por empresa (`app.plataforma.cache-segundos`, 0 en las pruebas). Un cambio de la plataforma limpia la memoria de esa instancia al instante; con varias instancias llega al vencer el tiempo. Los endpoints de `/api/auth/**` no pasan por la revisión de suscripción para que una empresa suspendida pueda cerrar su sesión.
- **API:** `GET/POST /api/plataforma/empresas`, `GET /{id}`, `PUT /{id}/plan`, `PUT /{id}/modulos/{modulo}` (`habilitado` true/false o ausente para quitar la excepción), `PATCH /{id}/estado`; `GET /api/plataforma/modulos`, `GET/POST /api/plataforma/planes`, `PUT /planes/{id}`; `GET /api/plataforma/auditoria?empresaId=`. Para la empresa: `GET /api/empresa/modulos`.
- **Alta de empresa:** valida plan activo, NIT y código únicos; crea empresa y suscripción en una transacción y, ya dentro de la empresa nueva, su administrador (`ADMIN_SISTEMA`) y la matriz de pesos inicial (35/20/15/10/15/5) en otra; si el segundo paso falla, se deshace el primero. El administrador entra con el código público y la contraseña que se le dio.
- **Auditoría global:** `auditoria_plataforma` (login, MFA, alta, plan, módulo, estado, planes), propia y separada de la de cada empresa; se guarda en su propia transacción para que un intento fallido quede aunque se revierta lo demás.
- **Fase 3 hecha (2026-11-10, V24):** versión de sesión en el JWT (`sv`) que sube al cambiar la clave o desactivar y corta sesiones al instante; recuperación de contraseña con token de un solo uso; MFA TOTP para administradores de empresa (obligatorio para ADMIN_SISTEMA, voluntario para los demás; reinicio por otro administrador); cabeceras seguras; Dependabot; `backend/sql/permisos.sql` (dos usuarios MySQL, sin aplicar todavía en un ambiente real).
- **Pendiente de esta línea (fase 4):** más de un dueño y su administración, métricas de uso y límites por plan, cobro de la suscripción, PRUEBA que vence sola, ruta de la consola para reiniciar el MFA de un administrador único.

- **Pendiente de esta línea:** carpeta de archivos por empresa en `LocalStorageClient`; consola de plataforma para crear empresas (fase 2); un módulo nuevo debe agregar `@TenantId` y su prueba de fuga.
