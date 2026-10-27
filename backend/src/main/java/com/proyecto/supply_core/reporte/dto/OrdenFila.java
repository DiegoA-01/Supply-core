package com.proyecto.supply_core.reporte.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.proyecto.supply_core.compra.orden.enums.EstadoOrden;

/**
 * Orden vigente de un proveedor, tal como sale de la base de datos.
 *
 * @param id          orden
 * @param consecutivo OC-000001
 * @param creadoEn    fecha de emisión
 * @param estado      estado actual
 * @param total       total de la orden
 */
public record OrdenFila(Long id, String consecutivo, LocalDateTime creadoEn, EstadoOrden estado, BigDecimal total) {
}
