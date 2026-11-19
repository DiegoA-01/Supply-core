package com.proyecto.supply_core.venta.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Lista de precios.
 *
 * @param nombre nombre visible ("Mayorista", "Taller aliado"…)
 * @param activo si se puede usar (la General siempre está activa); {@code null} no cambia
 */
public record ListaPrecioRequest(
        @NotBlank(message = "es obligatorio")
        @Size(max = 100, message = "máximo 100 caracteres")
        String nombre,
        Boolean activo) {
}
