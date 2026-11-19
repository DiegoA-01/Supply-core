package com.proyecto.supply_core.pedido.dto;

import com.proyecto.supply_core.pedido.enums.EstadoPedidoInterno;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Respuesta de compras a un pedido interno.
 *
 * @param estado    EN_GESTION, ATENDIDO o DESCARTADO
 * @param respuesta qué se hizo o por qué no (obligatoria al descartar)
 */
public record GestionPedidoRequest(
        @NotNull(message = "es obligatorio")
        EstadoPedidoInterno estado,
        @Size(max = 500, message = "máximo 500 caracteres")
        String respuesta) {
}
