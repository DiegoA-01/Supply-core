package com.proyecto.supply_core.compra.adjudicacion.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Elección de la cotización ganadora.
 *
 * @param cotizacionId  cotización elegida (debe estar en el análisis publicado)
 * @param justificacion por qué; obligatoria si no es la recomendada
 */
public record AdjudicarRequest(
        @NotNull(message = "es obligatoria")
        @Positive(message = "debe ser un id válido")
        Long cotizacionId,

        @Size(max = 1000, message = "máximo 1000 caracteres")
        String justificacion) {
}
