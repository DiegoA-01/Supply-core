package com.proyecto.supply_core.caja.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Apertura de turno.
 *
 * @param cajaId       caja
 * @param baseApertura efectivo contado al abrir
 */
public record AbrirTurnoRequest(
        @NotNull(message = "es obligatoria") @Positive(message = "debe ser un id válido") Long cajaId,
        @NotNull(message = "es obligatoria")
        @DecimalMin(value = "0", message = "no puede ser negativa")
        @Digits(integer = 16, fraction = 2, message = "máximo 2 decimales")
        BigDecimal baseApertura) {
}
