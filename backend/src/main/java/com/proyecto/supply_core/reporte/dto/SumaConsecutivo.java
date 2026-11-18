package com.proyecto.supply_core.reporte.dto;

import java.math.BigDecimal;

/**
 * Valor sumado de todas las versiones de una orden.
 *
 * @param consecutivo OC-000001
 * @param valor       suma de pagos o de facturas
 */
public record SumaConsecutivo(String consecutivo, BigDecimal valor) {
}
