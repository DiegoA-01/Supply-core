package com.proyecto.supply_core.venta.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Carrito que el POS manda para ver los totales sin guardar nada (spec 005).
 *
 * @param clienteId cliente ({@code null} = consumidor final)
 * @param lineas    productos (1 a 300)
 * @param pagos     pagos escritos hasta ahora (vacío = solo totales)
 */
public record CotizarRequest(
        @Positive(message = "debe ser un id válido") Long clienteId,
        @NotEmpty(message = "agregue al menos un producto") @Size(max = 300, message = "admite hasta 300 líneas")
        List<@Valid LineaVentaRequest> lineas,
        @Size(max = 10, message = "admite hasta 10 pagos") List<@Valid PagoVentaRequest> pagos) {
}
