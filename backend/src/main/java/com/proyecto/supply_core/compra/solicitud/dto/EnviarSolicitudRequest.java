package com.proyecto.supply_core.compra.solicitud.dto;

import jakarta.validation.constraints.Size;

/**
 * Datos opcionales al enviar una solicitud. Sin cuerpo, se envía sin excepción.
 *
 * @param justificacionExcepcion por qué se envía aunque exceda el presupuesto del centro de costo o repita un
 *                               producto ya pedido; sin ella, esas dos situaciones se rechazan con 409
 */
public record EnviarSolicitudRequest(
        @Size(max = 500, message = "máximo 500 caracteres")
        String justificacionExcepcion) {
}
