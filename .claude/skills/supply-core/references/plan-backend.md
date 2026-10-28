# Plan de trabajo del backend (por módulos)

Paquete base: `com.proyecto.supply_core`. Cada módulo = carpeta con `entity/ repository/ dto/ service/ controller/` (+ `mapper/` y `enums/` si aplica).
Orden obligatorio: cada paso depende del anterior. Marcar ✅ aquí y en `estado.md` al terminar.
"Listo cuando" = criterio de aceptación verificable (Postman/Swagger o prueba).

---

## Paso 0 — Base técnica `common/` + `config/` · ✅
Nota: consecutivos con tabla `consecutivo` (V3) + `UPDATE … LAST_INSERT_ID()` atómico. Excepción extra: `NoAutenticadoException` (401).
```
common/
├── exception/   NegocioException(409), NoEncontradoException(404), AccesoDenegadoException(403),
│                GlobalExceptionHandler (@RestControllerAdvice)
├── dto/         ErrorResponse {codigo, mensaje, detalles[], timestamp}, PageResponse<T>
└── util/        ConsecutivoGenerator (SOL-000001, RFQ-…, OC-…)
config/          WebConfig (CORS para Angular, registro de interceptores), OpenApiConfig
```
- pom: agregar `spring-security-crypto`, `springdoc-openapi-starter-webmvc-ui`.
- Listo cuando: la app arranca, Flyway V1/V2 en `success=1`, `/swagger-ui.html` abre, un error devuelve `ErrorResponse`.

## Paso 1 — `security/` (JWT manual) · UC-01 · ✅
Hecho además: `security/context/Roles` (constantes), `security/config/SecurityBeansConfig` (BCrypt 12), `usuario/` entity+repository+DataInitializer. Errores del filtro se delegan al GlobalExceptionHandler.
```
security/
├── jwt/         JwtUtil (crear/validar HS256), JwtProperties (secret, expiración)
├── filter/      AuthFilter (OncePerRequestFilter) → UsuarioAutenticado en request
├── annotation/  @RequiereRol
├── interceptor/ RolInterceptor (403 si no tiene rol)
├── context/     UsuarioAutenticado (id, correo, roles, proveedorId), SesionActual (helper)
├── controller/  AuthController  POST /api/auth/login, GET /api/auth/me
├── service/     AuthService (BCrypt, mensajes genéricos, ultimo_acceso)
└── dto/         LoginRequest, LoginResponse
```
- Depende de `usuario/` (entity + repository) → crear ambos juntos.
- Listo cuando: login correcto da token; mal password/inactivo dan 401 con mensaje genérico; ruta protegida sin token = 401, sin rol = 403.

## Paso 2 — `usuario/` · ✅
Hecho además: `PerfilController` `/api/perfil` (GET, PUT /password) para cualquier autenticado; `RestablecerPasswordRequest`, `RolResponse`; listado con filtros texto/activo/rol paginado.
```
usuario/
├── entity/      Usuario, Rol, Permiso (ManyToMany usuario_rol, rol_permiso)
├── repository/  UsuarioRepository (findByCorreo), RolRepository, PermisoRepository
├── dto/         UsuarioRequest, UsuarioUpdateRequest, UsuarioResponse, CambioPasswordRequest
├── service/     UsuarioService
├── controller/  UsuarioController /api/usuarios (ADMIN_SISTEMA), RolController /api/roles (lectura)
└── config/      DataInitializer (admin desde ADMIN_EMAIL/ADMIN_PASSWORD)
```
- Reglas: correo único; nunca devolver hash; desactivar en vez de borrar; asignar roles; `centro_costo_id` y `proveedor_id` opcionales.
- Listo cuando: CRUD de usuarios funciona y el admin inicial inicia sesión.

## Paso 3 — `auditoria/` (temprano: todos lo usan) · ✅
Uso en cada módulo nuevo: agregar la acción a `AccionAuditoria` y la entidad a `EntidadAuditada`; llamar `auditoriaService.exito(...)` dentro de la transacción tras el cambio, o `fallo(...)` antes de lanzar la excepción si el intento debe quedar registrado. Nunca auditar contraseñas.
```
auditoria/
├── entity/ Auditoria · repository/ AuditoriaRepository (filtros)
├── service/ AuditoriaService.registrar(entidad, id, accion, resultado, detalle)
├── dto/ AuditoriaResponse, AuditoriaFiltro
└── controller/ AuditoriaController GET /api/auditoria (AUDITOR) · UC-16
```

