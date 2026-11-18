package com.proyecto.supply_core.despacho.enums;

/**
 * Estados de un despacho (ENUM de {@code despacho.estado}).
 * <pre>
 * BORRADOR → CONFIRMADO (descuenta el stock) → ANULADO (lo devuelve)
 * BORRADOR → ANULADO (sin tocar el stock)
 * </pre>
 */
public enum EstadoDespacho {
    /** Se está armando; no toca el stock y se puede editar. */
    BORRADOR,
    /** El stock ya se descontó. Solo se puede anular. */
    CONFIRMADO,
    /** Anulado con motivo (final). */
    ANULADO
}
