package com.proyecto.supply_core.recepcion.enums;

/** Estado de una línea de recepción (ENUM de {@code recepcion_linea.estado}). */
public enum EstadoRecepcionLinea {
    /** Todo lo bueno recibido está dentro de lo pedido y ya sumó al stock. */
    ACEPTADA,
    /** Llegó más de lo pedido: el excedente no suma stock hasta que compras decida. */
    EN_REVISION,
    /** Compras decidió devolver el excedente al proveedor (no entró al stock). */
    EXCEDENTE_DEVUELTO
}