Uso en cada módulo nuevo: agregar la acción a AccionAuditoria y la entidad a EntidadAuditada;
llamar auditoriaService.exito(...) dentro de la transacción tras el cambio, o fallo(...) antes de
lanzar la excepción si el intento debe quedar registrado. Nunca auditar contraseñas.
- Listo cuando: el login (éxito/fallo) queda auditado y se consulta por fecha/usuario/acción.

## Paso 4 — `empresa/` · ✅
Hecho: Empresa (única, solo GET/PUT), Sede, CentroCosto, PoliticaAprobacion. `config_notificacion` pasa al paso 8. Para usar en pasos 10 y 14: `PoliticaAprobacionService.aplicable(TipoPolitica, monto)` → `Optional<PoliticaAprobacion>` (vacío = no requiere aprobación). Lecturas con `@UsuarioInterno`.
```
empresa/
├── entity/      Empresa, Sede, CentroCosto, PoliticaAprobacion, ConfigNotificacion
├── repository/  uno por entity
├── dto/         Request/Response de cada una
├── service/     EmpresaService, SedeService, CentroCostoService, PoliticaAprobacionService
└── controller/  /api/empresa, /api/sedes, /api/centros-costo, /api/politicas-aprobacion
```
- Reglas: rangos de política sin traslape por tipo; `PoliticaAprobacionService.resolver(tipo, monto)` → rol aprobador (lo usan solicitud y adjudicación).
- Listo cuando: se configuran sedes, centros de costo y políticas.

## Paso 5 — `catalogo/` · ✅
Para usar después: `UnidadMedidaService.convertir(...)` (normalización de cotizaciones, paso 13); validar en productos que categoría y unidad estén activas.
```
catalogo/  entity: CategoriaProducto, UnidadMedida · repository · dto · service · controller
           /api/categorias, /api/unidades-medida (escritura ADMIN_COMPRAS)
```
- Reglas: nombres/códigos únicos; desactivar, no borrar.

## Paso 6 — `inventario/` · UC-02, UC-03 · ✅
Para la recepción (paso 17): `MovimientoInventarioService.registrarEntradaCompra(productoId, bodegaId, cantidad, "RECEPCION", recepcionId)` y `registrarDevolucionProveedor(...)`. Cantidades en la unidad del producto (convertir antes con `UnidadMedidaService`).
```
inventario/
├── entity/      Producto, Bodega, StockProductoBodega, MovimientoInventario
├── enums/       TipoMovimiento
├── repository/  … + StockRepository (con lock PESSIMISTIC_WRITE para actualizar)
├── dto/         ProductoRequest/Response, BodegaRequest/Response, MovimientoRequest/Response, StockResponse
├── service/     ProductoService, BodegaService, MovimientoInventarioService (ÚNICO que cambia stock)
└── controller/  /api/productos, /api/bodegas, /api/inventario/movimientos, /api/inventario/stock
```
- Reglas: código de producto único; ajuste exige motivo; traslado origen ≠ destino; stock nunca negativo; NO existe endpoint para editar stock; `registrarEntradaCompra(...)` público para recepción; alerta de stock < mínimo.
- Listo cuando: entradas/salidas/ajustes/traslados actualizan stock y quedan en historial con `stock_resultante`.

## Paso 7 — `documento/` (antes de cotizaciones/pago/recepción) · ✅
Pendiente para pasos 12, 15, 16, 17: crear la `ReglaAccesoDocumentos` de COTIZACION, ORDEN_COMPRA, PAGO y RECEPCION, y guardar el id en `cotizacion.documento_original_id` / `pago.comprobante_documento_id`.
```
documento/
├── entity/ Documento · repository/ DocumentoRepository (findByEntidadTipoAndEntidadId)
├── storage/ StorageClient (interfaz) + LocalStorageClient (carpeta local; luego S3)
├── service/ DocumentoService (subir, descargar, listar por entidad; valida MIME y tamaño)
├── dto/ DocumentoResponse
└── controller/ /api/documentos (multipart)
```
- Listo cuando: se sube PDF/Excel/imagen, se guarda fuera de la BD y se descarga.

