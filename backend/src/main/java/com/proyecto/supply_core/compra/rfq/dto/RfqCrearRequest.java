package com.proyecto.supply_core.compra.rfq.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Crea una RFQ desde una solicitud APROBADA (las líneas se copian de la solicitud).
 *
 * @param solicitudId solicitud de origen
 * @param datos       fecha límite, condiciones, invitados y especificaciones
 */
public record RfqCrearRequest(
        @NotNull(message = "es obligatoria")
        @Positive(message = "debe ser un id válido")
        Long solicitudId,

        @NotNull(message = "son obligatorios")
        @Valid
        RfqDatosRequest datos) {
}
