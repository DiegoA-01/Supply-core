package com.proyecto.supply_core.factura.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import com.proyecto.supply_core.factura.enums.EstadoFactura;

/**
 * Cruce de tres vías en Java puro (orden, recepción y factura): calcula lo que el proveedor debería facturar
 * según lo aceptado en la recepción y decide si la factura cuadra dentro de la tolerancia de la empresa.
 * La IA no interviene: el dinero lo calcula siempre el sistema.
 */
public final class CalculadoraFactura {

    private static final BigDecimal CIEN = BigDecimal.valueOf(100);

    /** Clase de cálculo: no se instancia. */
    private CalculadoraFactura() {
    }

    /**
     * Una línea de la orden con lo que se aceptó de ella en la recepción.
     *
     * @param aceptado    cantidad aceptada (unidad del producto)
     * @param precio      precio unitario de la orden, sin impuesto
     * @param impuestoPct porcentaje de impuesto de la línea (0 a 100)
     */
    public record LineaCruce(BigDecimal aceptado, BigDecimal precio, BigDecimal impuestoPct) {
    }

    /**
     * Lo que debería facturar el proveedor.
     *
     * @param subtotal  neto
     * @param impuestos impuestos
     * @param total     total
     */
    public record Esperado(BigDecimal subtotal, BigDecimal impuestos, BigDecimal total) {
    }

    /**
     * Resultado de comparar la factura con lo esperado.
     *
     * @param estado     CUADRADA si está dentro de la tolerancia, EN_REVISION si no
     * @param diferencia total facturado menos total esperado (positivo = facturó de más)
     */
    public record Cruce(EstadoFactura estado, BigDecimal diferencia) {
    }

    /**
     * Calcula neto, impuestos y total esperados: por línea, cantidad aceptada por precio (2 decimales) más su
     * impuesto (2 decimales, mitad hacia arriba).
     *
     * @param lineas líneas de la orden con lo aceptado
     * @return montos esperados
     */
    public static Esperado esperado(List<LineaCruce> lineas) {
        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal impuestos = BigDecimal.ZERO;
        for (LineaCruce l : lineas) {
            BigDecimal neto = l.aceptado().multiply(l.precio()).setScale(2, RoundingMode.HALF_UP);
            BigDecimal impuesto = neto.multiply(l.impuestoPct()).divide(CIEN, 2, RoundingMode.HALF_UP);
            subtotal = subtotal.add(neto);
            impuestos = impuestos.add(impuesto);
        }
        return new Esperado(subtotal, impuestos, subtotal.add(impuestos));
    }

    /**
     * Compara el total facturado con el esperado. Cuadra si la diferencia absoluta no supera la tolerancia en
     * monto fijo, o si no supera el porcentaje de tolerancia del total esperado. Sin nada esperado (no se
     * aceptó mercancía) nunca cuadra.
     *
     * @param totalFactura   total de la factura
     * @param esperadoTotal  total esperado según la recepción
     * @param toleranciaPct  tolerancia en porcentaje del total esperado (0 a 100)
     * @param toleranciaMonto tolerancia en monto fijo (0 o más)
     * @return estado y diferencia
     */
    public static Cruce evaluar(BigDecimal totalFactura, BigDecimal esperadoTotal, BigDecimal toleranciaPct,
            BigDecimal toleranciaMonto) {
        BigDecimal diferencia = totalFactura.subtract(esperadoTotal);
        BigDecimal absoluta = diferencia.abs();
        boolean dentroMonto = absoluta.compareTo(toleranciaMonto) <= 0;
        boolean dentroPct = esperadoTotal.signum() > 0
                && absoluta.multiply(CIEN).compareTo(esperadoTotal.multiply(toleranciaPct)) <= 0;
        boolean cuadra = esperadoTotal.signum() > 0 && (dentroMonto || dentroPct);
        return new Cruce(cuadra ? EstadoFactura.CUADRADA : EstadoFactura.EN_REVISION, diferencia);
    }
}
