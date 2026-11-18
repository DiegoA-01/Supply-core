package com.proyecto.supply_core.compra.cotizacion.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * Precio ofertado para un producto de la RFQ.
 *
 * @param rfqLineaId             línea de la RFQ que se cotiza
 * @param cantidadOfertada       cantidad en la presentación del proveedor
 * @param unidadOfertada         presentación del proveedor ("caja x24")
 * @param factorConversion       unidades de la RFQ por unidad ofertada (caja x24 frente a UND = 24)
 * @param precioUnitarioOfertado precio de una unidad ofertada, sin impuesto
 * @param descuentoPct           descuento en % (opcional, 0–100)
 * @param impuestoPct            impuesto en % (opcional, 0–100; p. ej. IVA 19)
 */
public record LineaCotizacionRequest(
        @NotNull(message = "es obligatoria")
        @Positive(message = "debe ser un id válido")
        Long rfqLineaId,

        @NotNull(message = "es obligatoria")
        @Positive(message = "debe ser mayor que cero")
        @Digits(integer = 14, fraction = 4, message = "máximo 14 enteros y 4 decimales")
        BigDecimal cantidadOfertada,

        @NotBlank(message = "es obligatoria")
        @Size(max = 50, message = "máximo 50 caracteres")
        String unidadOfertada,

        @NotNull(message = "es obligatorio")
        @Positive(message = "debe ser mayor que cero")
        @Digits(integer = 12, fraction = 6, message = "máximo 12 enteros y 6 decimales")
        BigDecimal factorConversion,

        @NotNull(message = "es obligatorio")
        @PositiveOrZero(message = "no puede ser negativo")
        @Digits(integer = 14, fraction = 4, message = "máximo 14 enteros y 4 decimales")
        BigDecimal precioUnitarioOfertado,

        @DecimalMin(value = "0", message = "no puede ser negativo")
        @DecimalMax(value = "100", message = "no puede ser mayor que 100")
        @Digits(integer = 3, fraction = 2, message = "máximo 2 decimales")
        BigDecimal descuentoPct,

        @DecimalMin(value = "0", message = "no puede ser negativo")
        @DecimalMax(value = "100", message = "no puede ser mayor que 100")
        @Digits(integer = 3, fraction = 2, message = "máximo 2 decimales")
        BigDecimal impuestoPct) {
}
