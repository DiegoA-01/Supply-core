package com.proyecto.supply_core.contrato.dto;

import java.time.LocalDate;

import com.proyecto.supply_core.contrato.entity.Contrato;
import com.proyecto.supply_core.contrato.enums.EstadoContrato;

/**
 * Contrato para listados.
 *
 * @param id                   id
 * @param numero               número del contrato
 * @param proveedorId          proveedor
 * @param proveedorRazonSocial razón social
 * @param vigenteDesde         primer día de vigencia
 * @param vigenteHasta         último día de vigencia
 * @param estado               ACTIVO o ANULADO
 * @param vigente              {@code true} si hoy se puede usar
 */
public record ContratoResumenResponse(Long id, String numero, Long proveedorId, String proveedorRazonSocial,
        LocalDate vigenteDesde, LocalDate vigenteHasta, EstadoContrato estado, boolean vigente) {

    /**
     * Convierte la entidad (con proveedor cargado).
     *
     * @param c   contrato
     * @param hoy fecha con la que se calcula si está vigente
     * @return DTO
     */
    public static ContratoResumenResponse of(Contrato c, LocalDate hoy) {
        return new ContratoResumenResponse(c.getId(), c.getNumero(), c.getProveedor().getId(),
                c.getProveedor().getRazonSocial(), c.getVigenteDesde(), c.getVigenteHasta(), c.getEstado(),
                c.esVigente(hoy));
    }
}