## Paso 8 — `notificacion/` · ✅
```
notificacion/  entity: Notificacion, ConfigNotificacion · enums: EventoNotificacion (con asunto), CanalNotificacion
               service: NotificacionService.notificar(usuarioId, evento, msg) / notificarRol(rol, evento, msg)
               email/EmailClient (interfaz) + LogEmailClient · controller: /api/notificaciones, /api/config-notificaciones (ADMIN_SISTEMA)
```
Uso en pasos siguientes: agregar el evento a `EventoNotificacion` + sembrar sus canales en una migración; llamar `notificarRol`/`notificar` dentro de la transacción del Service (el correo sale después del commit).

## Paso 9 — `proveedor/` · portal + UC-14 · ✅
Hecho: perfil editable (`PUT /api/portal/proveedor` y `PUT /api/proveedores/{id}`, sin NIT ni razón social), filtro `categoriaId` + orden `scoreActual`, `ProveedorService.habilitadosDeCategoria(categoriaId)` para el paso 11, `EvaluacionProveedorService.registrar(proveedorId, ordenCompraId, IndicadoresEvaluacion)` para el paso 17 (score = promedio simple de los indicadores con datos; `score_actual` = promedio de scores), listados `/api/proveedores/{id}/evaluaciones` y `/api/portal/proveedor/evaluaciones`. MapsClient pospuesto (opcional).
```
proveedor/
├── entity/      Proveedor, ProveedorCategoria, EvaluacionProveedor
├── repository/  … + findHabilitadosPorCategoria
├── dto/         ProveedorRequest/Response, UsuarioPortalRequest, EvaluacionResponse
├── service/     ProveedorService (crea proveedor + su usuario PROVEEDOR), EvaluacionProveedorService
├── maps/        MapsClient (interfaz; geocodificación server-side, opcional)
└── controller/  /api/proveedores (ADMIN_COMPRAS), /api/portal/proveedor/perfil (PROVEEDOR)
```
- Reglas: NIT único; un PROVEEDOR solo ve su propio `proveedor_id`; evaluación: indicador sin datos = NULL; recalcula `score_actual`.

## Paso 10 — `compra/solicitud/` · UC-04, UC-05 · ✅
```
compra/solicitud/
├── entity/   SolicitudCompra, SolicitudCompraLinea, AprobacionSolicitud
├── enums/    EstadoSolicitud, DecisionAprobacion
├── dto/      SolicitudRequest (con líneas), SolicitudResponse, DecisionRequest
├── service/  SolicitudService (crear, enviar, cancelar), AprobacionSolicitudService (aprobar/rechazar/devolver)
└── controller/ /api/solicitudes, POST /{id}/enviar, POST /{id}/decision, GET /pendientes-aprobacion
```
- Reglas: requiere centro de costo; cantidad > 0; política → aprobador; segregación (solicitante ≠ aprobador); DEVUELTA vuelve a BORRADOR; auditar + notificar.

## Paso 11 — `compra/rfq/` · UC-06 · ✅
```
compra/rfq/  entity: Rfq, RfqLinea, RfqProveedor · enums: EstadoRfq
             service: RfqService (crear desde solicitud APROBADA, invitar, abrir, cerrar) + job cierre por fecha_limite (@Scheduled)
             controller: /api/rfqs, POST /{id}/abrir, POST /{id}/cerrar · portal: GET /api/portal/rfqs (invitadas)
```
- Reglas: alerta si no hay proveedores habilitados para la categoría; al abrir: solicitud → EN_RFQ y notificar invitados.

## Paso 11-bis — `despacho/` (agregado por el usuario) · ✅
Remisión de salida para lo que se vende o entrega (pensado para una ferretería, sin precios ni facturación). `Despacho` + `DespachoLinea` (V10), BORRADOR → CONFIRMADO (salidas SALIDA_VENTA, todo o nada, en orden de producto) → ANULADO (entradas ANULACION_DESPACHO con motivo). Endpoints `/api/despachos` (ALMACENISTA crea/confirma; anula ALMACENISTA o ADMIN_COMPRAS). Futuro opcional: carga del cierre de caja desde Excel/CSV (opción C).

## Paso 12 — `compra/cotizacion/` · UC-07 · ✅
```
compra/cotizacion/  entity: Cotizacion, CotizacionLinea · enums: EstadoCotizacion, OrigenCotizacion
                    service: CotizacionService (formulario o archivo; calcula subtotal/impuestos/total en Java)
                    controller: /api/portal/rfqs/{id}/cotizacion (PROVEEDOR), /api/rfqs/{id}/cotizaciones (COMPRADOR)
```
- Reglas: solo RFQ ABIERTA y vigente; una cotización por proveedor por RFQ; se conserva el archivo original; origen ARCHIVO dispara IA (paso 13).

