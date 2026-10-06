package com.proyecto.supply_core.compra.solicitud.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Datos para crear o editar una solicitud de compra (UC-04).
 *
 * @param motivo         justificación de la compra
 * @param fechaRequerida fecha en que se necesita (hoy o después)
 * @param lineas         productos y cantidades (al menos una, sin productos repetidos)
 */
public record SolicitudRequest(
        @NotBlank(message = "es obligatorio")
        @Size(max = 500, message = "máximo 500 caracteres")
        String motivo,

        @NotNull(message = "es obligatoria")
        @FutureOrPresent(message = "no puede ser una fecha pasada")
        LocalDate fechaRequerida,

        @NotNull(message = "es obligatorio")
        @NotEmpty(message = "debe tener al menos una línea")
        @Size(max = 100, message = "máximo 100 líneas")
        List<@NotNull(message = "no puede estar vacía") @Valid LineaSolicitudRequest> lineas) {

    /**
     * Regla entre líneas: un producto aparece una sola vez (se suma la cantidad en una línea).
     *
     * @return {@code true} si no hay productos repetidos (o faltan datos que ya reportan otras reglas)
     */
    @AssertTrue(message = "no puede repetir un producto; sume la cantidad en una sola línea")
    public boolean isLineasSinRepetir() {
        if (lineas == null) {
            return true;
        }
        var ids = lineas.stream().filter(Objects::nonNull).map(LineaSolicitudRequest::productoId)
                .filter(Objects::nonNull).toList();
        return ids.size() == ids.stream().distinct().count();
    }
}
