package com.proyecto.supply_core.venta.enums;

/** Estado de un pedido de venta armado por el vendedor (spec 004). */
public enum EstadoPedidoVenta {
    /** Espera que el cajero lo cobre. */
    PENDIENTE_COBRO,
    /** Ya se cobró: tiene su venta. */
    COBRADO,
    /** Se descartó sin cobrar. */
    DESCARTADO
}
