package com.proyecto.supply_core.venta.dto;

import java.math.BigDecimal;

import com.proyecto.supply_core.venta.entity.TarifaImpuesto;

/**
 * Impuesto de venta tal como lo ve la API.
 *
 * @param id         id
 * @param codigo     código
 * @param nombre     nombre
 * @param porcentaje tarifa
 * @param activo     si se puede asignar a productos
 */
public record TarifaImpuestoResponse(Long id, String codigo, String nombre, BigDecimal porcentaje, boolean activo) {

    /**
     * @param t entidad
     * @return DTO
     */
    public static TarifaImpuestoResponse of(TarifaImpuesto t) {
        return new TarifaImpuestoResponse(t.getId(), t.getCodigo(), t.getNombre(), t.getPorcentaje(), t.isActivo());
    }
}
