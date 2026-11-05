package com.proyecto.supply_core.reporte.dto;

import java.math.BigDecimal;

/**
 * Fila agregada que devuelve la base de datos al agrupar el gasto por proveedor o por categoría.
 *
 * @param id        proveedor o categoría
 * @param nombre    razón social o nombre de la categoría
 * @param ordenes   órdenes distintas del grupo
 * @param subtotal  suma de los subtotales de línea (sin impuesto)
 * @param impuestos suma del impuesto de las líneas (sin redondear)
 */
public record GastoFila(Long id, String nombre, Long ordenes, BigDecimal subtotal, BigDecimal impuestos) {
}
