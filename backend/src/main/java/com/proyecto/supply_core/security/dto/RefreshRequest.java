package com.proyecto.supply_core.security.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo de renovar o cerrar sesión.
 *
 * @param refreshToken valor entregado en el login o en la última renovación
 */
public record RefreshRequest(
        @NotBlank(message = "es obligatorio")
        @Size(max = 200, message = "máximo 200 caracteres")
        String refreshToken) {
}
