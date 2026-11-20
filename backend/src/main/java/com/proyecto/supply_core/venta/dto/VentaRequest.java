package com.proyecto.supply_core.venta.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Venta que manda el POS. La clave de idempotencia va en la cabecera {@code Idempotency-Key}.
 *
 * @param clienteId              cliente ({@code null} = consumidor final)
 * @param pedidoVentaId          pedido de venta que se cobra ({@code null} = venta directa)
 * @param lineas                 productos (1 a 300)
 * @param pagos                  pagos (1 a 10)
 * @param autorizacionDescuento  token del supervisor si algún descuento pasa el límite del rol
 * @param autorizacionPrecio     token del supervisor si algún precio queda bajo el mínimo
 */
public record VentaRequest(
        @Positive(message = "debe ser un id válido") Long clienteId,
        @Positive(message = "debe ser un id válido") Long pedidoVentaId,
        @NotEmpty(message = "agregue al menos un producto") @Size(max = 300, message = "admite hasta 300 líneas")
        List<@Valid LineaVentaRequest> lineas,
        @NotEmpty(message = "agregue al menos un pago") @Size(max = 10, message = "admite hasta 10 pagos")
        List<@Valid PagoVentaRequest> pagos,
        @Size(max = 100, message = "no es una autorización válida") String autorizacionDescuento,
        @Size(max = 100, message = "no es una autorización válida") String autorizacionPrecio) {
}
