# Detalle de hallazgos confirmados — Supply-Core

Cada hallazgo fue validado por un verificador independiente del cazador que lo encontró. "Fuente" = derivado de código; "En vivo" = reproducido contra el stack demo local. Las referencias `archivo:línea` son del commit `2f0ba20`.

---

## #1 — ALTA · JWT de empresa llega a la consola de plataforma por ruta codificada

**Fingerprint:** `security/AuthFilter+RolInterceptor:raw-requestURI-platform-prefix-classification`

**Qué pasa.** `AuthFilter` y `RolInterceptor` deciden si una petición va a la consola de plataforma (`/api/plataforma/**`) comparando la URI **cruda** contra el prefijo literal. Spring, en cambio, mapea el handler sobre la ruta decodificada y sin parámetros matrix. Para `GET /api/%70lataforma/empresas` o `GET /api/plataforma;x/empresas`, los filtros la ven como una ruta de empresa normal: la autentican con **cualquier** JWT de empresa (cualquier rol, p.ej. CAJERO) y fijan el contexto de empresa; pero el despacho va al controlador de plataforma. Esos controladores **no llevan** `@RequiereRol`/`@UsuarioInterno` (confían en que el filtro solo deja pasar un token de plataforma), así que pasan con la mera autenticación.

**Resultado.** Un usuario de una empresa lee los datos de **todas**: lista de empresas (id, nit, razón social, estado, plan), el administrador de cada una (nombre, correo) y todos los planes.

**Traza.**
- Entrada: `AuthFilter.java:111` — clasificación de plataforma con `ruta.startsWith(PREFIJO_PLATAFORMA)` sobre URI cruda.
- Propagación: `RolInterceptor.java:59` — mismo test crudo; no halla anotación de rol en el controlador de plataforma y devuelve `true`.
- Sink: `EmpresaPlataformaService.java:102` — `findAll()` sin filtro `empresa_id`.

**Evidencia en vivo.** Contra el stack demo: `GET /api/plataforma/empresas` → **401**; `GET /api/%70lataforma/empresas` y `GET /api/plataforma;x/empresas` → **200** con la lista completa de empresas; `GET /api/%70lataforma/planes` → **200** con todos los planes. Artefacto: `agents/v-local/artifacts/plataforma-ruta-codificada.txt`.

**Severidad.** Probabilidad ALTA (un cambio de una línea en la URL, sin condiciones), impacto ALTO (lectura cross-tenant total + alcance a la consola del dueño). Confianza ALTA (demostrado).

**Remediación.** Clasificar sobre la ruta canonizada en `AuthFilter`/`RolInterceptor` (`UrlPathHelper.getPathWithinApplication`, que decodifica `%NN` y quita `;matrix`), **y** añadir una comprobación positiva de principal de plataforma en los controladores `/api/plataforma/**`. Además volver `@Transactional` el `EmpresaPlataformaService.crear` (hoy persiste empresa + ADMIN_SISTEMA antes de que la auditoría falle para un token de empresa, dejando un tenant a medias).

---

## #2 — ALTA · Admin de una empresa muta el catálogo global `unidad_medida`

**Fingerprint:** `supply-core/catalogo/unidad-medida-global-write-company-role`

**Qué pasa.** `unidad_medida` es una tabla **global** (la entidad no tiene `@TenantId` y su `code` es único global; es un catálogo compartido deliberadamente). Pero sus endpoints de crear/actualizar/activar/desactivar están protegidos solo por roles **de empresa** (`ADMIN_COMPRAS`, `ADMIN_SISTEMA`), que pertenecen a cada empresa. El servicio escribe por `findById`/`save` sin filtro `empresa_id` ni chequeo de uso.

**Resultado.** El admin de la empresa A desactiva o renombra una fila que comparten todas. Desactivar una unidad base (UND, KG...) se propaga a todas: bloquea fijar precios (`PreciosService.unidadDelPrecio` lanza `UNIDAD_INACTIVA`) y degrada creación de productos y conversiones en toda la plataforma.

