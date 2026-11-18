package com.proyecto.supply_core.plataforma.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Primer paso del login de la plataforma.
 *
 * @param correo   correo del usuario de la plataforma
 * @param password contraseña
 */
public record LoginPlataformaRequest(
        @NotBlank(message = "es obligatorio")
        @Size(max = 150, message = "máximo 150 caracteres")
        String correo,

        @NotBlank(message = "es obligatoria")
        @Size(max = 72, message = "máximo 72 caracteres")
        String password) {
}
