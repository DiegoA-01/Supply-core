package com.proyecto.supply_core.compra.rfq.enums;

/**
 * Estados de una RFQ (ENUM de {@code rfq.estado}).
 * <pre>
 * BORRADOR → ABIERTA → CERRADA (fecha límite o manual) → ADJUDICADA
 * BORRADOR | ABIERTA → CANCELADA
 * </pre>
 */
public enum EstadoRfq {
    /** Creada por el comprador; aún no la ven los proveedores. */
    BORRADOR,
    /** Enviada a los invitados; reciben cotizaciones hasta la fecha límite. */
    ABIERTA,
    /** Ya no recibe cotizaciones; lista para el análisis. */
    CERRADA,
    /** Se adjudicó (paso 14). */
    ADJUDICADA,
    /** Cancelada (final). */
    CANCELADA;

    /**
     * Indica si se puede cancelar.
     *
     * @return {@code true} en BORRADOR o ABIERTA
     */
    public boolean esCancelable() {
        return this == BORRADOR || this == ABIERTA;
    }

    /**
     * Indica si el proveedor puede verla en su portal.
     *
     * @return {@code false} solo para BORRADOR
     */
    public boolean visibleParaProveedor() {
        return this != BORRADOR;
    }
}
