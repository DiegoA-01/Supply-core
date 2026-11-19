package com.proyecto.supply_core.venta.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Impuesto de venta.
 *
 * @param codigo     código corto (IVA19, IMPOCONSUMO8…); no cambia después de crearlo
 * @param nombre     nombre visible
 * @param porcentaje tarifa de 0 a 100
 */
public record TarifaImpuestoRequest(
        @NotBlank(message = "es obligatorio")
        @Pattern(regexp = "^[A-Za-z0-9_]{2,20}$", message = "de 2 a 20 letras, números o _")
        String codigo,
        @NotBlank(message = "es obligatorio")
        @Size(max = 100, message = "máximo 100 caracteres")
        String nombre,
        @NotNull(message = "es obligatorio")
        @DecimalMin(value = "0", message = "no puede ser negativo")
        @DecimalMax(value = "100", message = "no puede superar 100")
        @Digits(integer = 3, fraction = 2, message = "máximo 2 decimales")
        BigDecimal porcentaje) {
}
