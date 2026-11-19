package com.proyecto.supply_core.venta.dto;

import com.proyecto.supply_core.venta.entity.ListaPrecio;

/**
 * Lista de precios tal como la ve la API.
 *
 * @param id        id
 * @param nombre    nombre
 * @param esGeneral {@code true} para la lista General
 * @param activo    si se usa
 */
public record ListaPrecioResponse(Long id, String nombre, boolean esGeneral, boolean activo) {

    /**
     * @param l entidad
     * @return DTO
     */
    public static ListaPrecioResponse of(ListaPrecio l) {
        return new ListaPrecioResponse(l.getId(), l.getNombre(), l.isEsGeneral(), l.isActivo());
    }
}
