package com.proyecto.supply_core.delegacion.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

import com.proyecto.supply_core.delegacion.entity.DelegacionAprobacion;
import com.proyecto.supply_core.delegacion.enums.EstadoDelegacion;

/**
 * Delegación de aprobación tal como la ve la API.
 *
 * @param id              id
 * @param deleganteId     usuario que delega
 * @param deleganteNombre nombre de quien delega
 * @param delegadoId      usuario que recibe
 * @param delegadoNombre  nombre de quien recibe
 * @param vigenteDesde    primer día
 * @param vigenteHasta    último día
 * @param estado          ACTIVA o REVOCADA
 * @param vigente         {@code true} si hoy se puede usar (ACTIVA y dentro de las fechas)
 * @param motivo          motivo ({@code null} si no se dio)
 * @param creadoEn        fecha de creación
 * @param revocadaEn      fecha de revocación ({@code null} si sigue activa)
 */
public record DelegacionResponse(Long id, Long deleganteId, String deleganteNombre, Long delegadoId,
        String delegadoNombre, LocalDate vigenteDesde, LocalDate vigenteHasta, EstadoDelegacion estado,
        boolean vigente, String motivo, LocalDateTime creadoEn, LocalDateTime revocadaEn) {

    /**
     * Convierte la entidad.
     *
     * @param d       delegación
     * @param nombres nombre de usuario por id (puede faltar alguno)
     * @param hoy     fecha con la que se calcula si está vigente
     * @return DTO
     */
    public static DelegacionResponse of(DelegacionAprobacion d, Map<Long, String> nombres, LocalDate hoy) {
        return new DelegacionResponse(d.getId(), d.getDeleganteId(), nombres.get(d.getDeleganteId()), d.getDelegadoId(),
                nombres.get(d.getDelegadoId()), d.getVigenteDesde(), d.getVigenteHasta(), d.getEstado(),
                d.esVigente(hoy), d.getMotivo(), d.getCreadoEn(), d.getRevocadaEn());
    }
}