## Paso 13 — `ia/` + `compra/analisis/` · UC-08 · ✅
```
ia/
├── client/   IAClient (interfaz) + implementación del proveedor elegido + esquema JSON de salida
├── entity/   ProcesamientoIa, ExtraccionCampo
├── service/  ExtraccionService (@Async), ExplicacionService
└── controller/ /api/cotizaciones/{id}/extraccion (ver/confirmar campos)
compra/analisis/
├── entity/   CriterioEvaluacion, ConfigPesosEvaluacion(+Detalle), AnalisisCotizacion, AnalisisCotizacionResultado,
│             AnalisisCotizacionDetalle, AnomaliaCotizacion
├── service/  PesosService (vigente, suma = 100, nueva versión), MotorComparacionService (normaliza, puntúa, rankea — Java puro),
│             AnalisisService (orquesta y publica)
└── controller/ /api/rfqs/{id}/analisis, POST /publicar, /api/config-pesos
```
- Reglas: confianza baja en dato crítico bloquea publicar hasta confirmar; anomalía = alerta; snapshot de pesos; la IA nunca calcula.
- Empezar con un `IAClient` falso (mock) para avanzar sin API; prueba unitaria del motor con el ejemplo del PDF.

## Paso 14 — `compra/adjudicacion/` · UC-09 · ✅
```
compra/adjudicacion/  entity: Adjudicacion · service: AdjudicacionService (crear, aprobar, rechazar)
                      controller: /api/rfqs/{id}/adjudicacion, POST /api/adjudicaciones/{id}/decision
```
- Reglas: requiere análisis PUBLICADO; justificación si no sigue recomendación; política ADJUDICACION decide si requiere aprobación; marca cotizaciones SELECCIONADA/NO_SELECCIONADA; RFQ → ADJUDICADA.

## Paso 15 — `compra/orden/` · UC-10, UC-12 · ✅
```
compra/orden/  entity: OrdenCompra, OrdenCompraLinea, OrdenCompraEstadoHistorial · enums: EstadoOrden
               service: OrdenCompraService (emitir desde adjudicación APROBADA, nueva versión, cancelar), OrdenProveedorService (aceptar/rechazar/preparar/despachar)
               controller: /api/ordenes, /api/portal/ordenes
```
- Reglas: inmutable (nueva versión con motivo, anterior REEMPLAZADA); cada cambio de estado → historial + auditoría + notificación.

## Paso 16 — `pago/` · UC-11 · ✅
```
pago/  entity: Pago · service: PagoService · controller: /api/ordenes/{id}/pagos (ADMINISTRATIVO)
```
- Reglas: valor > 0; sin comprobante ⇒ `evidencia_pendiente=1`; segregación con solicitante/aprobador si la política lo pide.
- Hecho: `POST/GET /api/ordenes/{id}/pagos`, `GET /api/pagos` (filtros), `GET /api/pagos/{id}`, `POST /api/pagos/{id}/comprobante`, `POST /api/pagos/{id}/anular`, portal `GET /api/portal/ordenes/{id}/pagos`. Decisiones del usuario: pagos desde EMITIDA (anticipos/parciales, Σ ≤ total vigente), anular con motivo (V15 ANULADO), segregación a todos los involucrados.

