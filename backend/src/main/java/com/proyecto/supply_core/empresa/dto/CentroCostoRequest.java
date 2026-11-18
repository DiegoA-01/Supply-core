package com.proyecto.supply_core.empresa.dto;

import com.proyecto.supply_core.empresa.validation.CodigoCentroCostoUnico;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Alta de centro de costo.
 *
 * @param codigo código único (letras, números, guion o guion bajo; se guarda en mayúsculas)
 * @param nombre nombre visible
 */
public record CentroCostoRequest(
        @NotBlank(message = "es obligatorio")
        @Pattern(regexp = "^[A-Za-z0-9_-]{2,30}$", message = "de 2 a 30 caracteres: letras, números, '-' o '_'")
        @CodigoCentroCostoUnico
        String codigo,

        @NotBlank(message = "es obligatorio")
        @Size(max = 150, message = "máximo 150 caracteres")
        String nombre) {
}
