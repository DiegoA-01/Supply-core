package com.proyecto.supply_core.compra.cotizacion.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

import com.proyecto.supply_core.compra.cotizacion.entity.Cotizacion;
import com.proyecto.supply_core.compra.cotizacion.entity.CotizacionLinea;

/**
 * Cálculos de dinero de una cotización. Solo Java calcula montos (regla del proyecto: la IA nunca).
 * <ul>
 *   <li>Neto de línea = cantidad × precio × (1 − descuento %), 2 decimales HALF_UP.</li>
 *   <li>Impuesto de línea = neto × impuesto %, 2 decimales HALF_UP.</li>
 *   <li>Precio normalizado = precio × (1 − descuento %) ÷ factor de conversión, 4 decimales: el precio
 *       de una unidad de la RFQ, para comparar proveedores que cotizan en presentaciones distintas.</li>
 *   <li>Cotización: subtotal = Σ netos, impuestos = Σ impuestos de línea, total = subtotal + impuestos.</li>
 * </ul>
 */
public final class CalculadoraCotizacion {

    /** Cien, para porcentajes. */
    private static final BigDecimal CIEN = BigDecimal.valueOf(100);

    /** Clase de utilidades: no se instancia. */
    private CalculadoraCotizacion() {
    }

    /**
     * Recalcula cada línea y los totales de la cotización. Sin líneas, los montos quedan {@code null}
     * (p. ej. una cotización por archivo que la IA aún no ha leído).
     *
     * @param c cotización con sus líneas
     */
    public static void recalcular(Cotizacion c) {
        if (c.getLineas().isEmpty()) {
            c.setSubtotal(null);
            c.setImpuestos(null);
            c.setTotal(null);
            return;
        }
        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal impuestos = BigDecimal.ZERO;
        for (CotizacionLinea l : c.getLineas()) {
            BigDecimal neto = neto(l.getCantidadOfertada(), l.getPrecioUnitarioOfertado(), l.getDescuentoPct());
            l.setSubtotal(neto);
            l.setPrecioUnitarioNormalizado(normalizado(l.getPrecioUnitarioOfertado(), l.getDescuentoPct(),
                    l.getFactorConversion()));
            subtotal = subtotal.add(neto);
            impuestos = impuestos.add(porcentaje(neto, l.getImpuestoPct()).setScale(2, RoundingMode.HALF_UP));
        }
        c.setSubtotal(subtotal);
        c.setImpuestos(impuestos);
        c.setTotal(subtotal.add(impuestos));
    }

    /**
     * Neto de una línea.
     *
     * @param cantidad  cantidad ofertada
     * @param precio    precio unitario ofertado
     * @param descuento descuento en % (0–100)
     * @return neto con 2 decimales
     */
    static BigDecimal neto(BigDecimal cantidad, BigDecimal precio, BigDecimal descuento) {
        BigDecimal bruto = cantidad.multiply(precio);
        return bruto.subtract(porcentaje(bruto, descuento)).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Precio de una unidad de la RFQ, con descuento y sin impuesto.
     *
     * @param precio    precio unitario ofertado
     * @param descuento descuento en %
     * @param factor    unidades de la RFQ por unidad ofertada (&gt; 0)
     * @return precio normalizado con 4 decimales
     */
    static BigDecimal normalizado(BigDecimal precio, BigDecimal descuento, BigDecimal factor) {
        BigDecimal conDescuento = precio.subtract(porcentaje(precio, descuento));
        return conDescuento.divide(factor, 4, RoundingMode.HALF_UP);
    }

    /**
     * Calcula un porcentaje de un valor.
     *
     * @param valor valor base
     * @param pct   porcentaje (0–100)
     * @return {@code valor × pct / 100} sin redondear la escala final
     */
    private static BigDecimal porcentaje(BigDecimal valor, BigDecimal pct) {
        return valor.multiply(pct).divide(CIEN, 6, RoundingMode.HALF_UP);
    }
}
