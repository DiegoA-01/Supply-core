package com.proyecto.supply_core.venta.dto;

import java.math.BigDecimal;

/**
 * Línea de un pedido de venta.
 *
 * @param productoId     producto
 * @param codigo         código del producto
 * @param nombre         nombre del producto
 * @param unidadMedidaId unidad
 * @param unidad         código de la unidad
 * @param cantidad       cantidad
 * @param descuentoPct   descuento pedido
 */
public record PedidoVentaLineaResponse(Long productoId, String codigo, String nombre, Long unidadMedidaId,
        String unidad, BigDecimal cantidad, BigDecimal descuentoPct) {
}
