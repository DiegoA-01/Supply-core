package com.proyecto.supply_core.venta.enums;

import com.proyecto.supply_core.caja.enums.MedioPago;

/** Medio con que se paga una venta. El crédito y el saldo a favor no entran a la caja (spec 007). */
public enum MedioPagoVenta {
    /** Efectivo: el único que puede pasarse del total; el exceso es el cambio. */
    EFECTIVO,
    /** Tarjeta débito. */
    TARJETA_DEBITO,
    /** Tarjeta crédito. */
    TARJETA_CREDITO,
    /** Transferencia bancaria. */
    TRANSFERENCIA,
    /** Billetera digital (Nequi, Daviplata...). */
    BILLETERA,
    /** Venta a crédito del cliente: crea una cuenta por cobrar (spec 007). */
    CREDITO,
    /** Saldo a favor del cliente, que sale de sus devoluciones (spec 007). */
    SALDO_FAVOR;

    /**
     * @return {@code true} si es una tarjeta (guarda últimos 4 dígitos y autorización)
     */
    public boolean esTarjeta() {
        return this == TARJETA_DEBITO || this == TARJETA_CREDITO;
    }

    /**
     * @return {@code true} si el dinero entra a la caja del turno
     */
    public boolean entraACaja() {
        return this != CREDITO && this != SALDO_FAVOR;
    }

    /**
     * @return medio del libro de caja que le corresponde
     * @throws IllegalStateException si es crédito o saldo a favor (no entra dinero a la caja)
     */
    public MedioPago enCaja() {
        return switch (this) {
            case EFECTIVO -> MedioPago.EFECTIVO;
            case TARJETA_DEBITO -> MedioPago.TARJETA_DEBITO;
            case TARJETA_CREDITO -> MedioPago.TARJETA_CREDITO;
            case TRANSFERENCIA -> MedioPago.TRANSFERENCIA;
            case BILLETERA -> MedioPago.BILLETERA;
            case CREDITO, SALDO_FAVOR -> throw new IllegalStateException("El crédito y el saldo a favor no entran a la caja");
        };
    }
}
