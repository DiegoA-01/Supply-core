package com.proyecto.supply_core.compra.rfq.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Datos editables de una RFQ en BORRADOR.
 *
 * @param fechaLimite      hasta cuándo se reciben cotizaciones (futura)
 * @param condiciones      condiciones comerciales (entrega, pago, garantía...)
 * @param proveedorIds     invitados; vacío o {@code null} = todos los HABILITADOS de las categorías
 * @param especificaciones detalle técnico por producto (opcional)
 */
public record RfqDatosRequest(
        @NotNull(message = "es obligatoria")
        @Future(message = "debe ser una fecha futura")
        LocalDateTime fechaLimite,

        @Size(max = 1000, message = "máximo 1000 caracteres")
        String condiciones,

        @Size(max = 100, message = "máximo 100 proveedores")
        Set<@NotNull(message = "no puede estar vacío") @Positive(message = "debe ser un id válido") Long> proveedorIds,

        @Size(max = 100, message = "máximo 100 especificaciones")
        List<@NotNull(message = "no puede estar vacía") @Valid EspecificacionRequest> especificaciones) {
}
