package com.proyecto.supply_core.inventario.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Costo promedio ponderado (spec 008), sin base de datos para poder probarlo solo. */
public final class CalculoCosto {

    /** Decimales del costo unitario. */
    static final int ESCALA = 4;

    /** No se instancia: solo métodos estáticos. */
    private CalculoCosto() {
    }

    /**
     * (existencia × costo anterior + cantidad × costo de compra) ÷ (existencia + cantidad). Si no había existencia o no
     * había costo, el nuevo costo es el de la compra.
     *
     * @param existencia   existencia total antes de la entrada (unidad del producto)
     * @param costoAnterior costo promedio antes de la entrada ({@code null} si no tenía)
     * @param cantidad     cantidad que entra (> 0)
     * @param costoCompra  costo unitario de la compra
     * @return nuevo costo promedio con 4 decimales
     */
    public static BigDecimal promedio(BigDecimal existencia, BigDecimal costoAnterior, BigDecimal cantidad,
            BigDecimal costoCompra) {
        if (existencia == null || existencia.signum() <= 0 || costoAnterior == null) {
            return costoCompra.setScale(ESCALA, RoundingMode.HALF_UP);
        }
        BigDecimal valor = existencia.multiply(costoAnterior).add(cantidad.multiply(costoCompra));
        return valor.divide(existencia.add(cantidad), ESCALA, RoundingMode.HALF_UP);
    }
}
