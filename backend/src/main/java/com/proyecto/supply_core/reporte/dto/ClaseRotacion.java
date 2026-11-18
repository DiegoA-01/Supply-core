package com.proyecto.supply_core.reporte.dto;

/**
 * Qué tan rápido se mueve un producto, según cuántos días le alcanza el stock actual al ritmo de salida del período.
 */
public enum ClaseRotacion {
    /** Se acaba pronto: el stock alcanza para pocos días al ritmo actual (rota rápido). */
    ALTA,
    /** Ritmo de salida normal frente al stock. */
    MEDIA,
    /** Hay mucho más stock del que se consume: rota lento. */
    BAJA,
    /** Hay stock pero no salió nada en el período: dinero parado. */
    SIN_MOVIMIENTO,
    /** Se vendió o consumió en el período y hoy no queda nada: urgente reponer. */
    AGOTADO,
    /** Sin stock y sin salidas en el período: no hay nada que analizar. */
    SIN_ACTIVIDAD
}
