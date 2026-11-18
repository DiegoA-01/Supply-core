package com.proyecto.supply_core.contrato.enums;

/**
 * Estados de un contrato (ENUM de {@code contrato_proveedor.estado}).
 * <p>Un contrato ACTIVO solo se puede usar mientras hoy esté dentro de su vigencia: el vencimiento no cambia el
 * estado, se calcula con las fechas. ANULADO es final.</p>
 */
public enum EstadoContrato {
    /** Se puede usar si hoy está dentro de su vigencia. */
    ACTIVO,
    /** Anulado con motivo; no se usa más. Las órdenes ya emitidas no cambian. */
    ANULADO
}
