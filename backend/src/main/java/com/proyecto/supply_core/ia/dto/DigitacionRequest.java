package com.proyecto.supply_core.ia.dto;

import java.util.List;
import java.util.Objects;

import com.proyecto.supply_core.compra.cotizacion.dto.LineaCotizacionRequest;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Digitación manual de una cotización por archivo (sin IA, o cuando la IA no la leyó bien).
 *
 * @param lineas precios leídos del documento por una persona
 */
public record DigitacionRequest(
        @NotNull(message = "es obligatorio")
        @NotEmpty(message = "debe tener al menos una línea")
        @Size(max = 200, message = "máximo 200 líneas")
        List<@NotNull(message = "no puede estar vacía") @Valid LineaCotizacionRequest> lineas) {

    /**
     * Regla entre líneas: cada producto de la RFQ una sola vez.
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
