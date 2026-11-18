package com.proyecto.supply_core.notificacion.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Activa o desactiva un canal para un evento.
 *
 * @param activo nuevo estado
 */
public record ConfigNotificacionRequest(@NotNull(message = "es obligatorio") Boolean activo) {
}
