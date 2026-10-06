package com.proyecto.supply_core.compra.analisis.enums;

/** Estado de un análisis comparativo (ENUM de {@code analisis_cotizacion.estado}). */
public enum EstadoAnalisis {
    /** Calculado; se puede regenerar o corregir lecturas antes de publicar. */
    BORRADOR,
    /** Ranking fijo; habilita la adjudicación. */
    PUBLICADO
}
