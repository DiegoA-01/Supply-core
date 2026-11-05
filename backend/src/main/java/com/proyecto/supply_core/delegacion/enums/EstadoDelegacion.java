package com.proyecto.supply_core.delegacion.enums;

/**
 * Estados de una delegación (ENUM de {@code delegacion_aprobacion.estado}).
 * <p>El vencimiento no cambia el estado: una delegación ACTIVA solo sirve mientras hoy esté dentro de sus
 * fechas. REVOCADA es final.</p>
 */
public enum EstadoDelegacion {
    /** Se puede usar si hoy está dentro de sus fechas. */
    ACTIVA,
    /** La canceló el delegante o compras antes de tiempo. */
    REVOCADA
}
