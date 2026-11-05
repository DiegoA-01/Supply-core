package com.proyecto.supply_core.pago.enums;

/**
 * Estados de un pago (ENUM de {@code pago.estado}).
 * <pre>
 * REGISTRADO → ANULADO (con motivo; deja de sumar)
 * </pre>
 * PENDIENTE existe en el esquema pero no se usa: un pago nace REGISTRADO, y si no trae comprobante
 * queda con {@code evidencia_pendiente = 1}.
 */
public enum EstadoPago {
    /** Reservado en el esquema; no se usa. */
    PENDIENTE,
    /** Pago hecho y registrado; suma al pagado de la orden. */
    REGISTRADO,
    /** Registrado por error y anulado con motivo (final). No suma. */
    ANULADO
}
