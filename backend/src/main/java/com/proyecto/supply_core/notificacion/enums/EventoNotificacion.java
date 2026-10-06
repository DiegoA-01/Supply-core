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
    COTIZACION_RECIBIDA("Cotización recibida");

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
