package com.proyecto.supply_core.catalogo.dto;

import java.math.BigDecimal;

import com.proyecto.supply_core.catalogo.entity.UnidadMedida;
import com.proyecto.supply_core.catalogo.enums.Magnitud;

/**
 * Unidad de medida tal como la ve la API.
 *
 * @param id                   id
 * @param codigo               código
 * @param nombre               nombre
 * @param magnitud             qué mide
 * @param factorConversionBase equivalencia en la unidad base de su magnitud
 * @param activo               si se puede usar en nuevas operaciones
 */
public record UnidadMedidaResponse(Long id, String codigo, String nombre, Magnitud magnitud,
        BigDecimal factorConversionBase, boolean activo) {

    /**
     * Convierte la entidad en DTO (factor sin ceros sobrantes: 12, 0.001).
     *
     * @param u unidad
     * @return DTO
     */
    public static UnidadMedidaResponse of(UnidadMedida u) {
        return new UnidadMedidaResponse(u.getId(), u.getCodigo(), u.getNombre(), u.getMagnitud(),
                u.getFactorConversionBase().stripTrailingZeros(), u.isActivo());
    }
}
