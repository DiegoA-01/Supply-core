package com.proyecto.supply_core.caja.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

/**
 * Configuración de caja de la empresa.
 *
 * @param toleranciaDiferencia diferencia por medio de pago aceptada sin aprobación de un supervisor (decisión D6)
 */
public record ConfiguracionCaja(
        @NotNull(message = "es obligatoria")
        @DecimalMin(value = "0", message = "no puede ser negativa")
        @DecimalMax(value = "10000000", message = "es demasiado alta")
        @Digits(integer = 16, fraction = 2, message = "máximo 2 decimales")
        BigDecimal toleranciaDiferencia) {
}
