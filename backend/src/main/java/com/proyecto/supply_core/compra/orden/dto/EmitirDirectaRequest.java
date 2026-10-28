package com.proyecto.supply_core.compra.orden.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Orden directa al precio pactado de un contrato (sin RFQ). Las líneas salen de la solicitud y los precios del
 * contrato; la persona solo elige a quién y dónde se entrega.
 *
 * @param solicitudId          solicitud APROBADA que se compra
 * @param contratoId           contrato vigente que cubre todos sus productos
 * @param bodegaDestinoId      bodega donde se recibirá
 * @param lugarEntrega         dirección o indicaciones de entrega
 * @param fechaEntregaEsperada fecha comprometida (base de la puntualidad)
 */
public record EmitirDirectaRequest(
        @NotNull(message = "es obligatoria")
        @Positive(message = "debe ser un id válido")
        Long solicitudId,

        @NotNull(message = "es obligatorio")
        @Positive(message = "debe ser un id válido")
        Long contratoId,

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
