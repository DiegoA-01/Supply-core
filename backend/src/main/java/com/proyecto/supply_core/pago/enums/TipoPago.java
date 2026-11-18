package com.proyecto.supply_core.pago.enums;

/** Tipo de un pago (ENUM de {@code pago.tipo}). */
public enum TipoPago {
    /** Pago adelantado, antes de recibir: la suma de anticipos tiene un tope (% del total de la orden). */
    ANTICIPO,
    /** Pago contra factura, después de recibir: exige una factura que cuadre con lo recibido y descuenta lo ya pagado. */
    PAGO_FINAL
}