## Paso 17 — `recepcion/` · UC-13 · ✅ (2026-10-23, V18)
```
recepcion/  entity: Recepcion (@TenantId), RecepcionLinea · service: RecepcionService, CalculadoraRecepcion, RecepcionReglaDocumentos
            controller: RecepcionController (/api/ordenes/{id}/recepciones, /api/recepciones), PortalRecepcionController
```
- Orden DESPACHADA o RECIBIDA_PARCIAL; varias recepciones por orden. Por línea se informa recibido y rechazado (con `MotivoRechazo` obligatorio); Java reparte: aceptada = lo bueno hasta completar el pedido, excedente = lo bueno que sobra (línea EN_REVISION, no suma stock), faltante = lo que aún falta.
- Solo lo aceptado sube el stock (`registrarEntradaCompra`, referencia RECEPCION). Lo rechazado nunca entra: queda como devolución pendiente al proveedor, con su motivo (no hay movimiento de stock porque nunca ingresó). Cada recepción pone `producto.costo_referencia` = último precio de la orden.
- La orden pasa a RECIBIDA_PARCIAL o RECIBIDA_TOTAL. `POST .../recepciones/cerrar` (ALMACENISTA o ADMIN_COMPRAS): exige que no haya excedente sin resolver; con faltante (RECIBIDA_PARCIAL → CERRADA) exige motivo; al cerrar evalúa al proveedor (puntualidad, cumplimiento, incidencias y calidad calculados en `CalculadoraRecepcion`; precio y respuesta sin datos).
- Excedente: `POST /api/recepciones/lineas/{id}/resolver-exceso` (COMPRADOR o ADMIN_COMPRAS) ACEPTAR (entra al stock) o DEVOLVER, con motivo.
- Roles: registra ALMACENISTA; consultan compras, administrativo, aprobador y auditor; el proveedor ve las de sus órdenes en `/api/portal/ordenes/{id}/recepciones` con los motivos de rechazo. Evidencia fotográfica: `/api/documentos` con entidad RECEPCION (`RecepcionReglaDocumentos`).
- Avisos: RECEPCION_REGISTRADA (proveedor y comprador emisor), RECEPCION_EXCEDENTE (rol COMPRADOR), RECEPCION_CERRADA. Auditoría: RECEPCION_REGISTRAR, RECEPCION_RESOLVER_EXCESO, RECEPCION_CERRAR.

## Paso 17-bis — factura y cruce de tres vías · ✅ (2026-10-23, V19)
```
factura/  entity: FacturaProveedor (@TenantId) · service: FacturaService, CalculadoraFactura, FacturaReglaDocumentos
          controller: FacturaController (/api/ordenes/{id}/facturas, /api/facturas), PortalFacturaController
pago/     + enum TipoPago (ANTICIPO, PAGO_FINAL) · columna pago.tipo
empresa/  + tope_anticipo_pct, tolerancia_factura_pct, tolerancia_factura_monto (editables en PUT /api/empresa)
```
- **Anticipo:** `tipo = ANTICIPO` desde que se emite la orden; la suma de anticipos REGISTRADOS (todas las versiones) no supera `tope_anticipo_pct` (50 % por defecto) del total vigente ⇒ 409 `ANTICIPO_EXCEDE_TOPE`.
- **Factura:** la registra ADMINISTRATIVO sobre una orden con recepciones (RECIBIDA_PARCIAL, RECIBIDA_TOTAL o CERRADA). Una factura vigente por orden; número único por proveedor mientras no esté anulada. `CalculadoraFactura` calcula lo esperado (Σ cantidad aceptada × precio de la orden + impuesto por línea) y compara con el total: dentro de la tolerancia (porcentaje del esperado O monto fijo, lo que cubra) ⇒ CUADRADA; si no ⇒ EN_REVISION y avisa a ADMIN_COMPRAS. Sin nada aceptado nunca cuadra.
- **EN_REVISION:** ADMIN_COMPRAS aprueba con motivo (APROBADA); quien registró la factura no puede aprobarla (segregación). `recruzar` la vuelve a cruzar si la recepción cambió. Una factura equivocada se anula con motivo (no si ya hay un pago final registrado) y su número se puede volver a registrar. PDF: `POST /api/facturas/{id}/archivo` (una vez).
- **Pago final:** `tipo = PAGO_FINAL` solo con la orden RECIBIDA_PARCIAL/RECIBIDA_TOTAL/CERRADA y una factura CUADRADA o APROBADA (`FacturaService.exigirPagable` recalcula el cruce de una CUADRADA: si la recepción cambió y ya no cuadra ⇒ 409 `FACTURA_NO_CUADRA`). No supera lo que falta por pagar de la factura (descuenta los anticipos) ni el saldo de la orden. Sin factura ⇒ 409 `FACTURA_REQUERIDA`; en revisión ⇒ `FACTURA_EN_REVISION`.
- Avisos FACTURA_EN_REVISION (rol ADMIN_COMPRAS) y FACTURA_DECIDIDA (quien la registró). Auditoría: FACTURA_REGISTRAR, FACTURA_RECRUZAR, FACTURA_APROBAR, FACTURA_ANULAR, FACTURA_ARCHIVO. Portal: `/api/portal/ordenes/{id}/facturas`.
- Límite conocido: una sola factura vigente por orden; si el proveedor factura por entregas, debe emitir una factura que cubra lo recibido.

