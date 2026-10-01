package com.proyecto.supply_core.empresa.dto;

import java.time.LocalDateTime;

import com.proyecto.supply_core.empresa.entity.Empresa;

/**
 * Empresa tal como la ve la API.
 *
 * @param id                 id
 * @param razonSocial        nombre legal
 * @param nit                NIT
 * @param direccionPrincipal dirección principal
 * @param monedaBase         moneda (ISO 4217)
 * @param creadoEn           fecha de creación
 */
public record EmpresaResponse(Long id, String razonSocial, String nit, String direccionPrincipal,
        String monedaBase, LocalDateTime creadoEn) {

    /**
     * Convierte la entidad en DTO.
     *
     * @param e empresa
     * @return DTO
     */
    public static EmpresaResponse of(Empresa e) {
        return new EmpresaResponse(e.getId(), e.getRazonSocial(), e.getNit(), e.getDireccionPrincipal(),
                e.getMonedaBase(), e.getCreadoEn());
    }
}
