package com.proyecto.supply_core.venta.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Cálculo del margen de un precio de venta (regla de oro 1: el dinero lo calcula Java). El precio de mostrador suele
 * incluir el IVA: aquí se separa la base y el impuesto, y el margen se mide sobre la base, que es lo que queda para el
 * negocio.
 * <pre>
 * base       = precio ÷ (1 + %impuesto)   si el precio incluye el impuesto; si no, base = precio
 * margen     = base − costo
 * margen %   = margen ÷ base × 100
 * </pre>
 */
public final class CalculoMargen {

    private static final BigDecimal CIEN = BigDecimal.valueOf(100);

    private CalculoMargen() {
    }

    /**
     * Resultado del cálculo.
     *
     * @param base       precio sin impuesto
     * @param impuesto   valor del impuesto dentro del precio (o sumado aparte)
     * @param margen     base − costo ({@code null} si no hay costo)
     * @param margenPct  margen sobre la base, en % con un decimal ({@code null} si no hay costo)
     */
    public record Resultado(BigDecimal base, BigDecimal impuesto, BigDecimal margen, BigDecimal margenPct) {
    }

    /**
     * @param precio            precio de venta
     * @param incluyeImpuesto   {@code true} si el precio ya trae el impuesto
     * @param porcentajeImpuesto tarifa (19, 5, 0…); {@code null} se toma como 0
     * @param costo             costo de referencia; {@code null} si no se conoce
     * @return base, impuesto y margen
     */
    public static Resultado calcular(BigDecimal precio, boolean incluyeImpuesto, BigDecimal porcentajeImpuesto,
            BigDecimal costo) {
        BigDecimal pct = porcentajeImpuesto == null ? BigDecimal.ZERO : porcentajeImpuesto;
        BigDecimal base = incluyeImpuesto
                ? precio.multiply(CIEN).divide(CIEN.add(pct), 2, RoundingMode.HALF_UP)
                : precio.setScale(2, RoundingMode.HALF_UP);
        BigDecimal impuesto = incluyeImpuesto ? precio.subtract(base).setScale(2, RoundingMode.HALF_UP)
                : base.multiply(pct).divide(CIEN, 2, RoundingMode.HALF_UP);
        if (costo == null) {
            return new Resultado(base, impuesto, null, null);
        }
        BigDecimal margen = base.subtract(costo).setScale(2, RoundingMode.HALF_UP);
        BigDecimal pctMargen = base.signum() == 0 ? null
                : margen.multiply(CIEN).divide(base, 1, RoundingMode.HALF_UP);
        return new Resultado(base, impuesto, margen, pctMargen);
    }
}
