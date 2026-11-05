package com.proyecto.supply_core.usuario.dto;

import com.proyecto.supply_core.usuario.validation.ReglasPassword;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * El propio usuario cambia su contraseña: debe demostrar la actual.
 *
 * @param passwordActual contraseña vigente
 * @param passwordNueva  nueva contraseña (8–72, con letra y número, distinta a la actual)
 */
public record CambioPasswordRequest(
        @NotBlank(message = "es obligatoria")
        @Size(max = ReglasPassword.MAXIMO, message = "máximo 72 caracteres")
        String passwordActual,

        @NotBlank(message = "es obligatoria")
        @Size(min = ReglasPassword.MINIMO, max = ReglasPassword.MAXIMO, message = ReglasPassword.MENSAJE_LARGO)
        @Pattern(regexp = ReglasPassword.PATRON, message = ReglasPassword.MENSAJE_PATRON)
        String passwordNueva) {
}
