# Reglas de negocio y casos de uso

Resumen fiel del documento funcional (`docs/proyecto supply-core.pdf`, no abrir).

## Roles (códigos en tabla `rol`)
SaaS: estos roles son un catálogo global; el admin de cada empresa los asigna a SUS usuarios (ver `saas.md`). ADMIN_SISTEMA = administrador de la empresa. CAJERO (V16) = registra ventas/entregas en caja. El dueño de la plataforma no es un rol: es `usuario_plataforma`.
ADMIN_SISTEMA (usuarios, seguridad, catálogos; no decide compras) · ADMIN_COMPRAS (políticas, categorías, proveedores habilitados) · SOLICITANTE · COMPRADOR (RFQ, análisis, orden) · APROBADOR (aprueba según límite) · ALMACENISTA (recepción, movimientos) · ADMINISTRATIVO (registra pago) · PROVEEDOR (portal: cotiza, confirma, despacha) · AUDITOR (solo lectura).
Segregación: no registra el pago quien solicitó, aprobó la solicitud, adjudicó, aprobó la adjudicación o emitió alguna versión de la orden (validado en `PagoService`).

## Flujo de punta a punta
Necesidad (stock < mínimo o manual) → Solicitud → Aprobación → RFQ → Proveedor cotiza (form o archivo) → IA extrae (confianza por dato) → Java valida/calcula/normaliza → IA explica ranking → Comprador decide → Aprobación de adjudicación si el monto lo exige → Orden de compra → Pago registrado → Proveedor confirma/despacha → Recepción → Stock sube → Evaluación del proveedor → Cierre.

