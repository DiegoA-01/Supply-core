package com.proyecto.supply_core.caja.enums;

/** Tipo de un movimiento del libro de caja. Los que entran suman; los que salen se guardan en negativo. */
public enum TipoMovimientoCaja {
    /** Efectivo con el que se abre el turno. */
    BASE,
    /** Cobro de una venta (spec 004). */
    VENTA,
    /** Reembolso de una devolución de cliente (spec 006). */
    DEVOLUCION,
    /** Reversa de una venta anulada en el turno (spec 006). */
    ANULACION,
    /** Efectivo que sale a la bóveda. */
    RETIRO,
    /** Gasto menor pagado con el efectivo de la caja. */
    GASTO,
    /** Efectivo que entra a la caja sin ser venta (por ejemplo, sencillo). */
    INGRESO,
    /** Abono de un cliente a su cartera (spec 007). */
    ABONO_CARTERA
}
