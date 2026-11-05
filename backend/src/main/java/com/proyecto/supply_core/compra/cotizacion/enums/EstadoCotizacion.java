package com.proyecto.supply_core.compra.cotizacion.enums;

/**
 * Estados de una cotización (ENUM de {@code cotizacion.estado}).
 * <pre>
 * BORRADOR → ENVIADA → EN_ANALISIS → SELECCIONADA | NO_SELECCIONADA
 * ENVIADA | EN_ANALISIS → RECHAZADA (inválida)
 * </pre>
 * Solo BORRADOR es editable por el proveedor; los demás estados los maneja compras (pasos 13–14).
 */
public enum EstadoCotizacion {
    /** El proveedor la está preparando; nadie más la ve. */
    BORRADOR,
    /** Enviada al comprador; ya no se edita. */
    ENVIADA,
    /** En comparación (paso 13). */
    EN_ANALISIS,
    /** Ganó la adjudicación (paso 14). */
    SELECCIONADA,
    /** No ganó. */
    NO_SELECCIONADA,
    /** Descartada por inválida. */
    RECHAZADA
}
