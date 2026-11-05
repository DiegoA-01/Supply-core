package com.proyecto.supply_core.inventario.dto;

import java.math.BigDecimal;

/**
 * Resultado de registrar un movimiento.
 *
 * @param movimiento         movimiento registrado
 * @param stockTotalProducto stock total del producto en todas las bodegas tras el movimiento
 * @param bajoStockMinimo    {@code true} si el producto quedó por debajo de su mínimo (alerta de reposición)
 */
public record RegistroMovimientoResponse(MovimientoResponse movimiento, BigDecimal stockTotalProducto,
        boolean bajoStockMinimo) {
}
