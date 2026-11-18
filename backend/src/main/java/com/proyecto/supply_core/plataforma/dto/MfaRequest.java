package com.proyecto.supply_core.plataforma.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Segundo paso del login de la plataforma: el código de la app de autenticación.
 *
 * @param desafio desafío entregado al acertar la contraseña
 * @param codigo  código de 6 dígitos
 */
public record MfaRequest(
        @NotBlank(message = "es obligatorio")
        String desafio,

        @NotBlank(message = "es obligatorio")
        @Pattern(regexp = "^\\d{6}$", message = "debe tener 6 dígitos")
        String codigo) {
}
