package com.proyecto.supply_core.reporte.dto;

import java.math.BigDecimal;

import com.proyecto.supply_core.inventario.enums.TipoMovimiento;

/**
 * Fila agregada que devuelve la base de datos: lo movido de un producto por tipo de movimiento en el período.
 *
 * @param productoId producto
 * @param tipo       tipo de movimiento
 * @param cantidad   suma de las cantidades de ese tipo
 */
public record RotacionMovimiento(Long productoId, TipoMovimiento tipo, BigDecimal cantidad) {
}
