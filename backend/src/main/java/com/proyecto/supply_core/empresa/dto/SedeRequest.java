package com.proyecto.supply_core.empresa.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Alta o edición de una sede. El nombre único se valida en el Service (alta y edición).
 *
 * @param nombre    nombre visible
 * @param direccion dirección
 * @param latitud   latitud (-90 a 90), junto con la longitud o ninguna
 * @param longitud  longitud (-180 a 180), junto con la latitud o ninguna
 */
public record SedeRequest(
        @NotBlank(message = "es obligatorio")
        @Size(max = 150, message = "máximo 150 caracteres")
        String nombre,

        @Size(max = 250, message = "máximo 250 caracteres")
        String direccion,

        @DecimalMin(value = "-90", message = "debe estar entre -90 y 90")
        @DecimalMax(value = "90", message = "debe estar entre -90 y 90")
        @Digits(integer = 3, fraction = 7, message = "máximo 7 decimales")
        BigDecimal latitud,

        @DecimalMin(value = "-180", message = "debe estar entre -180 y 180")
        @DecimalMax(value = "180", message = "debe estar entre -180 y 180")
        @Digits(integer = 3, fraction = 7, message = "máximo 7 decimales")
        BigDecimal longitud) {

    /**
     * Regla entre campos: las coordenadas van juntas.
     *
     * @return {@code true} si vienen ambas o ninguna
     */
    @AssertTrue(message = "latitud y longitud deben enviarse juntas")
    public boolean isCoordenadasCompletas() {
        return (latitud == null) == (longitud == null);
    }
}
