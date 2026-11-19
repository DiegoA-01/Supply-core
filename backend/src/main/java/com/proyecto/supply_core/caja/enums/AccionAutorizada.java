package com.proyecto.supply_core.caja.enums;

/** Acción del cajero que necesita la autorización de un supervisor con su PIN. */
public enum AccionAutorizada {
    /** Anular una venta del turno abierto (spec 006). */
    ANULAR_VENTA,
    /** Descuento por encima del límite del rol (spec 004). */
    DESCUENTO,
    /** Vender por debajo del precio mínimo (spec 004). */
    PRECIO_BAJO_MINIMO,
    /** Devolución de cliente (spec 006). */
    DEVOLUCION,
    /** Retiro de efectivo a la bóveda. */
    RETIRO,
    /** Gasto menor. */
    GASTO,
    /** Ingreso de efectivo que no es venta. */
    INGRESO
}
