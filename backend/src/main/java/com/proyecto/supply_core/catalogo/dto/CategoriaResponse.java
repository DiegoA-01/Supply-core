package com.proyecto.supply_core.catalogo.dto;

import com.proyecto.supply_core.catalogo.entity.CategoriaProducto;

/**
 * Categoría de producto tal como la ve la API (catálogo, registro de proveedores y detalle del proveedor).
 *
 * @param id     id de la categoría
 * @param nombre nombre visible
 * @param activo si se puede usar en nuevas operaciones
 */
public record CategoriaResponse(Long id, String nombre, boolean activo) {

    /**
     * Convierte la entidad en DTO.
     *
     * @param c categoría
     * @return DTO
     */
    public static CategoriaResponse of(CategoriaProducto c) {
        return new CategoriaResponse(c.getId(), c.getNombre(), c.isActivo());
    }
}
