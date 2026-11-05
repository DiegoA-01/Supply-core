package com.proyecto.supply_core.compra.solicitud.dto;

import java.time.LocalDateTime;

import com.proyecto.supply_core.compra.solicitud.entity.AprobacionSolicitud;
import com.proyecto.supply_core.compra.solicitud.enums.DecisionAprobacion;

/**
 * Decisión registrada sobre una solicitud.
 *
 * @param id                 id
 * @param usuarioAprobadorId quién decidió
 * @param decision           decisión
 * @param observaciones      comentario
 * @param decididoEn         fecha
 */
public record AprobacionResponse(Long id, Long usuarioAprobadorId, DecisionAprobacion decision, String observaciones,
        LocalDateTime decididoEn) {

    /**
     * Convierte la entidad.
     *
     * @param a decisión
     * @return DTO
     */
    public static AprobacionResponse of(AprobacionSolicitud a) {
        return new AprobacionResponse(a.getId(), a.getUsuarioAprobadorId(), a.getDecision(), a.getObservaciones(),
                a.getDecididoEn());
    }
}
