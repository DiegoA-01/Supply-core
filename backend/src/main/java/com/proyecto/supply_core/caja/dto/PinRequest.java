package com.proyecto.supply_core.caja.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Fijar o cambiar el PIN de supervisor.
 *
 * @param passwordActual contraseña de la cuenta, para confirmar que es la persona
 * @param pin            PIN nuevo de 6 dígitos
 */
public record PinRequest(
        @NotBlank(message = "es obligatoria") @Size(max = 72, message = "máximo 72 caracteres") String passwordActual,
        @NotBlank(message = "es obligatorio") @Pattern(regexp = "^\\d{6}$", message = "son 6 dígitos") String pin) {

    /**
     * Oculta la contraseña y el PIN si el registro se imprime en un log.
     *
     * @return texto sin secretos
     */
    @Override
    public String toString() {
        return "PinRequest[***]";
    }
}
