package com.proyecto.supply_core.venta.dto;

import java.math.BigDecimal;

/**
 * Línea de una venta como quedó (sin el costo: eso es del dueño).
 *
 * @param productoId        producto
 * @param codigo            código del producto
 * @param nombre            nombre del producto
 * @param unidad            código de la unidad vendida
 * @param cantidad          cantidad vendida
 * @param precioLista       precio de lista
 * @param precioUnitario    precio con descuento
 * @param descuentoPct      descuento en porcentaje
 * @param descuentoValor    valor del descuento
 * @param tarifaImpuestoPct tarifa de impuesto
 * @param baseGravable      base
 * @param impuesto          impuesto
 * @param totalLinea        total de la línea
 * @param autorizadoPor     supervisor que autorizó el descuento o el precio ({@code null} si no hizo falta)
 * @param id                id de la línea (para devolverla)
 * @param cantidadDevuelta  cantidad ya devuelta por el cliente
 */
public record VentaLineaResponse(Long productoId, String codigo, String nombre, String unidad, BigDecimal cantidad,
        BigDecimal precioLista, BigDecimal precioUnitario, BigDecimal descuentoPct, BigDecimal descuentoValor,
        BigDecimal tarifaImpuestoPct, BigDecimal baseGravable, BigDecimal impuesto, BigDecimal totalLinea,
        String autorizadoPor, Long id, BigDecimal cantidadDevuelta) {
}
