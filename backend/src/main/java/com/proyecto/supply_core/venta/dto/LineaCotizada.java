package com.proyecto.supply_core.venta.dto;

import java.math.BigDecimal;

/**
 * Línea del carrito con el precio del día y sus montos.
 *
 * @param productoId        producto
 * @param codigo            código del producto
 * @param nombre            nombre del producto
 * @param unidadMedidaId    unidad
 * @param unidad            código de la unidad
 * @param cantidad          cantidad
 * @param precioLista       precio de lista
 * @param precioUnitario    precio con descuento
 * @param descuentoPct      descuento pedido
 * @param descuentoValor    valor del descuento
 * @param tarifaImpuestoPct tarifa de impuesto
 * @param totalLinea        total de la línea
 * @param pasaLimite        {@code true} si el descuento pasa el límite del rol
 * @param bajoMinimo        {@code true} si el precio queda bajo el mínimo
 */
public record LineaCotizada(Long productoId, String codigo, String nombre, Long unidadMedidaId, String unidad,
        BigDecimal cantidad, BigDecimal precioLista, BigDecimal precioUnitario, BigDecimal descuentoPct,
        BigDecimal descuentoValor, BigDecimal tarifaImpuestoPct, BigDecimal totalLinea, boolean pasaLimite,
        boolean bajoMinimo) {
}
