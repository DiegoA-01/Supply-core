package com.proyecto.supply_core.cartera.enums;

/** Estado de una cuenta por cobrar (spec 007). */
public enum EstadoCuenta {
    /** Tiene saldo por pagar. */
    ABIERTA,
    /** Se pagó completa. */
    PAGADA,
    /** Su venta se anuló antes de recibir abonos. */
    ANULADA
}
