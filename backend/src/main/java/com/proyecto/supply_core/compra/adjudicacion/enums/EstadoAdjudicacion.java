package com.proyecto.supply_core.compra.adjudicacion.enums;

/**
 * Estados de una adjudicación (ENUM de {@code adjudicacion.estado}).
 * <pre>
 * PENDIENTE_APROBACION → APROBADA | RECHAZADA
 * (si ninguna política exige aprobación, nace APROBADA)
 * </pre>
 */
public enum EstadoAdjudicacion {
    /** Espera la decisión del rol aprobador de la política ADJUDICACION. */
    PENDIENTE_APROBACION,
    /** Vigente: habilita la orden de compra (paso 15). */
    APROBADA,
    /** Rechazada: la RFQ sigue CERRADA y se puede adjudicar de nuevo. */
    RECHAZADA
}
