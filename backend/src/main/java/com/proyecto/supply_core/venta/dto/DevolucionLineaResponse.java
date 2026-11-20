package com.proyecto.supply_core.venta.dto;

import java.math.BigDecimal;

import com.proyecto.supply_core.venta.enums.EstadoProductoDevuelto;

/**
 * Línea de una devolución.
 *
 * @param ventaLineaId   línea vendida
 * @param productoId     producto
 * @param nombre         nombre del producto
 * @param unidad         unidad vendida
 * @param cantidad       cantidad devuelta
 * @param estadoProducto estado en que volvió
 * @param bodega         bodega a la que reingresó ({@code null} si no reingresó)
 * @param valor          valor devuelto
 */
public record DevolucionLineaResponse(Long ventaLineaId, Long productoId, String nombre, String unidad,
        BigDecimal cantidad, EstadoProductoDevuelto estadoProducto, String bodega, BigDecimal valor) {
}
