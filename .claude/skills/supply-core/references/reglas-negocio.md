# Reglas de negocio y casos de uso

Resumen fiel del documento funcional (`docs/proyecto supply-core.pdf`, no abrir).

## Roles (códigos en tabla `rol`)
ADMIN_SISTEMA (usuarios, seguridad, catálogos; no decide compras) · ADMIN_COMPRAS (políticas, categorías, proveedores habilitados) · SOLICITANTE · COMPRADOR (RFQ, análisis, orden) · APROBADOR (aprueba según límite) · ALMACENISTA (recepción, movimientos) · ADMINISTRATIVO (registra pago) · PROVEEDOR (portal: cotiza, confirma, despacha) · AUDITOR (solo lectura).
Segregación: no registra el pago quien solicitó, aprobó la solicitud, adjudicó, aprobó la adjudicación o emitió alguna versión de la orden (validado en `PagoService`).

## Flujo de punta a punta
Necesidad (stock < mínimo o manual) → Solicitud → Aprobación → RFQ → Proveedor cotiza (form o archivo) → IA extrae (confianza por dato) → Java valida/calcula/normaliza → IA explica ranking → Comprador decide → Aprobación de adjudicación si el monto lo exige → Orden de compra → Pago registrado → Proveedor confirma/despacha → Recepción → Stock sube → Evaluación del proveedor → Cierre.

## Casos de uso (actor · reglas clave · flujo alterno)
- **UC-01 Login** · todos · menú según roles · credenciales inválidas: mensaje genérico (no decir qué falló); cuenta inactiva: rechazar y "contacte al administrador".
- **UC-02 Catálogo** · ADMIN_COMPRAS · código único (si duplica, mostrar el existente) · desactivar ≠ borrar: no usable en nuevas operaciones, conserva historial.
- **UC-03 Movimiento inventario** · ALMACENISTA · producto y bodega activos · ajuste exige motivo · traslado exige origen ≠ destino · recalcula `stock_producto_bodega`, nunca negativo.
- **UC-04 Crear solicitud** · SOLICITANTE con centro de costo · producto, cantidad>0, unidad, fecha requerida, justificación · evalúa política → BORRADOR o PENDIENTE_APROBACION.
- **UC-05 Aprobar solicitud** · APROBADOR asignado · APROBADA / RECHAZADA / DEVUELTA (vuelve al solicitante) con observaciones · notifica + audita.
- **UC-06 Crear RFQ** · COMPRADOR · requiere solicitud APROBADA · productos, cantidades, condiciones, fecha límite, proveedores · alerta si no hay proveedores habilitados para la categoría (`proveedor_categoria`) · al enviar: ABIERTA, solicitud → EN_RFQ.
- **UC-07 Cotizar** · PROVEEDOR invitado · RFQ ABIERTA y no vencida · formulario o archivo (Excel/PDF/imagen), se conserva el original · cotización → ENVIADA.
- **UC-08 Analizar (IA)** · COMPRADOR · ≥1 cotización ENVIADA · dato crítico con confianza baja ⇒ confirmación obligatoria antes de publicar · precio atípico = alerta, nunca exclusión · publica ranking con detalle por criterio.
- **UC-09 Adjudicar** · COMPRADOR/APROBADOR · requiere análisis PUBLICADO · justificación obligatoria si no sigue recomendación · monto > límite ⇒ PENDIENTE_APROBACION.
- **UC-10 Emitir orden** · COMPRADOR · requiere adjudicación APROBADA · proveedor, líneas, precios, lugar y fecha de entrega · inmutable; modificar = nueva versión con motivo (la anterior → REEMPLAZADA) · notifica al proveedor.
- **UC-11 Registrar pago** · ADMINISTRATIVO · orden emitida · método, valor, fecha, referencia, comprobante opcional · sin comprobante: REGISTRADO + `evidencia_pendiente=1`. No hay pasarela ni contabilidad.
- **UC-12 Confirmar/despachar** · PROVEEDOR · ACEPTADA_PROVEEDOR → EN_PREPARACION → DESPACHADA (+evidencia) · rechazo ⇒ RECHAZADA_PROVEEDOR y notifica al comprador.
- **UC-13 Recepción** · ALMACENISTA · orden DESPACHADA · cantidad recibida/faltante/rechazada por línea + evidencia foto · genera ENTRADA_COMPRA solo por lo aceptado · parcial ⇒ RECIBIDA_PARCIAL con saldo abierto · exceso ⇒ línea EN_REVISION, no suma stock.
- **UC-14 Evaluar proveedor** · sistema al cerrar recepción/orden · puntualidad (vs `fecha_entrega_esperada`), cumplimiento, incidencias, calidad, precio, respuesta → score · sin datos ⇒ NULL, nunca valor por defecto · actualiza `proveedor.score_actual`.
- **UC-15 Dashboards** · AUDITOR/gerencia · gasto, inventario, proveedores; filtros fecha/categoría/proveedor · sin datos = estado vacío, no error · solo lectura.
- **UC-16 Auditoría** · AUDITOR · buscar por usuario/entidad/acción/fechas · sin permiso ⇒ bloquear módulo · registros inalterables.

## Máquinas de estado
- solicitud: BORRADOR → PENDIENTE_APROBACION → APROBADA | RECHAZADA | DEVUELTA(→BORRADOR) ; APROBADA → EN_RFQ → ADJUDICADA → CERRADA ; CANCELADA desde estados abiertos.
- rfq: BORRADOR → ABIERTA → CERRADA (fecha límite o manual) → ADJUDICADA ; CANCELADA.
- cotizacion: BORRADOR → ENVIADA → EN_ANALISIS → SELECCIONADA | NO_SELECCIONADA ; RECHAZADA.
- adjudicacion: PENDIENTE_APROBACION → APROBADA | RECHAZADA (si no requiere aprobación, nace APROBADA).
- orden: EMITIDA → ACEPTADA_PROVEEDOR | RECHAZADA_PROVEEDOR ; → EN_PREPARACION → DESPACHADA → RECIBIDA_PARCIAL → RECIBIDA_TOTAL → CERRADA ; CANCELADA ; REEMPLAZADA (nueva versión).
- pago: nace REGISTRADO (PENDIENTE sin uso) → ANULADO (motivo, deja de sumar).
Cada transición: validar estado origen en el Service, registrar auditoría, notificar si aplica.

## Fuera de alcance
Ventas, multiempresa/SaaS, marketplace, pasarela de pagos, facturación DIAN, microservicios, ML propio/agentes autónomos.
