package com.proyecto.supply_core.inventario.dto;

import java.math.BigDecimal;

import com.proyecto.supply_core.inventario.entity.StockProductoBodega;

/**
 * Existencia de un producto en una bodega.
 *
 * @param productoId     producto
 * @param productoCodigo código del producto
 * @param productoNombre nombre del producto
 * @param unidad         código de la unidad del stock
 * @param bodegaId       bodega
 * @param bodegaNombre   nombre de la bodega
 * @param cantidad       existencia actual
 */
public record StockResponse(Long productoId, String productoCodigo, String productoNombre, String unidad,
        Long bodegaId, String bodegaNombre, BigDecimal cantidad) {

    /**
     * Convierte la entidad en DTO (requiere producto, unidad y bodega cargados).
     *
     * @param s fila de stock
     * @return DTO
     */
    public static StockResponse of(StockProductoBodega s) {
        return new StockResponse(s.getProducto().getId(), s.getProducto().getCodigo(), s.getProducto().getNombre(),
                s.getProducto().getUnidadMedida().getCodigo(), s.getBodega().getId(), s.getBodega().getNombre(),
                s.getCantidad());
    }
}