## Casos de uso (actor · reglas clave · flujo alterno)
- **UC-01 Login** · todos · menú según roles · credenciales inválidas: mensaje genérico (no decir qué falló); cuenta inactiva: rechazar y "contacte al administrador".
- **UC-02 Catálogo** · ADMIN_COMPRAS · código único (si duplica, mostrar el existente) · desactivar ≠ borrar: no usable en nuevas operaciones, conserva historial.
- **UC-03 Movimiento inventario** · ALMACENISTA · producto y bodega activos · ajuste exige motivo · traslado exige origen ≠ destino · recalcula `stock_producto_bodega`, nunca negativo.
- **UC-04 Crear solicitud** · SOLICITANTE con centro de costo · producto, cantidad>0, unidad, fecha requerida, justificación · evalúa política → BORRADOR o PENDIENTE_APROBACION. Al enviar: si el centro de costo tiene presupuesto en el mes de la fecha requerida y el monto lo excede ⇒ 409 `PRESUPUESTO_EXCEDIDO`; si otra solicitud abierta del centro pide el mismo producto ⇒ 409 `SOLICITUD_DUPLICADA`. Con justificación se envía como excepción (el exceso pasa por la política de mayor tramo) y queda auditada (paso 18-a). Si hay un contrato vigente que cubra toda la solicitud aprobada, el comprador puede emitir una orden directa al precio pactado sin RFQ (paso 18-b): solo si cabe en el tope del contrato y no supera el monto aprobado; quien la emite no puede ser el solicitante. Aprobar (UC-05) puede hacerse por delegación vigente del rol aprobador, nunca de lo que pidió el delegado ni el delegante; si la solicitud pasa del SLA de la empresa, se avisa al 80 % y al vencer se escala a la política de mayor tramo (paso 18-c). Cuando un producto baja de su stock mínimo, el sistema sugiere cuánto pedir (máximo − stock − por recibir − ya solicitado) y puede dejar un borrador, pero nunca envía ni compra solo (paso 18-d).
- **UC-05 Aprobar solicitud** · APROBADOR asignado · APROBADA / RECHAZADA / DEVUELTA (vuelve al solicitante) con observaciones · notifica + audita.
- **UC-06 Crear RFQ** · COMPRADOR · requiere solicitud APROBADA · productos, cantidades, condiciones, fecha límite, proveedores · alerta si no hay proveedores habilitados para la categoría (`proveedor_categoria`) · al enviar: ABIERTA, solicitud → EN_RFQ.
- **UC-07 Cotizar** · PROVEEDOR invitado · RFQ ABIERTA y no vencida · formulario o archivo (Excel/PDF/imagen), se conserva el original · cotización → ENVIADA.
- **UC-08 Analizar (IA)** · COMPRADOR · ≥1 cotización ENVIADA · dato crítico con confianza baja ⇒ confirmación obligatoria antes de publicar · precio atípico = alerta, nunca exclusión · publica ranking con detalle por criterio.
- **UC-09 Adjudicar** · COMPRADOR/APROBADOR · requiere análisis PUBLICADO · justificación obligatoria si no sigue recomendación · monto > límite ⇒ PENDIENTE_APROBACION.
- **UC-10 Emitir orden** · COMPRADOR · requiere adjudicación APROBADA · proveedor, líneas, precios, lugar y fecha de entrega · inmutable; modificar = nueva versión con motivo (la anterior → REEMPLAZADA) · notifica al proveedor.
- **UC-11 Registrar pago** · ADMINISTRATIVO · orden emitida · método, valor, fecha, referencia y **tipo**: ANTICIPO (antes de recibir; la suma de anticipos no supera `empresa.tope_anticipo_pct` del total, 50 % por defecto) o PAGO_FINAL (orden recibida + factura CUADRADA o APROBADA; no supera lo que falta por pagar de la factura) · comprobante opcional: sin comprobante REGISTRADO + `evidencia_pendiente=1` · no hay pasarela ni contabilidad.
- **Factura y cruce de tres vías** · ADMINISTRATIVO registra la factura del proveedor de una orden con recepciones · el sistema calcula lo esperado (cantidad aceptada × precio de la orden + impuesto) y la compara con el total: CUADRADA si la diferencia cabe en la tolerancia de la empresa (`tolerancia_factura_pct` o `tolerancia_factura_monto`), si no EN_REVISION y ADMIN_COMPRAS decide (aprueba con motivo, nunca quien la registró) · anular con motivo si no hay pago final.
- **UC-12 Confirmar/despachar** · PROVEEDOR · ACEPTADA_PROVEEDOR → EN_PREPARACION → DESPACHADA (+evidencia) · rechazo ⇒ RECHAZADA_PROVEEDOR y notifica al comprador.
- **UC-13 Recepción** · ALMACENISTA · orden DESPACHADA o RECIBIDA_PARCIAL (varias entregas) · por línea: recibido, rechazado (motivo obligatorio: DANADO, NO_CUMPLE_ESPECIFICACION, VENCIDO, CANTIDAD_ERRADA, OTRO) y evidencia opcional (documento RECEPCION) · Java reparte: aceptada (lo bueno hasta completar el pedido, única que genera ENTRADA_COMPRA), excedente (línea EN_REVISION, no suma stock hasta que COMPRADOR/ADMIN_COMPRAS acepte o devuelva), faltante · lo rechazado nunca entra al stock y queda como devolución pendiente al proveedor · actualiza `costo_referencia` con el último precio de la orden · parcial ⇒ RECIBIDA_PARCIAL con saldo abierto · cierre (ALMACENISTA/ADMIN_COMPRAS): sin excedente pendiente; con faltante exige motivo (RECIBIDA_PARCIAL → CERRADA); al cerrar se evalúa al proveedor.
- **UC-14 Evaluar proveedor** · sistema al cerrar recepción/orden · puntualidad (vs `fecha_entrega_esperada`), cumplimiento, incidencias, calidad, precio, respuesta → score · sin datos ⇒ NULL, nunca valor por defecto · actualiza `proveedor.score_actual`.
- **UC-15 Dashboards** · AUDITOR/gerencia · gasto, inventario, proveedores; filtros fecha/categoría/proveedor · sin datos = estado vacío, no error · solo lectura · implementado: gasto (órdenes vigentes), inventario, ranking de proveedores y estado de cuenta por proveedor (`/api/reportes/*`).
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
Marketplace, pasarela para pagar a proveedores, microservicios, ML propio/agentes autónomos. Facturación DIAN y multimoneda: fases futuras (`saas.md`). Reglas por construir (presupuesto, contratos, delegación/SLA, reorden, calidad en recepción, tolerancia configurable): ver `saas.md` › "Reglas de negocio para competir". **Multiempresa/SaaS ya está dentro del alcance** (decisión 2026-10-23, ver `saas.md`); la pasarela solo cobrará la suscripción.
