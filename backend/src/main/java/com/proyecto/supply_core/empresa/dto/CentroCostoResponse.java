package com.proyecto.supply_core.empresa.dto;

import com.proyecto.supply_core.empresa.entity.CentroCosto;

/**
 * Centro de costo tal como lo ve la API.
 *
 * @param id     id
 * @param codigo código
 * @param nombre nombre
 * @param activo si se puede usar en nuevas operaciones
 */
public record CentroCostoResponse(Long id, String codigo, String nombre, boolean activo) {

    /**
     * Convierte la entidad en DTO.
     *
     * @param c centro de costo
     * @return DTO
     */
    public static CentroCostoResponse of(CentroCosto c) {
        return new CentroCostoResponse(c.getId(), c.getCodigo(), c.getNombre(), c.isActivo());
    }
}
