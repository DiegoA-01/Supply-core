package com.proyecto.supply_core.reporte.dto;

import java.math.BigDecimal;

/**
 * Fila agregada que devuelve la base de datos al agrupar el gasto por mes de emisión de la orden.
 *
 * @param anio      año
 * @param mes       mes (1 a 12)
 * @param ordenes   órdenes distintas del mes
 * @param subtotal  suma de los subtotales de línea (sin impuesto)
 * @param impuestos suma del impuesto de las líneas (sin redondear)
 */
public record GastoMesFila(Integer anio, Integer mes, Long ordenes, BigDecimal subtotal, BigDecimal impuestos) {
}
