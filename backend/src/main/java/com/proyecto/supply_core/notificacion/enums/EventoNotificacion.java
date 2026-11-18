package com.proyecto.supply_core.notificacion.enums;

/**
 * Eventos que generan notificaciones. El nombre se guarda tal cual en
 * {@code notificacion.evento} y {@code config_notificacion.evento} (V7 siembra su configuración).
 * Al agregar un evento nuevo, sembrar también sus canales en una migración.
 */
public enum EventoNotificacion {
    /** Un proveedor se registró en el portal y espera aprobación (destino: ADMIN_COMPRAS). */
    PROVEEDOR_PENDIENTE("Proveedor pendiente de aprobación"),
    /** Un producto quedó por debajo de su stock mínimo (destino: COMPRADOR). */
    STOCK_BAJO_MINIMO("Producto bajo stock mínimo"),
    /** Una solicitud de compra espera aprobación (destino: el rol aprobador de la política). */
    SOLICITUD_PENDIENTE_APROBACION("Solicitud de compra pendiente de aprobación"),
    /** El aprobador decidió sobre una solicitud (destino: el solicitante). */
    SOLICITUD_DECIDIDA("Decisión sobre su solicitud de compra"),
    /** Una solicitud quedó APROBADA y puede pasar a RFQ (destino: COMPRADOR). */
    SOLICITUD_APROBADA("Solicitud de compra aprobada"),
    /** Un proveedor fue invitado a cotizar una RFQ abierta (destino: usuarios del proveedor). */
    RFQ_INVITACION("Invitación a cotizar"),
    /** Una RFQ se cerró (destino: comprador que la creó e invitados). */
    RFQ_CERRADA("Solicitud de cotización cerrada"),
    /** Una RFQ abierta se canceló (destino: invitados). */
    RFQ_CANCELADA("Solicitud de cotización cancelada"),
    /** Un proveedor envió su cotización (destino: comprador que creó la RFQ). */
    COTIZACION_RECIBIDA("Cotización recibida"),
    /** La IA leyó una cotización con dudas o falló; el comprador debe revisarla (destino: comprador de la RFQ). */
    EXTRACCION_REQUIERE_REVISION("Cotización por revisar"),
    /** Una adjudicación espera aprobación (destino: rol aprobador de la política). */
    ADJUDICACION_PENDIENTE_APROBACION("Adjudicación pendiente de aprobación"),
    /** Se aprobó o rechazó una adjudicación (destino: comprador que adjudicó). */
    ADJUDICACION_DECIDIDA("Decisión sobre la adjudicación"),
    /** La cotización del proveedor ganó (destino: usuarios del proveedor). */
    COTIZACION_SELECCIONADA("Su cotización fue seleccionada"),
    /** Se emitió una orden de compra (destino: usuarios del proveedor). */
    ORDEN_EMITIDA("Nueva orden de compra"),
    /** La orden cambió de versión o se canceló (destino: usuarios del proveedor). */
    ORDEN_ACTUALIZADA("Orden de compra actualizada"),
    /** El proveedor aceptó, rechazó, preparó o despachó (destino: comprador que emitió). */
    ORDEN_RESPUESTA_PROVEEDOR("Respuesta del proveedor a la orden"),
    /** La orden fue despachada y llegará a bodega (destino: ALMACENISTA). */
    ORDEN_DESPACHADA("Mercancía despachada"),
    /** Se registró un pago de la orden (destino: usuarios del proveedor y comprador que emitió). */
    PAGO_REGISTRADO("Pago registrado"),
    /** Se anuló un pago de la orden (destino: usuarios del proveedor y comprador que emitió). */
    PAGO_ANULADO("Pago anulado"),
    /** Se recibió mercancía de la orden, con sus rechazos y faltantes (destino: proveedor y comprador que emitió). */
    RECEPCION_REGISTRADA("Mercancía recibida"),
    /** Llegó más de lo pedido y espera decisión (destino: COMPRADOR). */
    RECEPCION_EXCEDENTE("Mercancía de más por resolver"),
    /** Se cerró la recepción de la orden y se evaluó al proveedor (destino: proveedor y comprador que emitió). */
    RECEPCION_CERRADA("Recepción cerrada"),
    /** Una factura no cuadra con lo recibido y espera la decisión de compras (destino: ADMIN_COMPRAS). */
    FACTURA_EN_REVISION("Factura por revisar"),
    /** Compras aprobó o anularon una factura (destino: el administrativo que la registró). */
    FACTURA_DECIDIDA("Decisión sobre la factura"),
    /** Una solicitud pendiente va por el 80 % del tiempo para decidirse. */
    SOLICITUD_SLA_POR_VENCER("Solicitud por vencer en aprobación"),
    /** Una solicitud pendiente venció su tiempo y pasó a la política de mayor tramo. */
    SOLICITUD_ESCALADA("Solicitud escalada"),
    /** Un aprobador le delegó sus decisiones. */
    DELEGACION_ASIGNADA("Delegación de aprobación asignada"),
    /** El administrador de la empresa le pidió un informe de compras al área de compras. */
    INFORME_PEDIDO("Informe de compras pedido"),
    /** El área de compras envió un informe de compras al administrador de la empresa. */
    INFORME_RECIBIDO("Informe de compras recibido");

    /** Asunto del correo. */
    private final String asunto;

    /**
     * @param asunto asunto del correo
     */
    EventoNotificacion(String asunto) {
        this.asunto = asunto;
    }

    /**
     * Asunto legible para el correo.
     *
     * @return asunto
     */
    public String asunto() {
        return asunto;
    }
}
