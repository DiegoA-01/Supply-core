package com.proyecto.supply_core.empresa.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Edición de centro de costo. El código único se valida en el Service (excluye al propio centro).
 *
 * @param codigo código (se guarda en mayúsculas)
 * @param nombre nombre visible
 */
public record CentroCostoUpdateRequest(
        @NotBlank(message = "es obligatorio")
        @Pattern(regexp = "^[A-Za-z0-9_-]{2,30}$", message = "de 2 a 30 caracteres: letras, números, '-' o '_'")
        String codigo,

        @NotBlank(message = "es obligatorio")
        @Size(max = 150, message = "máximo 150 caracteres")
        String nombre) {
}
