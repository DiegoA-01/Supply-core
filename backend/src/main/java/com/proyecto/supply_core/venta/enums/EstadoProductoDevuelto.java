package com.proyecto.supply_core.venta.enums;

/** Estado en que vuelve un producto devuelto (spec 006). */
public enum EstadoProductoDevuelto {
    /** Vuelve a la bodega de venta. */
    BUENO,
    /** Va a la bodega de averías, o no reingresa si la empresa no tiene una. */
    DANADO
}
