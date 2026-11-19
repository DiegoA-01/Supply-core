package com.proyecto.supply_core.pedido.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * "Necesito este producto": un producto del catálogo o una descripción libre de algo que aún no se maneja.
 *
 * @param productoId  producto del catálogo (opcional si hay descripción)
 * @param descripcion qué hace falta, si no está en el catálogo (opcional si hay producto)
 * @param cantidad    cuánto, opcional
 * @param sedeId      sede donde hace falta, opcional
 * @param nota        para qué o para cuándo, opcional
 */
public record PedidoInternoRequest(
        @Positive(message = "debe ser un id válido")
        Long productoId,
        @Size(max = 300, message = "máximo 300 caracteres")
        String descripcion,
        @Positive(message = "debe ser mayor que cero")
        @Digits(integer = 14, fraction = 4, message = "máximo 14 enteros y 4 decimales")
        BigDecimal cantidad,
        @Positive(message = "debe ser un id válido")
        Long sedeId,
        @Size(max = 500, message = "máximo 500 caracteres")
        String nota) {

    /**
     * @return {@code true} si dice qué hace falta: un producto o una descripción
     */
    @AssertTrue(message = "elige un producto o describe lo que hace falta")
    public boolean isQueHaceFalta() {
        return productoId != null || (descripcion != null && !descripcion.isBlank());
    }
}
