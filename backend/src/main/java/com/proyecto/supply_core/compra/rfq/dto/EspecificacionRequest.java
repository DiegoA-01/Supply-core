package com.proyecto.supply_core.compra.rfq.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Especificación técnica de un producto de la RFQ (marca, medidas, calidad...).
 *
 * @param productoId producto (debe estar en la solicitud)
 * @param texto      especificación
 */
public record EspecificacionRequest(
        @NotNull(message = "es obligatorio")
        @Positive(message = "debe ser un id válido")
        Long productoId,

        @NotBlank(message = "es obligatoria")
        @Size(max = 1000, message = "máximo 1000 caracteres")
        String texto) {
}
