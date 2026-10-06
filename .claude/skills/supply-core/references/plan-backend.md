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

## Paso 13 — `ia/` + `compra/analisis/` · UC-08
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

## Paso 14 — `compra/adjudicacion/` · UC-09
```
compra/adjudicacion/  entity: Adjudicacion · service: AdjudicacionService (crear, aprobar, rechazar)
                      controller: /api/rfqs/{id}/adjudicacion, POST /api/adjudicaciones/{id}/decision
```
- Reglas: requiere análisis PUBLICADO; justificación si no sigue recomendación; política ADJUDICACION decide si requiere aprobación; marca cotizaciones SELECCIONADA/NO_SELECCIONADA; RFQ → ADJUDICADA.

## Paso 15 — `compra/orden/` · UC-10, UC-12
```
compra/orden/  entity: OrdenCompra, OrdenCompraLinea, OrdenCompraEstadoHistorial · enums: EstadoOrden
               service: OrdenCompraService (emitir desde adjudicación APROBADA, nueva versión, cancelar), OrdenProveedorService (aceptar/rechazar/preparar/despachar)
               controller: /api/ordenes, /api/portal/ordenes
```
- Reglas: inmutable (nueva versión con motivo, anterior REEMPLAZADA); cada cambio de estado → historial + auditoría + notificación.

## Paso 16 — `pago/` · UC-11
```
pago/  entity: Pago · service: PagoService · controller: /api/ordenes/{id}/pagos (ADMINISTRATIVO)
```
- Reglas: valor > 0; sin comprobante ⇒ `evidencia_pendiente=1`; segregación con solicitante/aprobador si la política lo pide.

## Paso 17 — `recepcion/` · UC-13
```
recepcion/  entity: Recepcion, RecepcionLinea · service: RecepcionService · controller: /api/ordenes/{id}/recepciones
```
- Reglas: orden DESPACHADA; por línea recibida/faltante/rechazada; llama a `MovimientoInventarioService.registrarEntradaCompra` solo por lo aceptado; exceso ⇒ EN_REVISION; parcial/total actualiza orden; al cerrar dispara `EvaluacionProveedorService`.

## Paso 18 — `reporte/` · UC-15
```
reporte/  dto: GastoResponse, InventarioResumen, ProveedorRanking · service: ReporteService (consultas agregadas, solo lectura)
          controller: /api/reportes/{gasto|inventario|proveedores}?desde&hasta&categoria&proveedor
```
- Reglas: sin datos = lista vacía, nunca error.

## Paso 19 — Cierre
- Pruebas: unitarias de cada Service (reglas y transiciones), integración del flujo completo (solicitud → recepción).
- Seguridad: revisar `@RequiereRol` en todos los endpoints y el filtro por `proveedor_id` en el portal.
- Swagger completo, README actualizado.

---

## Plantilla por módulo (repetir en cada paso)
1. Grep en V1 las tablas del módulo (`modelo-datos.md`).
2. `entity/` + `enums/` → `repository/` → `dto/` (+ `mapper/`) → `service/` (reglas de `reglas-negocio.md`) → `controller/` con `@RequiereRol`.
3. Auditoría en acciones sensibles y notificaciones si aplica.
4. Prueba del Service + prueba manual en Swagger.
5. Marcar ✅ aquí y actualizar `estado.md`.
