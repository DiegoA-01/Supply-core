package com.proyecto.supply_core.venta.enums;

import com.proyecto.supply_core.caja.enums.MedioPago;

/** Medio con que se paga una venta. {@link #CREDITO} se rechaza hasta la spec 007 (decisión D4). */
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
    /** Venta a crédito del cliente (spec 007). */
    CREDITO;

    /**
     * @return {@code true} si es una tarjeta (guarda últimos 4 dígitos y autorización)
     */
    public boolean esTarjeta() {
        return this == TARJETA_DEBITO || this == TARJETA_CREDITO;
    }

    /**
     * @return medio del libro de caja que le corresponde
     * @throws IllegalStateException si es crédito (no entra dinero a la caja)
     */
    public MedioPago enCaja() {
        return switch (this) {
            case EFECTIVO -> MedioPago.EFECTIVO;
            case TARJETA_DEBITO -> MedioPago.TARJETA_DEBITO;
            case TARJETA_CREDITO -> MedioPago.TARJETA_CREDITO;
            case TRANSFERENCIA -> MedioPago.TRANSFERENCIA;
            case BILLETERA -> MedioPago.BILLETERA;
            case CREDITO -> throw new IllegalStateException("El crédito no entra a la caja");
        };
    }
}
