package com.proyecto.supply_core.reorden.dto;

import java.time.LocalDate;
import java.util.List;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Productos del plan de reabastecimiento que se pasan a una solicitud en BORRADOR con su cantidad sugerida.
 *
 * @param fechaRequerida fecha en que se necesita la mercancía (hoy o después)
 * @param productoIds    productos a incluir (deben tener cantidad sugerida)
 * @param sedeId         sede del plan del que salen las cantidades ({@code null} = toda la empresa)
 */
public record ReabastecimientoBorradorRequest(
        @NotNull(message = "es obligatoria")
        @FutureOrPresent(message = "no puede ser una fecha pasada")
        LocalDate fechaRequerida,
        @NotEmpty(message = "elige al menos un producto")
        List<@NotNull(message = "no puede ser vacío") Long> productoIds,
        @Positive(message = "debe ser un id válido")
        Long sedeId) {
}
