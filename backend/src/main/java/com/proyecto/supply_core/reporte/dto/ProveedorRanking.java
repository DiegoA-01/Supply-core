package com.proyecto.supply_core.reporte.dto;

import java.math.BigDecimal;

/**
 * Un proveedor con actividad en el período, ordenado por score.
 *
 * @param posicion      1 es el mejor
 * @param proveedorId   proveedor
 * @param razonSocial   nombre
 * @param score         score actual; {@code null} si aún no tiene evaluaciones
 * @param ordenes       órdenes del período
 * @param totalOrdenado subtotal + impuestos de esas órdenes
 */
public record ProveedorRanking(int posicion, Long proveedorId, String razonSocial, BigDecimal score, long ordenes,
        BigDecimal totalOrdenado) {
}
