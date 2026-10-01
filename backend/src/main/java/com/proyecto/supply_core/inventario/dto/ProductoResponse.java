package com.proyecto.supply_core.inventario.dto;

import java.math.BigDecimal;

import com.proyecto.supply_core.catalogo.dto.CategoriaResponse;
import com.proyecto.supply_core.catalogo.dto.UnidadMedidaResponse;
import com.proyecto.supply_core.inventario.entity.Producto;

/**
 * Producto tal como lo ve la API, con su stock total calculado.
 *
 * @param id              id
 * @param codigo          código
 * @param nombre          nombre
 * @param descripcion     descripción
 * @param categoria       categoría
 * @param unidadMedida    unidad en la que se lleva el stock
 * @param stockMinimo     mínimo total
 * @param stockMaximo     máximo total o {@code null}
 * @param costoReferencia precio de referencia o {@code null}
 * @param activo          si se puede usar en nuevas operaciones
 * @param stockTotal      suma del stock en todas las bodegas
 * @param bajoStockMinimo {@code true} si está activo y {@code stockTotal < stockMinimo}
 */
public record ProductoResponse(Long id, String codigo, String nombre, String descripcion,
        CategoriaResponse categoria, UnidadMedidaResponse unidadMedida, BigDecimal stockMinimo,
        BigDecimal stockMaximo, BigDecimal costoReferencia, boolean activo, BigDecimal stockTotal,
        boolean bajoStockMinimo) {

    /**
     * Convierte la entidad en DTO (requiere categoría y unidad cargadas).
     *
     * @param p          producto
     * @param stockTotal stock total ya calculado
     * @return DTO
     */
    public static ProductoResponse of(Producto p, BigDecimal stockTotal) {
        return new ProductoResponse(p.getId(), p.getCodigo(), p.getNombre(), p.getDescripcion(),
                CategoriaResponse.of(p.getCategoria()), UnidadMedidaResponse.of(p.getUnidadMedida()),
                p.getStockMinimo(), p.getStockMaximo(), p.getCostoReferencia(), p.isActivo(), stockTotal,
                p.isActivo() && stockTotal.compareTo(p.getStockMinimo()) < 0);
    }
}
