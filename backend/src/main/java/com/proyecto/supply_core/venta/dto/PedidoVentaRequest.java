package com.proyecto.supply_core.venta.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Pedido de venta que arma el vendedor.
 *
 * @param clienteId cliente ({@code null} = consumidor final)
 * @param lineas    productos (1 a 300)
 * @param nota      nota para el cajero
 */
public record PedidoVentaRequest(
        @Positive(message = "debe ser un id válido") Long clienteId,
        @NotEmpty(message = "agregue al menos un producto") @Size(max = 300, message = "admite hasta 300 líneas")
        List<@Valid LineaVentaRequest> lineas,
        @Size(max = 300, message = "admite hasta 300 caracteres") String nota) {
}
