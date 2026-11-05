package com.proyecto.supply_core.catalogo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Edición de unidad de medida: solo el nombre (código, magnitud y factor son inmutables
 * porque cambiarlos alteraría las cantidades ya registradas).
 *
 * @param nombre nombre nuevo
 */
public record UnidadMedidaUpdateRequest(
        @NotBlank(message = "es obligatorio")
        @Size(max = 80, message = "máximo 80 caracteres")
        String nombre) {
}
