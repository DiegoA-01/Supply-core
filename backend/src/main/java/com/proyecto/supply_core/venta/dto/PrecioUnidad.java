package com.proyecto.supply_core.venta.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Precio de un producto en una unidad distinta de la suya (por ejemplo la caja de 24 de un producto que se cuenta por
 * unidad), con su margen calculado en Java contra el costo convertido a esa unidad (decisión D9).
 *
 * @param unidadMedidaId unidad del precio
 * @param unidad         código de la unidad
 * @param precio         precio vigente
 * @param precioMinimo   piso vigente o {@code null}
 * @param vigenteDesde   desde cuándo rige
 * @param base           precio sin impuesto
 * @param margenPct      margen sobre la base en %, o {@code null} si el producto no tiene costo
 */
public record PrecioUnidad(Long unidadMedidaId, String unidad, BigDecimal precio, BigDecimal precioMinimo,
        LocalDateTime vigenteDesde, BigDecimal base, BigDecimal margenPct) {
}
