package com.proyecto.supply_core.plataforma.dto;

import java.time.LocalDateTime;

import com.proyecto.supply_core.empresa.entity.Empresa;
import com.proyecto.supply_core.plataforma.entity.Suscripcion;
import com.proyecto.supply_core.plataforma.enums.EstadoSuscripcion;

/**
 * Empresa cliente tal como la ve la consola de la plataforma: sus datos de contrato y su suscripción, nunca datos de
 * negocio.
 *
 * @param id            id
 * @param razonSocial   nombre legal
 * @param nit           NIT
 * @param codigoPublico código público
 * @param plan          código del plan
 * @param planNombre    nombre del plan
 * @param estado        estado de la suscripción
 * @param motivoEstado  motivo del último cambio de estado
 * @param creadoEn      fecha de alta de la empresa
 */
public record EmpresaPlataformaResponse(Long id, String razonSocial, String nit, String codigoPublico, String plan,
        String planNombre, EstadoSuscripcion estado, String motivoEstado, LocalDateTime creadoEn) {

    /**
     * Convierte la empresa y su suscripción.
     *
     * @param e empresa
     * @param s suscripción (con plan cargado)
     * @return DTO
     */
    public static EmpresaPlataformaResponse of(Empresa e, Suscripcion s) {
        return new EmpresaPlataformaResponse(e.getId(), e.getRazonSocial(), e.getNit(), e.getCodigoPublico(),
                s.getPlan().getCodigo(), s.getPlan().getNombre(), s.getEstado(), s.getMotivoEstado(), e.getCreadoEn());
    }
}
