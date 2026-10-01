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
    STOCK_BAJO_MINIMO("Producto bajo stock mínimo");

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
