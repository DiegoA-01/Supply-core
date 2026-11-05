package com.proyecto.supply_core.inventario.dto;

import com.proyecto.supply_core.inventario.entity.Bodega;

/**
 * Bodega tal como la ve la API.
 *
 * @param id         id
 * @param nombre     nombre
 * @param direccion  dirección
 * @param sedeId     sede o {@code null}
 * @param sedeNombre nombre de la sede o {@code null}
 * @param activo     si se puede usar en nuevos movimientos
 */
public record BodegaResponse(Long id, String nombre, String direccion, Long sedeId, String sedeNombre,
        boolean activo) {

    /**
     * Convierte la entidad en DTO (requiere la sede cargada si existe).
     *
     * @param b bodega
     * @return DTO
     */
    public static BodegaResponse of(Bodega b) {
        return new BodegaResponse(b.getId(), b.getNombre(), b.getDireccion(),
                b.getSede() == null ? null : b.getSede().getId(),
                b.getSede() == null ? null : b.getSede().getNombre(), b.isActivo());
    }
}
