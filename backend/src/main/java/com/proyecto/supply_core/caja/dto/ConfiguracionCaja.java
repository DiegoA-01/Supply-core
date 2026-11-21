package com.proyecto.supply_core.caja.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Configuración de caja de la empresa.
 *
 * @param toleranciaDiferencia diferencia por medio de pago aceptada sin aprobación de un supervisor (decisión D6)
 * @param bodegaAveriasId      bodega a la que va lo devuelto en mal estado ({@code null} = no reingresa, spec 006)
 * @param umbralAnulacionesDia anulaciones de un cajero en un día que avisan al dueño ({@code null} = no cambia, spec 010)
 * @param umbralDescuentosDia  ventas con descuento autorizado de un cajero en un día que avisan ({@code null} = no cambia)
 */
public record ConfiguracionCaja(
        @NotNull(message = "es obligatoria")
        @DecimalMin(value = "0", message = "no puede ser negativa")
        @DecimalMax(value = "10000000", message = "es demasiado alta")
        @Digits(integer = 16, fraction = 2, message = "máximo 2 decimales")
        BigDecimal toleranciaDiferencia,
        @Positive(message = "debe ser un id válido") Long bodegaAveriasId,
        @Min(value = 1, message = "debe ser al menos 1") @Max(value = 1000, message = "es demasiado alto") Integer umbralAnulacionesDia,
        @Min(value = 1, message = "debe ser al menos 1") @Max(value = 1000, message = "es demasiado alto") Integer umbralDescuentosDia) {
}
