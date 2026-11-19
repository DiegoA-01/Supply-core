package com.proyecto.supply_core.caja.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Datos de una caja.
 *
 * @param nombre             nombre visible (por ejemplo "Caja 1 mostrador")
 * @param sedeId             sede de la caja
 * @param bodegaId           bodega de la que descuenta (debe ser de la misma sede)
 * @param prefijoConsecutivo prefijo de los consecutivos de venta (1 a 10 letras o números)
 * @param topeEfectivo       efectivo a partir del cual se avisa retirar (0 = sin aviso)
 * @param activa             {@code false} para desactivarla; {@code null} = sin cambio
 */
public record CajaRequest(
        @NotBlank(message = "es obligatorio") @Size(max = 100, message = "máximo 100 caracteres") String nombre,
        @NotNull(message = "es obligatoria") @Positive(message = "debe ser un id válido") Long sedeId,
        @NotNull(message = "es obligatoria") @Positive(message = "debe ser un id válido") Long bodegaId,
        @NotBlank(message = "es obligatorio")
        @Pattern(regexp = "^[A-Za-z0-9]{1,10}$", message = "de 1 a 10 letras o números")
        String prefijoConsecutivo,
        @NotNull(message = "es obligatorio")
        @DecimalMin(value = "0", message = "no puede ser negativo")
        @Digits(integer = 16, fraction = 2, message = "máximo 2 decimales")
        BigDecimal topeEfectivo,
        Boolean activa) {
}
