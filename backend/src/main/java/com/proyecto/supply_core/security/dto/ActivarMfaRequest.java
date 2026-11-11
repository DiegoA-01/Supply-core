package com.proyecto.supply_core.security.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Confirma que la app de autenticación quedó vinculada.
 *
 * @param codigo código de 6 dígitos que muestra la app
 */
public record ActivarMfaRequest(
        @NotBlank(message = "es obligatorio") @Pattern(regexp = "\\d{6}", message = "debe tener 6 dígitos") String codigo) {
}
