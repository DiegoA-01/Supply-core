package com.proyecto.supply_core.inventario.dto;

import com.proyecto.supply_core.empresa.validation.SedeActiva;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Alta o edición de bodega. El nombre único se valida en el Service.
 *
 * @param nombre    nombre visible
 * @param direccion dirección opcional
 * @param sedeId    sede activa a la que pertenece (opcional)
 */
public record BodegaRequest(
        @NotBlank(message = "es obligatorio")
        @Size(max = 150, message = "máximo 150 caracteres")
        String nombre,

        @Size(max = 250, message = "máximo 250 caracteres")
        String direccion,

        @Positive(message = "debe ser un id válido")
        @SedeActiva
        Long sedeId) {
}