**Traza.** `UnidadMedidaController.java:133` (desactivar, `@RequiereRol` de empresa) → `UnidadMedidaService.cambiarEstado:120` (sin filtro de tenant) → entidad sin `@TenantId` (`UnidadMedida.java:30`). Contraste: la hermana `CategoriaProducto.java:31` sí lleva `@TenantId`.

**Severidad.** Probabilidad MEDIA (basta una sesión de admin, que existe en cada empresa), impacto ALTO (integridad/disponibilidad cross-tenant). Confianza ALTA.

**Remediación.** Tratar `unidad_medida` como propiedad de la plataforma: mover sus escrituras a `/api/plataforma/**` (usuario_plataforma) y dejar a las empresas solo lectura/conversión; **o** volverla por empresa añadiendo `@TenantId empresa_id`. Añadir guardia de "en uso" antes de desactivar.

---

## #3 — MEDIA · Costos de producto expuestos a cualquier rol interno

**Fingerprint:** `supply-core/inventario/producto-get-cost-exposure-internal-roles`

**Qué pasa.** Los GET de `ProductoController` (`buscar`, `obtener`, `alertas-stock`) están protegidos solo por `@UsuarioInterno` + `@RequiereModulo(INVENTARIO)`, **sin** `@RequiereRol`, y `ProductoResponse` serializa `costoReferencia` y `costoPromedio`. Cualquier usuario interno de una empresa con el módulo INVENTARIO activo —incluidos CAJERO y VENDEDOR— lee los costos. Esto contradice la restricción deliberada de `PreciosController` (costos solo a `{ADMIN_SISTEMA, ADMIN_COMPRAS, AUDITOR}`) y su búsqueda POS, que omite costos a propósito.

**Traza.** `ProductoController.java:60` (GET sin rol) → `ProductoResponse.of:40` → campos `costoReferencia`/`costoPromedio:43`. Contraste intencional: `PreciosController.java:55` y `:208` (`buscarParaVender` sin costos).

**Severidad.** Probabilidad MEDIA (cualquier rol interno, tras auth y módulo activo), impacto MEDIO (fuga de costo/margen a roles a los que la app se lo niega). Confianza ALTA.

**Remediación.** Exponer los costos solo a los roles autorizados: o gatear los GET con `@RequiereRol({ADMIN_SISTEMA, ADMIN_COMPRAS, AUDITOR})`, o —preferible— partir el DTO para devolver una versión sin costos a los demás roles internos (que así conservan la vista de inventario sin costo), calculando la visibilidad en `ProductoService` desde los roles de la sesión.

---

## #4 — MEDIA · Autorización de supervisor no atada al monto ni al payload

**Fingerprint:** `caja/AutorizacionService/consumir:approval-not-bound-to-amount-or-payload`

**Qué pasa.** El token de un solo uso del PIN de supervisor se ata solo a (supervisor, solicitante, tipo de acción, turno, referencia opcional). **No lleva monto ni huella del payload.** Para movimientos de caja (RETIRO/GASTO/INGRESO) y acciones de venta (DESCUENTO, PRECIO_BAJO_MINIMO) se consume con `referenciaId=null`, así que un cajero que logra que un supervisor apruebe un GASTO pequeño reutiliza el mismo token para un GASTO mucho mayor, hasta el efectivo del cajón, atribuido al supervisor. Eso baja el efectivo esperado y permite sustraer caja cuadrando el cierre ciego. Un token de DESCUENTO cubre cualquier carrito hasta el límite del autorizador; uno de DEVOLUCION (atado solo al id de la venta) cubre cualquier línea y forma de reembolso de esa venta.

**Traza.** `TurnoService.registrarMovimiento:165` → `consumir(token, accion, turnoId, null):179` → `AutorizacionService.consumir:160` (valida acción/turno/solicitante/referencia; ningún monto). La entidad `AutorizacionSupervisor.java:54` no tiene columna de monto. El único freno de salida es `saldo(EFECTIVO) < monto` (`TurnoService.java:176`).

