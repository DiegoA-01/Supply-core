package com.proyecto.supply_core.catalogo.dto;

import com.proyecto.supply_core.catalogo.validation.NombreCategoriaUnico;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Alta de categoría de producto.
 *
 * @param nombre nombre único
 */
public record CategoriaRequest(
        @NotBlank(message = "es obligatorio")
        @Size(max = 150, message = "máximo 150 caracteres")
        @NombreCategoriaUnico
        String nombre) {
}
