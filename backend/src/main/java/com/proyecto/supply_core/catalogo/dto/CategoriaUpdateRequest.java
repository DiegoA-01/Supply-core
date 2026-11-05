package com.proyecto.supply_core.catalogo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Edición de categoría. El nombre único se valida en el Service (excluye a la propia categoría).
 *
 * @param nombre nombre nuevo
 */
public record CategoriaUpdateRequest(
        @NotBlank(message = "es obligatorio")
        @Size(max = 150, message = "máximo 150 caracteres")
        String nombre) {
}
