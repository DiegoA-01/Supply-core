package com.proyecto.supply_core.catalogo.dto;

import java.math.BigDecimal;

import com.proyecto.supply_core.catalogo.enums.Magnitud;
import com.proyecto.supply_core.catalogo.validation.CodigoUnidadUnico;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Alta de unidad de medida. Código, magnitud y factor no se pueden cambiar después.
 *
 * @param codigo               código único (letras y números; se guarda en mayúsculas)
 * @param nombre               nombre visible
 * @param magnitud             qué mide
 * @param factorConversionBase cuántas unidades base de la magnitud equivale (Docena = 12)
 */
public record UnidadMedidaRequest(
        @NotBlank(message = "es obligatorio")
        @Pattern(regexp = "^[A-Za-z0-9]{1,20}$", message = "de 1 a 20 letras o números, sin espacios")
        @CodigoUnidadUnico
        String codigo,

        @NotBlank(message = "es obligatorio")
        @Size(max = 80, message = "máximo 80 caracteres")
        String nombre,

        @NotNull(message = "es obligatoria")
        Magnitud magnitud,

        @NotNull(message = "es obligatorio")
        @Positive(message = "debe ser mayor que cero")
        @DecimalMax(value = "1000000", message = "no puede superar 1.000.000")
        @Digits(integer = 12, fraction = 6, message = "máximo 6 decimales")
        BigDecimal factorConversionBase) {
}
