package com.proyecto.supply_core.pago.enums;

/** Medio con que se pagó ({@code pago.metodo}). Solo se registra; el sistema no mueve dinero. */
public enum MetodoPago {
    /** Transferencia bancaria. */
    TRANSFERENCIA,
    /** Consignación en la cuenta del proveedor. */
    CONSIGNACION,
    /** Cheque. */
    CHEQUE,
    /** Efectivo. */
    EFECTIVO,
    /** Tarjeta corporativa. */
    TARJETA,
    /** Otro medio (detallar en la referencia). */
    OTRO
}
