package com.proyecto.supply_core.caja.enums;

/** Medio de pago de un movimiento de caja. El crédito no pasa por la caja: es cartera. */
public enum MedioPago {
    /** Billetes y monedas. */
    EFECTIVO,
    /** Tarjeta débito (se registra, no se procesa). */
    TARJETA_DEBITO,
    /** Tarjeta crédito (se registra, no se procesa). */
    TARJETA_CREDITO,
    /** Transferencia bancaria. */
    TRANSFERENCIA,
    /** Billetera digital (Nequi, Daviplata…). */
    BILLETERA
}