**Severidad.** Probabilidad MEDIA (requiere una aprobación del mismo tipo en ventana de 2 min; GASTO/RETIRO/descuento pequeños son rutina), impacto MEDIO (robo de caja acotado por movimiento al efectivo del cajón; todo queda auditado). Confianza ALTA.

**Remediación.** Atar cada autorización a su operación: guardar el `monto` (y, para ventas, una huella del carrito/devolución) en `autorizacion_supervisor` al otorgar, y en `consumir` exigir que el monto/payload de la operación coincida. Capturar el monto en `AutorizacionRequest` para que el supervisor apruebe una cifra concreta.

---

## #5 — MEDIA · Nueva versión de orden sube cantidades sin reaprobación

**Fingerprint:** `supply-core:compra/orden/OrdenCompraService.nuevaVersion:quantity-increase-bypasses-award-approval`

**Qué pasa.** Tras aprobar (o autoaprobar bajo el umbral) una adjudicación, un COMPRADOR puede `POST /api/ordenes/{id}/version` y subir sin tope la cantidad de cualquier línea que no sea de contrato, **sin** reaprobación. `nuevaVersion` solo bloquea aumentos en líneas de contrato y solo rechaza un total nuevo por debajo de lo ya pagado; nunca compara el total nuevo contra el monto adjudicado aprobado ni reaplica `PoliticaAprobacionService`. La hermana `OrdenDirectaService.emitir` sí rechaza con `ORDEN_SUPERA_APROBADO`.

**Resultado.** Un solo COMPRADOR compromete a la empresa por encima de lo aprobado sin segundo aprobador, y como los topes de anticipo/pago derivan del total de la orden, el anticipo autorizado crece con él.

**Traza.** `OrdenController.nuevaVersion:90` → `OrdenCompraService.nuevaVersion:213` (cantidad verbatim; solo líneas de contrato topadas) → `:237` (único chequeo: total ≥ pagado). Contraste: `OrdenDirectaService.java:141` (`ORDEN_SUPERA_APROBADO`). Tope de anticipo que escala: `PagoService.java:327`.

**Severidad.** Probabilidad MEDIA (cualquier COMPRADOR; precondiciones normales de una orden emitida), impacto MEDIO (rompe segregación de funciones de la adjudicación; acotado porque el proveedor debe aceptar la nueva versión y el pago requiere otro rol). Confianza ALTA.

**Remediación.** En `nuevaVersion`, tras totalizar, comparar el total nuevo contra el monto aprobado y rechazar si lo supera (como `OrdenDirectaService`), o reenrutar la versión por `PoliticaAprobacionService` antes de pasar a EMITIDA. Prueba de regresión: un aumento sobre línea adjudicada devuelve 409; una rebaja sigue funcionando.

---

## #6 — MEDIA · Reintento de extracción IA filtra precios de licitación sellada

**Fingerprint:** `ia/ExtraccionService.reintentar:sealed-bid-gate-missing`

**Qué pasa.** `POST /api/cotizaciones/{id}/extraccion/reintentar` devuelve el `ProcesamientoResponse` completo —precios unitarios, cantidades, factor, total impreso— **sin** la comprobación de sobre sellado (`exigirSobreAbierto`) que sí aplican `obtener`, `confirmar` y `digitar`. Un COMPRADOR o ADMIN_COMPRAS lee los precios de la competencia antes de cerrar la RFQ cuando la lectura está en `REQUIERE_REVISION` o `ERROR` (rutinario por baja confianza o descuadre de total).

**Traza.** `ExtraccionController.reintentar:90` → `ExtraccionService.reintentar:368` (solo chequea IA disponible, estado y modificable) → `respuesta(p):380` (expone `valorExtraido`). Contraste: `obtener` llama `exigirSobreAbierto` (`:282`), que lanza `SOBRE_CERRADO` (`:500`).

