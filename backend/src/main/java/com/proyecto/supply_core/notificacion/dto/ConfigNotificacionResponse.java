package com.proyecto.supply_core.notificacion.dto;

import com.proyecto.supply_core.notificacion.entity.ConfigNotificacion;
import com.proyecto.supply_core.notificacion.enums.CanalNotificacion;
import com.proyecto.supply_core.notificacion.enums.EventoNotificacion;

/**
 * Configuración de un evento en un canal.
 *
 * @param id     id
 * @param evento evento
 * @param canal  canal
 * @param activo si se envía
 */
public record ConfigNotificacionResponse(Long id, EventoNotificacion evento, CanalNotificacion canal, boolean activo) {

    /**
     * Convierte la entidad.
     *
     * @param c configuración
     * @return DTO
     */
    public static ConfigNotificacionResponse of(ConfigNotificacion c) {
        return new ConfigNotificacionResponse(c.getId(), c.getEvento(), c.getCanal(), c.isActivo());
    }
}
