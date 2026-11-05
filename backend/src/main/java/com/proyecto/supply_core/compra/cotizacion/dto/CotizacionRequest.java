package com.proyecto.supply_core.compra.cotizacion.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * Datos de la cotización por formulario (borrador). Las líneas pueden ser solo de algunos productos.
 *
 * @param moneda                 código ISO de 3 letras (por defecto COP)
 * @param plazoEntregaDias       días para entregar después de la orden
 * @param garantiaMeses          meses de garantía
 * @param validezHasta           hasta cuándo se sostienen los precios
 * @param condicionesComerciales forma de pago, transporte, etc.
 * @param lineas                 precios por producto (sin repetir línea de la RFQ)
 */
public record CotizacionRequest(
        @Pattern(regexp = "^[A-Z]{3}$", message = "debe ser un código de 3 letras en mayúscula (ej. COP)")
        String moneda,

        @PositiveOrZero(message = "no puede ser negativo")
        @Max(value = 3650, message = "máximo 3650 días")
        Integer plazoEntregaDias,

        @PositiveOrZero(message = "no puede ser negativa")
        @Max(value = 600, message = "máximo 600 meses")
        Integer garantiaMeses,

        @FutureOrPresent(message = "no puede ser una fecha pasada")
        LocalDate validezHasta,

        @Size(max = 1000, message = "máximo 1000 caracteres")
        String condicionesComerciales,

        @Size(max = 200, message = "máximo 200 líneas")
        List<@NotNull(message = "no puede estar vacía") @Valid LineaCotizacionRequest> lineas) {

    /**
     * Regla entre líneas: cada producto de la RFQ se cotiza una sola vez.
     *
     * @return {@code true} si no hay líneas de la RFQ repetidas
     */
    @AssertTrue(message = "no puede cotizar dos veces la misma línea de la RFQ")
    public boolean isLineasSinRepetir() {
        if (lineas == null) {
            return true;
        }
        var ids = lineas.stream().filter(Objects::nonNull).map(LineaCotizacionRequest::rfqLineaId)
                .filter(Objects::nonNull).toList();
        return ids.size() == ids.stream().distinct().count();
    }
}