**Severidad.** Probabilidad MEDIA (rol interno de comprador; estado REQUIERE_REVISION rutinario), impacto MEDIO (rompe confidencialidad de licitación sellada; posible manipulación de ofertas dentro de la empresa). Confianza ALTA.

**Remediación.** Llamar `exigirSobreAbierto(c)` al inicio de `reintentar`, como las hermanas. Prueba: `SOBRE_CERRADO` con RFQ ABIERTA.

---

## #7 — BAJA · Límite de tasa del PIN de supervisor evitable por ruta codificada

**Fingerprint:** `security/AuthFilter+RateLimitFilter:raw-uri-ratelimit-bypass`

**Qué pasa.** `RateLimitFilter.shouldNotFilter` decide aplicar el límite 20/min por IP comparando la URI cruda contra una lista con el literal `/api/autorizaciones`. Como Spring enruta sobre la ruta decodificada, un `POST /api/%61utorizaciones` o `/api/autorizaciones;x` no coincide (se salta el límite) pero sí llega a `AutorizacionController.otorgar`. Autenticación y rol siguen intactos: el atacante debe ser un interno autenticado (CAJERO/SUPERVISOR/ADMIN). El efecto es saltarse el throttle que frena la adivinación del PIN. **Fuertemente acotado**: `AutorizacionService` bloquea el PIN del supervisor 15 min tras 5 intentos fallidos. La variante de login se auto-neutraliza: `AuthFilter.esPublica` usa el mismo match crudo, así que un `/api/auth/login` codificado no se trata como público y da 401.

**Traza.** `RateLimitFilter.java:68` (URI cruda) → `AutorizacionController.otorgar:52`. Bloqueo que acota: `AutorizacionService.java:112`.

