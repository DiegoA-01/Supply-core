package com.proyecto.supply_core.empresa.dto;

import java.math.BigDecimal;

import com.proyecto.supply_core.empresa.entity.Sede;

/**
 * Sede tal como la ve la API.
 *
 * @param id        id
 * @param nombre    nombre
 * @param direccion dirección
 * @param latitud   latitud o {@code null}
 * @param longitud  longitud o {@code null}
 * @param activo    si se puede usar en nuevas operaciones
 */
public record SedeResponse(Long id, String nombre, String direccion, BigDecimal latitud, BigDecimal longitud,
        boolean activo) {

    /**
     * Convierte la entidad en DTO.
     *
     * @param s sede
     * @return DTO
     */
    public static SedeResponse of(Sede s) {
        return new SedeResponse(s.getId(), s.getNombre(), s.getDireccion(), s.getLatitud(), s.getLongitud(),
                s.isActivo());
    }
}
