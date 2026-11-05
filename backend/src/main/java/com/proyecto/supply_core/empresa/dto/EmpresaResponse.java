package com.proyecto.supply_core.empresa.dto;

import java.math.BigDecimal;
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
 * @param topeAnticipoPct    tope de los anticipos (% del total de la orden)
 * @param toleranciaFacturaPct   tolerancia del cruce de la factura en %
 * @param toleranciaFacturaMonto tolerancia del cruce de la factura en monto fijo
 * @param slaAprobacionHoras horas para decidir una solicitud pendiente antes de escalarla (0 = sin SLA)
 * @param creadoEn           fecha de creación
 */
public record EmpresaResponse(Long id, String razonSocial, String nit, String direccionPrincipal,
        String monedaBase, BigDecimal topeAnticipoPct, BigDecimal toleranciaFacturaPct,
        BigDecimal toleranciaFacturaMonto, int slaAprobacionHoras, LocalDateTime creadoEn) {

    /**
     * Convierte la entidad en DTO.
     *
     * @param e empresa
     * @return DTO
     */
    public static EmpresaResponse of(Empresa e) {
        return new EmpresaResponse(e.getId(), e.getRazonSocial(), e.getNit(), e.getDireccionPrincipal(),
                e.getMonedaBase(), e.getTopeAnticipoPct(), e.getToleranciaFacturaPct(), e.getToleranciaFacturaMonto(),
                e.getSlaAprobacionHoras(), e.getCreadoEn());
    }
}
