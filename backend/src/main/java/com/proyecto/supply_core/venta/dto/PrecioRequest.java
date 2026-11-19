package com.proyecto.supply_core.venta.dto;

import java.math.BigDecimal;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Nuevo precio de un producto en una lista (cierra el anterior).
 *
 * @param listaId      lista de precios
 * @param productoId   producto
 * @param precio       precio de venta en la unidad indicada
 * @param precioMinimo piso opcional (no mayor que el precio)
 * @param motivo       por qué cambia, opcional
 * @param unidadMedidaId unidad del precio, opcional ({@code null} = la unidad del producto); debe convertirse a ella
 */
@Schema(name = "PrecioVentaRequest")
public record PrecioRequest(
        @NotNull(message = "es obligatoria") @Positive(message = "debe ser un id válido") Long listaId,
        @NotNull(message = "es obligatorio") @Positive(message = "debe ser un id válido") Long productoId,
        @NotNull(message = "es obligatorio")
        @Positive(message = "debe ser mayor que cero")
        @Digits(integer = 16, fraction = 2, message = "máximo 2 decimales")
        BigDecimal precio,
        @Positive(message = "debe ser mayor que cero")
        @Digits(integer = 16, fraction = 2, message = "máximo 2 decimales")
        BigDecimal precioMinimo,
        @Size(max = 300, message = "máximo 300 caracteres")
        String motivo,
        @Positive(message = "debe ser un id válido") Long unidadMedidaId) {

    /**
     * @return {@code true} si no hay mínimo o no supera el precio
     */
    @AssertTrue(message = "el precio mínimo no puede ser mayor que el precio")
    public boolean isMinimoValido() {
        return precioMinimo == null || precio == null || precioMinimo.compareTo(precio) <= 0;
    }
}
