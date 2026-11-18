package com.proyecto.supply_core.security.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Segundo paso del login de un usuario con MFA.
 *
 * @param desafio desafío entregado al acertar la contraseña
 * @param codigo  código de 6 dígitos de la app de autenticación
 */
public record MfaCodigoRequest(
        @NotBlank(message = "es obligatorio") String desafio,
        @NotBlank(message = "es obligatorio") @Pattern(regexp = "\\d{6}", message = "debe tener 6 dígitos") String codigo) {
}