**Severidad.** Probabilidad MEDIA (trivial para un interno autenticado), impacto BAJO (solo quita el throttle; el PIN sigue con bloqueo 5/15 min). Confianza MEDIA (misma causa de enrutado que #1, demostrada; esta ruta exacta no se ejercitó aparte).

**Remediación.** Misma corrección que #1: `shouldNotFilter` sobre la ruta canonizada (`UrlPathHelper.getPathWithinApplication`).

---

## #8 — BAJA · Cambio de rol no revoca sesiones

**Fingerprint:** `usuario/UsuarioService.actualizar:role-change-without-session-revocation`

**Qué pasa.** `UsuarioService.actualizar` reemplaza los roles (y `proveedorId`) in situ pero **nunca** llama `sesiones.revocarSesiones`, a diferencia de `desactivar`, `restablecerPassword` y `cambiarPasswordPropia`. Como `revocarSesiones` sube la versión de sesión (`sv`) y revoca los refresh, un cambio de rol deja el access token ya emitido plenamente válido (`AuthFilter.exigirVigente` sigue pasando). Un ADMIN_SISTEMA degradado conserva ADMIN_SISTEMA en su JWT hasta que expire (≤15 min) y, en esa ventana, puede seguir operando como admin, incluido reautoasignarse ADMIN_SISTEMA.

**Traza.** `UsuarioController.actualizar:96` → `UsuarioService.actualizar:171` (sin bump de `sv`) → `AuthFilter.exigirVigente:130` (acepta el token viejo). Hermanas que sí revocan: `UsuarioService.desactivar:224`, `SesionUsuarioService.revocarSesiones:73`. Acota: `AuthService.refrescar:191` relee roles.

**Severidad.** Probabilidad BAJA (interno, ventana temporal), impacto MEDIO (un admin degradado sigue operando y reescala). Confianza ALTA.

**Remediación.** Llamar `sesiones.revocarSesiones(u)` en `actualizar` cuando cambie el conjunto de roles o el `proveedorId`. Prueba: tras degradar, el JWT viejo da 401.

---

## #9 — BAJA · Refresh no reevalúa el MFA obligatorio de admin

**Fingerprint:** `supply-core:auth/refresh-skips-mandatory-admin-mfa-after-role-grant`

**Qué pasa.** `AuthService.refrescar` rota el refresh y relee los roles de la BD, pero **nunca** consulta `mfa.requiere(usuario)` como sí hace el login. Con `mfa-admin-obligatorio=true`, el login fuerza TOTP a un ADMIN_SISTEMA; el refresh no. Si un usuario que se autenticó sin MFA (su rol previo no lo exigía) es luego promovido a ADMIN_SISTEMA, su familia de refresh viva emite tokens de admin **sin** segundo factor. Se solapa con #8 pero es distinto: el control ausente vive en `refrescar` y su remediación es independiente.

**Traza.** `AuthService.refrescar:189` (solo `exigirPuedeEntrar`) → `emitirSesion:202` (token con ADMIN_SISTEMA sin TOTP). Contraste: login llama `mfa.requiere` (`AuthService.java:117`); política en `MfaUsuarioService.java:76`.

**Severidad.** Probabilidad BAJA (requiere MFA-admin activo + promoción de quien nunca hizo TOTP + familia de refresh viva), impacto MEDIO (anula el segundo factor del rol más privilegiado). Confianza MEDIA (ausencia explícita en código; precondición ligada a #8).

**Remediación.** En `refrescar`, tras `exigirPuedeEntrar`: si `mfa.requiere(usuario)` y el usuario no tiene MFA activo, cerrar sus sesiones y rechazar (forzar login nuevo con segundo factor). Combinar con #8.

---

## #10 — BAJA · Notificación de revisión filtra los totales de una cotización

**Fingerprint:** `ia/ExtraccionService.guardarResultado:sealed-bid-total-in-notification`

**Qué pasa.** Cuando la lectura IA termina con descuadre del total impreso, `guardarResultado` hace `p.setError(cruce.mensaje())` y llama `avisarRevision(c, p.getError())`; ese mensaje contiene el total impreso y el total calculado de la cotización del proveedor. La extracción corre apenas el proveedor envía sobre una RFQ ABIERTA, así que el comprador recibe esos totales antes de que acabe el periodo sellado, sin ninguna acción.

**Traza.** `ExtraccionService.guardarResultado:247` → `CruceTotal.Resultado.mensaje:43` (compone con impreso y calculado) → `avisarRevision:531` (notifica al dueño de la RFQ, sin chequear estado). `programar:131` corre al enviar, así que puede completarse con la RFQ ABIERTA.

**Severidad.** Probabilidad MEDIA (automático para cualquier cotización por archivo con descuadre mientras la IA procesa), impacto BAJO (solo dos totales de una cotización a un comprador interno antes del cierre; sin precios de línea). Confianza ALTA.

**Remediación.** Enviar una notificación **sin cifras** mientras la RFQ esté ABIERTA (dejar el error detallado en el procesamiento, legible tras el cierre).

---

## Endurecimiento observado (sin hallazgo, para tener en cuenta)

- `AdjudicacionController.decidir`/`pendientes` carecen de `@RequiereRol` a nivel de método y confían solo en el servicio; añadir la anotación por defensa en profundidad.
- `PedidoInternoController.crear` sin `@RequiereRol`: un AUDITOR de solo lectura (y CAJERO/VENDEDOR) puede crear una requisición no vinculante; estrecharlo si AUDITOR debe quedar solo lectura.
- Un solo ALMACENISTA puede registrar `AJUSTE_POSITIVO` para inventar stock con solo un registro de auditoría detectivo; considerar umbral de aprobación para ajustes grandes.
- `CabecerasSeguridadFilter` gatea CSP/no-store sobre el prefijo `/api/` crudo; un prefijo `%61pi` las saltaría (acoplado a `AuthFilter`, mitigado al canonizar una vez).
- Javadoc obsoleto en `CategoriaProducto` (dice solo lectura, tiene CRUD completo); sin impacto de seguridad.