## Paso 18 — `reporte/` · UC-15 · ✅ (2026-10-27, sin migración)
```
reporte/  dto: GastoResponse, InventarioResumen, ProveedorRanking · service: ReporteService (consultas agregadas, solo lectura)
          controller: /api/reportes/{gasto|inventario|proveedores}?desde&hasta&categoria&proveedor
```
- Reglas: sin datos = lista vacía, nunca error. Gasto = líneas de órdenes vigentes (sin CANCELADA, RECHAZADA_PROVEEDOR, REEMPLAZADA), subtotal + impuesto de línea. Estado de cuenta: saldo = (factura vigente, o total de la orden si no hay) − pagado. Ranking: proveedores con órdenes en el período, por score (sin score al final). Límite conocido: el inventario no se pagina.

## Ajustes a pasos pendientes (decisión 2026-10-23)
- **Paso 17 (recepción):** cada línea rechazada exige motivo (catálogo) y foto opcional; lo rechazado llama a `registrarDevolucionProveedor` y alimenta el indicador de calidad del score.
- **Paso 17-bis:** tolerancia del cruce configurable por empresa (`tolerancia_factura_pct`, por defecto 1 %).

## Pasos 18-a a 18-d — compras avanzadas (después de reportes, detalle en `saas.md`)
- **18-a `presupuesto/` ✅ (2026-10-28, V20):** presupuesto por centro de costo y mes; al enviar la solicitud se compara con lo comprometido; 409 `PRESUPUESTO_EXCEDIDO`; excepción con justificación y auditoría.
  - Reglas: el mes lo da la **fecha requerida** de la solicitud. Comprometido = Σ `monto_estimado` de solicitudes del centro en ese mes con estado PENDIENTE_APROBACION, APROBADA, EN_RFQ, ADJUDICADA o CERRADA (es un estimado, no el gasto real de las órdenes). Llegar justo al tope se permite. Sin presupuesto en el mes no hay control. La fila del presupuesto se bloquea (`FOR UPDATE`) al evaluar: dos envíos a la vez no se pasan juntos del tope.
  - Excepción: `POST /api/solicitudes/{id}/enviar` acepta un cuerpo opcional `{justificacionExcepcion}`. Con ella, una solicitud que excede el presupuesto pasa siempre por la política de **mayor tramo**; si la empresa no tiene política de solicitud, no hay excepción posible (409). Queda `excede_presupuesto`, la justificación y la acción `SOLICITUD_EXCEPCION` en auditoría.
  - Duplicadas: 409 `SOLICITUD_DUPLICADA` si otra solicitud PENDIENTE_APROBACION o APROBADA del mismo centro pide alguno de los mismos productos; la misma justificación la permite (no cambia la política).
  - API: `/api/presupuestos` (POST y PUT: ADMIN_COMPRAS; GET: ADMIN_COMPRAS, COMPRADOR, APROBADOR, ADMINISTRATIVO, AUDITOR). Lista y detalle traen comprometido, disponible y porcentaje de uso. No se borra.
- **18-b `contrato/` ✅ (2026-10-28, V21):** contratos y precios pactados con vigencia y tope; orden directa o RFQ; nunca se aplica un precio vencido.
  - Contrato: proveedor HABILITADO, número único por empresa, vigencia (la fecha de fin no puede estar en el pasado), y por producto precio sin impuesto en la unidad del producto, impuesto % y `cantidad_maxima` opcional. Solo se mueve la fecha de fin (`PATCH /{id}/vigencia`) o se anula con motivo (`PATCH /{id}/anular`); los precios no se editan. Dos contratos ACTIVOS del mismo proveedor no pueden cruzarse en fechas para el mismo producto (409 `CONTRATO_SOLAPADO`). El vencimiento se calcula con las fechas, no hay job.
  - Ofertas: `GET /api/contratos/ofertas?solicitudId=` (COMPRADOR, ADMIN_COMPRAS) valora la solicitud APROBADA contra cada contrato vigente que toque sus productos. Primero van las que se pueden emitir y, entre ellas, la más barata. Solo informa: la persona elige entre orden directa y RFQ.
  - Orden directa `POST /api/ordenes/directa` (COMPRADOR, `OrdenDirectaService`): exige solicitud APROBADA, que quien emite no sea su solicitante, contrato vigente hoy, proveedor HABILITADO, que el contrato cubra TODOS los productos (409 `CONTRATO_NO_CUBRE`), que cada cantidad quepa en el tope que queda (409 `CONTRATO_TOPE_EXCEDIDO`; lo consumido suma las líneas de órdenes vivas, sin CANCELADA ni REEMPLAZADA, y el contrato se bloquea `FOR UPDATE`), que el monto aprobado fuera completo (409 `MONTO_INCOMPLETO`) y que el subtotal al precio pactado no supere ese monto (409 `ORDEN_SUPERA_APROBADO`): así la orden directa no esquiva la aprobación. No pasa por la política ADJUDICACION: lo protege el monto ya aprobado.
  - Efectos: la orden nace EMITIDA (v1) con `contrato_id` y `solicitud_id` y sin adjudicación; sus líneas llevan `contrato_precio_id`. La solicitud pasa a ADJUDICADA; si la orden se cancela, vuelve a APROBADA. Una versión nueva de una orden de contrato copia el origen y solo puede bajar cantidades. Auditoría: CONTRATO_CREAR, CONTRATO_EDITAR, CONTRATO_ANULAR, ORDEN_EMITIR_DIRECTA.
  - No incluido: alerta en el comparador cuando una cotización supera el precio pactado, y RFQ con precio de referencia precargado.
