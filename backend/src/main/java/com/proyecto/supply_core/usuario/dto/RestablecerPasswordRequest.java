package com.proyecto.supply_core.usuario.dto;

import com.proyecto.supply_core.usuario.validation.ReglasPassword;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * El administrador asigna una contraseña nueva a otro usuario (p. ej. si la olvidó).
 *
 * @param passwordNueva nueva contraseña (8–72, con letra y número)
 */
public record RestablecerPasswordRequest(
        @NotBlank(message = "es obligatoria")
        @Size(min = ReglasPassword.MINIMO, max = ReglasPassword.MAXIMO, message = ReglasPassword.MENSAJE_LARGO)
        @Pattern(regexp = ReglasPassword.PATRON, message = ReglasPassword.MENSAJE_PATRON)
        String passwordNueva) {
}
