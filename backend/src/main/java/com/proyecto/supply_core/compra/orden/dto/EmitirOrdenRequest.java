package com.proyecto.supply_core.compra.orden.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Emisión de la orden desde una adjudicación aprobada (líneas y precios salen de la cotización ganadora).
 *
 * @param adjudicacionId       adjudicación APROBADA
 * @param bodegaDestinoId      bodega donde se recibirá
 * @param lugarEntrega         dirección o indicaciones de entrega
 * @param fechaEntregaEsperada fecha comprometida (base de la puntualidad)
 */
public record EmitirOrdenRequest(
        @NotNull(message = "es obligatoria")
        @Positive(message = "debe ser un id válido")
        Long adjudicacionId,

        @NotNull(message = "es obligatoria")
        @Positive(message = "debe ser un id válido")
        Long bodegaDestinoId,

        @NotBlank(message = "es obligatorio")
        @Size(max = 250, message = "máximo 250 caracteres")
        String lugarEntrega,

        @NotNull(message = "es obligatoria")
        @Future(message = "debe ser una fecha futura")
        LocalDate fechaEntregaEsperada) {
}
