package com.proyecto.supply_core.usuario.dto;

import com.proyecto.supply_core.usuario.entity.Rol;

/**
 * Rol tal como lo ve la API.
 *
 * @param id          id
 * @param codigo      código estable (p. ej. {@code COMPRADOR})
 * @param nombre      nombre visible
 * @param descripcion descripción o {@code null}
 */
public record RolResponse(Long id, String codigo, String nombre, String descripcion) {

    /**
     * Convierte la entidad en DTO.
     *
     * @param r entidad
     * @return DTO de respuesta
     */
    public static RolResponse of(Rol r) {
        return new RolResponse(r.getId(), r.getCodigo(), r.getNombre(), r.getDescripcion());
    }
}
