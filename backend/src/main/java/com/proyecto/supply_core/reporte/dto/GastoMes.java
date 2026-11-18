package com.proyecto.supply_core.reporte.dto;

import java.math.BigDecimal;

/**
 * Gasto de un mes.
 *
 * @param periodo   año y mes, por ejemplo {@code 2026-10}
 * @param ordenes   órdenes emitidas en el mes
 * @param subtotal  gasto sin impuesto
 * @param impuestos impuesto de las líneas
 * @param total     subtotal + impuestos
 */
public record GastoMes(String periodo, long ordenes, BigDecimal subtotal, BigDecimal impuestos, BigDecimal total) {
}
