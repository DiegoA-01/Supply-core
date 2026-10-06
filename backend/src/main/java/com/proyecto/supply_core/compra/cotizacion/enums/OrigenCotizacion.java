package com.proyecto.supply_core.compra.cotizacion.enums;

/** Cómo llegó la cotización (ENUM de {@code cotizacion.origen}). */
public enum OrigenCotizacion {
    /** El proveedor digitó los precios en el portal. */
    FORMULARIO,
    /** El proveedor subió su documento (PDF, Excel o foto); la IA lo leerá en el paso 13. */
    ARCHIVO
}