- **18-c `delegacion/` + SLA ✅ (2026-10-28, V22):** delegación de aprobación por fechas y job de escalamiento por horas (aviso al 80 %, escala al vencer); segregación de funciones intacta.
  - Delegación: `POST /api/delegaciones {delegadoId, vigenteDesde, vigenteHasta, motivo}` la crea el usuario de la sesión si tiene un rol que aprueba en alguna política activa (409 `SIN_ROL_APROBADOR`); el delegado es un usuario interno activo distinto (409 `DELEGADO_INVALIDO`) y la fecha de fin no está en el pasado. `PATCH /{id}/revocar` (delegante o ADMIN_COMPRAS), `GET /otorgadas` y `/recibidas`. El delegado decide con los roles que el delegante tiene en la BD en ese momento: `DelegacionService.autorizar(sesion, rol, autorId)` devuelve vacío si decide con su propio rol o el id de la delegación (403 si no tiene ninguna que sirva). La consulta excluye las delegaciones de quien originó lo que se decide, y quien lo originó nunca decide lo suyo. Aplica a `AprobacionSolicitudService` y a `AdjudicacionService` (decidir y bandeja). Queda en `aprobacion_solicitud.delegacion_id` y `adjudicacion.delegacion_id`. Auditoría: DELEGACION_CREAR, DELEGACION_REVOCAR y `porDelegacion=` en la decisión.
  - SLA (`SlaAprobacionService`, solo solicitudes): `empresa.sla_aprobacion_horas` (editable por ADMIN_SISTEMA en `/api/empresa`). Horas corridas desde `enviado_en`. Al 80 % avisa una vez (`sla_aviso_en`); al vencer escala una vez (`escalada_en`): cambia a la política de mayor tramo y avisa al rol, a sus delegados y al solicitante; si ya estaba en el más alto, avisa también a ADMIN_COMPRAS. La solicitud sigue PENDIENTE_APROBACION; reenviarla reinicia las marcas. Auditoría SOLICITUD_ESCALAR (sin usuario). Job `SlaAprobacionJob`: `app.aprobacion.sla-automatico` (true) y `app.aprobacion.sla-cada-ms` (300000); apagado en las pruebas.
  - Límite: la bandeja del delegado puede mostrar una solicitud del delegante, que luego se rechaza con 403 al decidir. El SLA no cubre adjudicaciones.
