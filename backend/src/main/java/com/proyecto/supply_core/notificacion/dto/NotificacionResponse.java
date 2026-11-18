package com.proyecto.supply_core.notificacion.dto;

import java.time.LocalDateTime;

import com.proyecto.supply_core.notificacion.entity.Notificacion;
import com.proyecto.supply_core.notificacion.enums.EventoNotificacion;

/**
 * Notificación para la bandeja del usuario.
 *
 * @param id       id
 * @param evento   evento que la generó
 * @param mensaje  texto
 * @param leida    si ya la leyó
 * @param creadoEn fecha de creación
 */
public record NotificacionResponse(Long id, EventoNotificacion evento, String mensaje, boolean leida,
        LocalDateTime creadoEn) {

    /**
     * Convierte la entidad.
     *
     * @param n notificación
     * @return DTO
     */
    public static NotificacionResponse of(Notificacion n) {
        return new NotificacionResponse(n.getId(), n.getEvento(), n.getMensaje(), n.isLeida(), n.getCreadoEn());
    }
}
