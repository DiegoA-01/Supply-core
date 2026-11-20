package com.proyecto.supply_core.venta.enums;

/** Cómo se le devuelve el dinero al cliente en una devolución (spec 006). */
public enum FormaReembolso {
    /** Efectivo del turno en curso. */
    EFECTIVO,
    /** Por el mismo medio con que pagó (solo si la venta se pagó con uno). */
    MISMO_MEDIO,
    /** Queda como saldo a favor del cliente (se consume con la cartera, spec 007). */
    SALDO_A_FAVOR
}
