package com.proyecto.supply_core.reorden.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Pide crear una solicitud en BORRADOR con las cantidades sugeridas. El borrador no compra nada: el solicitante lo
 * revisa, lo edita si quiere y lo envía por el flujo normal (presupuesto, política y aprobación).
 *
 * @param fechaRequerida fecha en que se necesita la mercancía (hoy o después)
 * @param productoIds    productos a incluir; si falta o está vacío, todos los que tienen cantidad sugerida
 */
public record BorradorReordenRequest(
        @NotNull(message = "es obligatoria")
        @FutureOrPresent(message = "no puede ser una fecha pasada")
        LocalDate fechaRequerida,

        @Size(max = 200, message = "máximo 200 productos")
        List<@NotNull(message = "no puede estar vacío") @Positive(message = "debe ser un id válido") Long> productoIds) {

    /**
     * Regla entre productos: cada uno una sola vez.
     *
     * @return {@code true} si no hay productos repetidos
     */
    @AssertTrue(message = "no puede repetir un producto")
    public boolean isProductosSinRepetir() {
        if (productoIds == null) {
            return true;
        }
        var ids = productoIds.stream().filter(Objects::nonNull).toList();
        return ids.size() == ids.stream().distinct().count();
    }
}
