package com.proyecto.supply_core.notificacion.dto;

/**
 * Contador de notificaciones (campana del frontend o resultado de "marcar todas").
 *
 * @param cantidad número de notificaciones
 */
public record CantidadResponse(long cantidad) {
}
