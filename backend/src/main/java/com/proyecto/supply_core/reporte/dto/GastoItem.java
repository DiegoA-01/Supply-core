package com.proyecto.supply_core.reporte.dto;

import java.math.BigDecimal;

/**
 * Gasto de un proveedor o de una categoría.
 *
 * @param id        proveedor o categoría
 * @param nombre    razón social o nombre de la categoría
 * @param ordenes   órdenes con al menos una línea del grupo
 * @param subtotal  gasto sin impuesto
 * @param impuestos impuesto de las líneas
 * @param total     subtotal + impuestos
 */
public record GastoItem(Long id, String nombre, long ordenes, BigDecimal subtotal, BigDecimal impuestos,
        BigDecimal total) {
}
