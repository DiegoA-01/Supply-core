package com.proyecto.supply_core.reporte.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Inventario de los productos activos con su stock total.
 *
 * @param productos  productos en la lista
 * @param bajoMinimo productos con stock menor al mínimo
 * @param valorTotal suma de stock × costo de referencia de los productos que tienen costo
 * @param items      productos por nombre
 */
public record InventarioResumen(long productos, long bajoMinimo, BigDecimal valorTotal, List<InventarioItem> items) {
}
