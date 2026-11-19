package com.proyecto.supply_core.venta.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Un producto en una lista de precios, con su margen calculado en Java.
 *
 * @param productoId           producto
 * @param codigo               código
 * @param nombre               nombre
 * @param categoria            categoría
 * @param unidad               unidad del precio
 * @param vendible             si se vende
 * @param codigoBarras         código de barras o {@code null}
 * @param tarifaImpuestoId     impuesto de venta o {@code null}
 * @param impuesto             nombre del impuesto o {@code null}
 * @param porcentajeImpuesto   tarifa o {@code null}
 * @param precioIncluyeImpuesto si el precio trae el impuesto
 * @param costo                costo de referencia (último precio de compra) o {@code null}
 * @param precio               precio vigente o {@code null} si no tiene
 * @param precioMinimo         piso vigente o {@code null}
 * @param vigenteDesde         desde cuándo rige el precio
 * @param base                 precio sin impuesto
 * @param valorImpuesto        impuesto dentro del precio
 * @param margen               base − costo
 * @param margenPct            margen sobre la base en %
 * @param stock                stock total
 * @param alertas              SIN_PRECIO, SIN_COSTO, SIN_IMPUESTO, BAJO_COSTO, MINIMO_BAJO_COSTO
 * @param otrasUnidades        precios vigentes del producto en otras unidades (por ejemplo la caja), decisión D9
 */
public record PrecioFila(Long productoId, String codigo, String nombre, String categoria, String unidad,
        boolean vendible, String codigoBarras, Long tarifaImpuestoId, String impuesto, BigDecimal porcentajeImpuesto,
        boolean precioIncluyeImpuesto, BigDecimal costo, BigDecimal precio, BigDecimal precioMinimo,
        LocalDateTime vigenteDesde, BigDecimal base, BigDecimal valorImpuesto, BigDecimal margen, BigDecimal margenPct,
        BigDecimal stock, List<String> alertas, List<PrecioUnidad> otrasUnidades) {
}
