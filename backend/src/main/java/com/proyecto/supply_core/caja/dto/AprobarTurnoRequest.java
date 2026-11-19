package com.proyecto.supply_core.caja.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Aprobación de la diferencia de un turno.
 *
 * @param explicacion qué pasó (al menos 10 caracteres)
 */
public record AprobarTurnoRequest(
        @NotBlank(message = "es obligatoria") @Size(min = 10, max = 500, message = "de 10 a 500 caracteres")
        String explicacion) {
}
