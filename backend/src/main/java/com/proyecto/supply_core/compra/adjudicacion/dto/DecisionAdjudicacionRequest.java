package com.proyecto.supply_core.compra.adjudicacion.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Decisión del aprobador sobre una adjudicación.
 *
 * @param aprobar       {@code true} aprueba, {@code false} rechaza
 * @param observaciones comentario (obligatorio al rechazar)
 */
public record DecisionAdjudicacionRequest(
        @NotNull(message = "es obligatorio")
        Boolean aprobar,

        @Size(max = 500, message = "máximo 500 caracteres")
        String observaciones) {

    /**
     * Regla entre campos: rechazar exige explicar por qué.
     *
     * @return {@code true} si aprueba o escribió observaciones
     */
    @AssertTrue(message = "son obligatorias al rechazar")
    public boolean isObservacionesPresentes() {
        return aprobar == null || aprobar || (observaciones != null && !observaciones.isBlank());
    }
}
