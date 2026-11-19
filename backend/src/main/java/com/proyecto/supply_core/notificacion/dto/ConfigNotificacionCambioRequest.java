package com.proyecto.supply_core.notificacion.dto;

import com.proyecto.supply_core.notificacion.enums.CanalNotificacion;
import com.proyecto.supply_core.notificacion.enums.EventoNotificacion;

import jakarta.validation.constraints.NotNull;

/**
 * Activa o desactiva un canal de un evento por su nombre, exista o no la fila en la base (sin fila = activo).
 *
 * @param evento evento del aviso
 * @param canal  bandeja interna o correo
 * @param activo nuevo estado
 */
public record ConfigNotificacionCambioRequest(
        @NotNull(message = "es obligatorio") EventoNotificacion evento,
        @NotNull(message = "es obligatorio") CanalNotificacion canal,
        @NotNull(message = "es obligatorio") Boolean activo) {
}
