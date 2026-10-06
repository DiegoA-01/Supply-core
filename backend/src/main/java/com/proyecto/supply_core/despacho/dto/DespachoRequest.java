package com.proyecto.supply_core.despacho.dto;

import java.util.List;
import java.util.Objects;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Datos para crear o editar un despacho en BORRADOR.
 *
 * @param bodegaId          bodega de la que sale la mercancía
 * @param cliente           cliente o destinatario (opcional)
 * @param referenciaExterna número de factura o ticket de caja (opcional)
 * @param observaciones     notas (opcional)
 * @param lineas            productos y cantidades (al menos una, sin productos repetidos)
 */
public record DespachoRequest(
        @NotNull(message = "es obligatoria")
        @Positive(message = "debe ser un id válido")
        Long bodegaId,

        @Size(max = 200, message = "máximo 200 caracteres")
        String cliente,

        @Size(max = 100, message = "máximo 100 caracteres")
        String referenciaExterna,

        @Size(max = 500, message = "máximo 500 caracteres")
        String observaciones,

        @NotNull(message = "es obligatorio")
        @NotEmpty(message = "debe tener al menos una línea")
        @Size(max = 200, message = "máximo 200 líneas")
        List<@NotNull(message = "no puede estar vacía") @Valid LineaDespachoRequest> lineas) {

    /**
     * Regla entre líneas: un producto aparece una sola vez.
     *
     * @return {@code true} si no hay productos repetidos (o faltan datos que ya reportan otras reglas)
     */
    @AssertTrue(message = "no puede repetir un producto; sume la cantidad en una sola línea")
    public boolean isLineasSinRepetir() {
        if (lineas == null) {
            return true;
        }
        var ids = lineas.stream().filter(Objects::nonNull).map(LineaDespachoRequest::productoId)
                .filter(Objects::nonNull).toList();
        return ids.size() == ids.stream().distinct().count();
    }
}
