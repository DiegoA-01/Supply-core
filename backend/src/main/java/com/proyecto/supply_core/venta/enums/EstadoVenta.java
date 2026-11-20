package com.proyecto.supply_core.venta.enums;

/** Estado de una venta (spec 004). Una venta confirmada no se edita; solo la anulación (spec 006) la cambia. */
public enum EstadoVenta {
    /** Cobrada: descontó el stock y registró el dinero en la caja. */
    CONFIRMADA,
    /** Anulada en el mismo turno: el stock y el dinero volvieron (spec 006). */
    ANULADA
}
