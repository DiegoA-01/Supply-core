# Modelo de datos (resumen de V1)

DDL real: `backend/src/main/resources/db/migration/V1__esquema_inicial.sql` (Grep `CREATE TABLE <tabla>` -A 25; no leer entero).
Convenciones: PK `id BIGINT AUTO_INCREMENT`; FK `<tabla>_id BIGINT`; booleanos `activo TINYINT(1)`; dinero `DECIMAL(18,2)`, cantidades/precios unitarios `DECIMAL(18,4)`; fechas `creado_en DATETIME DEFAULT CURRENT_TIMESTAMP`.

## Tabla → módulo (paquete Java)
| Módulo | Tablas |
|---|---|
| `security` + `usuario` | usuario (centro_costo_id?, proveedor_id?), rol, permiso, rol_permiso, usuario_rol |
| `empresa` | empresa, sede, centro_costo, politica_aprobacion, config_notificacion |
| `catalogo` | categoria_producto, unidad_medida |
| `inventario` | producto, bodega (sede_id?), stock_producto_bodega, movimiento_inventario |
| `proveedor` | proveedor, proveedor_categoria, evaluacion_proveedor |
| `compra.solicitud` | solicitud_compra, solicitud_compra_linea, aprobacion_solicitud |
| `compra.rfq` | rfq, rfq_linea, rfq_proveedor |
| `compra.cotizacion` | cotizacion, cotizacion_linea |
| `compra.analisis` | criterio_evaluacion, config_pesos_evaluacion(+_detalle), analisis_cotizacion, analisis_cotizacion_resultado, analisis_cotizacion_detalle, anomalia_cotizacion |
| `ia` | procesamiento_ia, extraccion_campo |
| `compra.adjudicacion` | adjudicacion |
| `compra.orden` | orden_compra, orden_compra_linea, orden_compra_estado_historial |
| `recepcion` | recepcion, recepcion_linea |
| `pago` | pago |
| `documento` | documento (polimórfica `entidad_tipo` + `entidad_id`) |
| `notificacion` | notificacion |
| `auditoria` | auditoria (entidad + entidad_id, resultado EXITO/FALLO, ip) |

## Enums (valores exactos)
- movimiento_inventario.tipo: ENTRADA_COMPRA, SALIDA_CONSUMO, AJUSTE_POSITIVO, AJUSTE_NEGATIVO, DEVOLUCION_PROVEEDOR, TRASLADO
- solicitud_compra.estado: BORRADOR, PENDIENTE_APROBACION, APROBADA, DEVUELTA, RECHAZADA, EN_RFQ, ADJUDICADA, CANCELADA, CERRADA
- aprobacion_solicitud.decision: APROBADA, RECHAZADA, DEVUELTA
- politica_aprobacion.tipo: SOLICITUD, ADJUDICACION (con rol_aprobador_id, monto_desde, monto_hasta NULL = sin tope)
- rfq.estado: BORRADOR, ABIERTA, CERRADA, ADJUDICADA, CANCELADA
- cotizacion.estado: BORRADOR, ENVIADA, EN_ANALISIS, SELECCIONADA, NO_SELECCIONADA, RECHAZADA · origen: FORMULARIO, ARCHIVO
- procesamiento_ia.estado: PENDIENTE, PROCESANDO, COMPLETADO, REQUIERE_REVISION, ERROR
- analisis_cotizacion.estado: BORRADOR, PUBLICADO · anomalia.severidad: BAJA, MEDIA, ALTA
- adjudicacion.estado: PENDIENTE_APROBACION, APROBADA, RECHAZADA
- orden_compra.estado: EMITIDA, ACEPTADA_PROVEEDOR, RECHAZADA_PROVEEDOR, EN_PREPARACION, DESPACHADA, RECIBIDA_PARCIAL, RECIBIDA_TOTAL, CERRADA, CANCELADA, REEMPLAZADA
- pago.estado: PENDIENTE (sin uso), REGISTRADO (+ evidencia_pendiente), ANULADO (V15, con motivo_anulacion) · pago.metodo: TRANSFERENCIA, CONSIGNACION, CHEQUE, EFECTIVO, TARJETA, OTRO
- recepcion_linea.estado: ACEPTADA, EN_REVISION
- config_notificacion.canal: EMAIL, INTERNA

## Claves de diseño
- Stock real en `stock_producto_bodega` (UNIQUE producto+bodega, CHECK ≥ 0); `movimiento_inventario` guarda `stock_resultante` y `referencia_tipo/id`.
- Normalización: `cotizacion_linea.factor_conversion` (unidades base por unidad ofertada) → `precio_unitario_normalizado` calculado en Java.
- Snapshot: `analisis_cotizacion.config_pesos_evaluacion_id` + `peso_aplicado` copiado en `analisis_cotizacion_detalle`. Pesos por config deben sumar 100 (validar en app).
- Orden versionada: UNIQUE (consecutivo, version), `orden_padre_id`.
- `cotizacion` UNIQUE (rfq_id, proveedor_id): una cotización por proveedor por RFQ.
- FKs a `documento` se agregan con ALTER al final de V1 (cotizacion.documento_original_id, pago.comprobante_documento_id).

## Cambios posteriores a V1
- V3: tabla `consecutivo (prefijo, ultimo)` para SOL/RFQ/OC.
- V4: `proveedor.estado` ENUM(PENDIENTE, HABILITADO, RECHAZADO, SUSPENDIDO) + `motivo_estado`, `revisado_por_usuario_id`, `revisado_en`; **se eliminó `proveedor.activo`**. 4 categorías demo (Papelería y oficina, Aseo y cafetería, Tecnología, Ferretería y mantenimiento).

- V5: `unidad_medida.magnitud` ENUM(CANTIDAD, MASA, VOLUMEN, LONGITUD) + `activo`. Factor = unidades base de su magnitud (UND, KG, LT, M = 1). Unidades nuevas: CAJA24 (24), G (0.001), ML (0.001), M (1), CM (0.01). Código, magnitud y factor inmutables (solo cambia el nombre).

- V6: `movimiento_inventario.stock_resultante_destino` (solo TRASLADO: saldo del destino; `stock_resultante` = saldo de la bodega afectada / origen) + índices por bodega origen/destino.

## Semillas (V2)
Empresa demo id=1; 9 roles; criterios PRECIO 35, CALIDAD 20, ENTREGA 15, GARANTIA 10, HISTORIAL 15, RIESGO 5; unidades UND, DOC, KG, LT. El admin se crea en Java (BCrypt), no en SQL.
