package com.proyecto.supply_core.reorden.dto;

import java.math.BigDecimal;

/**
 * Cantidad sumada por producto (resultado de las consultas de lo que ya viene en camino).
 *
 * @param productoId producto
 * @param cantidad   suma de las cantidades, en la unidad del producto
 */
public record CantidadPorProducto(Long productoId, BigDecimal cantidad) {
}
