package com.proyecto.supply_core.security.dto;

import com.proyecto.supply_core.usuario.validation.ReglasPassword;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Canjea el token recibido por correo por una contraseña nueva.
 *
 * @param empresa       código público de la empresa
 * @param token         valor recibido por correo (sirve una sola vez)
 * @param passwordNueva nueva contraseña (8–72, con letra y número)
 */
public record ReiniciarPasswordRequest(
        @NotBlank(message = "es obligatorio") @Size(max = 60, message = "máximo 60 caracteres") String empresa,
        @NotBlank(message = "es obligatorio") @Size(max = 200, message = "máximo 200 caracteres") String token,
        @NotBlank(message = "es obligatoria")
        @Size(min = ReglasPassword.MINIMO, max = ReglasPassword.MAXIMO, message = ReglasPassword.MENSAJE_LARGO)
        @Pattern(regexp = ReglasPassword.PATRON, message = ReglasPassword.MENSAJE_PATRON)
        String passwordNueva) {
}
