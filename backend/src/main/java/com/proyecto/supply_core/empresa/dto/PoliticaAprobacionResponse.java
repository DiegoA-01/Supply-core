package com.proyecto.supply_core.empresa.dto;

import java.math.BigDecimal;

import com.proyecto.supply_core.empresa.entity.PoliticaAprobacion;
import com.proyecto.supply_core.empresa.enums.TipoPolitica;

/**
 * Política de aprobación tal como la ve la API.
 *
 * @param id                 id
 * @param tipo               qué aprueba
 * @param rolAprobador       código del rol que aprueba
 * @param rolAprobadorNombre nombre del rol
 * @param montoDesde         inicio del rango (inclusive)
 * @param montoHasta         fin del rango (exclusivo) o {@code null} = sin tope
 * @param activo             si está vigente
 */
public record PoliticaAprobacionResponse(Long id, TipoPolitica tipo, String rolAprobador, String rolAprobadorNombre,
        BigDecimal montoDesde, BigDecimal montoHasta, boolean activo) {

    /**
     * Convierte la entidad en DTO (requiere el rol cargado).
     *
     * @param p política
     * @return DTO
     */
    public static PoliticaAprobacionResponse of(PoliticaAprobacion p) {
        return new PoliticaAprobacionResponse(p.getId(), p.getTipo(), p.getRolAprobador().getCodigo(),
                p.getRolAprobador().getNombre(), p.getMontoDesde(), p.getMontoHasta(), p.isActivo());
    }
}
