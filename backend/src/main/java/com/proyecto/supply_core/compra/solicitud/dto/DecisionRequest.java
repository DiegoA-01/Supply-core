package com.proyecto.supply_core.compra.solicitud.dto;

import com.proyecto.supply_core.compra.solicitud.enums.DecisionAprobacion;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Decisión del aprobador (UC-05).
 *
 * @param decision      APROBADA, RECHAZADA o DEVUELTA
 * @param observaciones comentario (obligatorio para RECHAZADA y DEVUELTA)
 */
public record DecisionRequest(
        @NotNull(message = "es obligatoria")
        DecisionAprobacion decision,

        @Size(max = 500, message = "máximo 500 caracteres")
        String observaciones) {

    /**
     * Regla entre campos: rechazar o devolver exige explicar por qué.
     *
     * @return {@code true} si no hace falta o se escribió una observación
     */
    @AssertTrue(message = "son obligatorias al rechazar o devolver")
    public boolean isObservacionesPresentes() {
        return decision == null || !decision.exigeObservaciones()
                || (observaciones != null && !observaciones.isBlank());
    }
}