- **18-d reorden + carga masiva ✅ (2026-10-28, sin migración):** sugerir solicitud al cruzar el mínimo (máximo − stock − pendiente); solo sugiere, la persona decide.
  - `ReordenService`: productos activos con stock total menor que el mínimo (`ProductoRepository.bajoStockMinimo`). Cantidad = `stock_maximo` − stock total − por recibir − ya solicitado, nunca negativa. Por recibir = Σ cantidad de líneas de órdenes EMITIDA, ACEPTADA_PROVEEDOR, EN_PREPARACION, DESPACHADA o RECIBIDA_PARCIAL menos Σ `cantidad_aceptada` de sus recepciones (lo rechazado cuenta como pendiente). Ya solicitado = líneas de solicitudes PENDIENTE_APROBACION, APROBADA o EN_RFQ, convertidas a la unidad del producto (las ADJUDICADA no cuentan, para no duplicar con su orden). Sin máximo: `sinMaximo` y sin cantidad. Cubierto: cantidad 0 y `yaCubierto`. Pista de proveedor: el contrato vigente de menor precio, solo informativo.
  - `GET /api/reorden/sugerencias` (ADMIN_COMPRAS, COMPRADOR, ALMACENISTA, SOLICITANTE, AUDITOR). `POST /api/reorden/borrador {fechaRequerida, productoIds?}` (SOLICITANTE) reutiliza `SolicitudService.crear`: queda BORRADOR a nombre del solicitante, con su centro de costo, y se envía por el flujo normal (presupuesto, duplicadas, política). 409 `SIN_SUGERENCIAS` o `PRODUCTO_SIN_SUGERENCIA`. Auditoría REORDEN_GENERAR.
  - Carga masiva (`ProductoCargaService`, `/api/productos/carga-masiva`, ADMIN_COMPRAS): .xlsx de hasta 5 MB y 2.000 filas; encabezados `codigo` (obligatorio), `nombre`, `categoria`, `unidad`, `descripcion`, `stock_minimo`, `stock_maximo`, `costo_referencia` (sin importar mayúsculas ni tildes). El código identifica; celda vacía = no cambia (nuevo exige nombre, categoría y unidad). Categoría por nombre, se crea si falta; unidad por código, debe estar activa; no se cambia la unidad con movimientos (UNIDAD_EN_USO). De todo o nada con errores por fila y `soloValidar`. No crea movimientos ni cambia `activo`. Auditoría PRODUCTO_CARGA_MASIVA. `GET /plantilla` baja un Excel de ejemplo.
  - Límites: no hay escalas de precio por cantidad ni proveedor sugerido por historial; la carga no actualiza precios de contratos.
- **Fases futuras:** factura electrónica DIAN (vía proveedor tecnológico) y multimoneda con tasa congelada en el análisis.

## Fases SaaS (decisión 2026-10-23, detalle en `saas.md`; la fase 2 quedó hecha el 2026-10-28, V23: consola, planes, módulos y `@RequiereModulo`)
Entre el 18 y el 19 van la **fase 1** (`empresa_id` en el JWT y en todas las tablas, filtro central por tenant, consecutivos y archivos por empresa, pruebas de fuga) y la **fase 2** (consola de plataforma, planes, módulos, `@RequiereModulo`), para que el cierre (19) pruebe multiempresa. Recepción (17) y factura (17-bis) ya se escriben con `empresa_id`. La seguridad de sesión (refresh, bloqueo, rate limit, V16) y la fase 1 (aislamiento por empresa con `@TenantId`, V17) ya están hechas: todo módulo nuevo agrega `@TenantId` a sus entidades de negocio y su prueba en `AislamientoEmpresasTest`.

## Paso 19 — Cierre
- Pruebas: unitarias de cada Service (reglas y transiciones), integración del flujo completo (solicitud → recepción).
- Seguridad: revisar `@RequiereRol` en todos los endpoints y el filtro por `proveedor_id` en el portal.
- Swagger completo, README actualizado.
- IA: la extracción también trae el total impreso (texto); Java lo compara con Σ líneas y si difiere > 1 % ⇒ REQUIERE_REVISION con alerta `TOTAL_NO_CUADRA`.
- BD: script `docs/sql/permisos.sql` con dos usuarios (`supply_migrator` para Flyway vía `spring.flyway.user`, `supply_app` sin UPDATE/DELETE en `auditoria`, `movimiento_inventario`, `aprobacion_solicitud`, `orden_compra_estado_historial`, `evaluacion_proveedor`, `documento`).
- Documento de exposición: una sola numeración (pasos 0–19), tabla de los 9 roles, ER partido en tres, explicar IVA/moneda única COP, referencias polimórficas (índice ya existe) y cierre de RFQ seguro con dos instancias (`FOR UPDATE` + re-verificación).

---

## Plantilla por módulo (repetir en cada paso)
1. Grep en V1 las tablas del módulo (`modelo-datos.md`).
2. `entity/` + `enums/` → `repository/` → `dto/` (+ `mapper/`) → `service/` (reglas de `reglas-negocio.md`) → `controller/` con `@RequiereRol`.
3. Auditoría en acciones sensibles y notificaciones si aplica.
4. Prueba del Service + prueba manual en Swagger.
5. Marcar ✅ aquí y actualizar `estado.md`.
