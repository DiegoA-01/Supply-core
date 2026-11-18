package com.proyecto.supply_core.security.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Pide el correo de recuperación de una contraseña olvidada.
 *
 * @param empresa código público de la empresa
 * @param correo  correo del usuario
 */
public record RecuperarPasswordRequest(
        @NotBlank(message = "es obligatorio") @Size(max = 60, message = "máximo 60 caracteres") String empresa,
        @NotBlank(message = "es obligatorio") @Email(message = "no es un correo válido")
        @Size(max = 150, message = "máximo 150 caracteres") String correo) {
}
