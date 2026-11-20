package com.proyecto.supply_core.venta.dto;

import java.math.BigDecimal;

/**
 * Impuesto discriminado por tarifa en el tiquete.
 *
 * @param tarifaPct tarifa
 * @param base      base gravable de esa tarifa
 * @param impuesto  impuesto de esa tarifa
 */
public record ImpuestoTiquete(BigDecimal tarifaPct, BigDecimal base, BigDecimal impuesto) {
}
