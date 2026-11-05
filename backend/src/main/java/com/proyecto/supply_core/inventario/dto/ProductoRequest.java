package com.proyecto.supply_core.inventario.dto;

import java.math.BigDecimal;

import com.proyecto.supply_core.catalogo.validation.CategoriaActiva;
import com.proyecto.supply_core.catalogo.validation.UnidadMedidaActiva;
import com.proyecto.supply_core.inventario.validation.CodigoProductoUnico;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * Alta de producto (UC-02).
 *
 * @param codigo          código único (letras, números, '.', '-', '_'; se guarda en mayúsculas)
 * @param nombre          nombre visible
 * @param descripcion     descripción opcional
 * @param categoriaId     categoría activa
 * @param unidadMedidaId  unidad activa en la que se lleva el stock
 * @param stockMinimo     total mínimo entre bodegas (≥ 0)
 * @param stockMaximo     total máximo opcional (≥ mínimo)
 * @param costoReferencia precio de referencia opcional (≥ 0)
 */
public record ProductoRequest(
        @NotBlank(message = "es obligatorio")
        @Pattern(regexp = "^[A-Za-z0-9._-]{1,50}$", message = "de 1 a 50 caracteres: letras, números, '.', '-' o '_'")
        @CodigoProductoUnico
        String codigo,

        @NotBlank(message = "es obligatorio")
        @Size(max = 200, message = "máximo 200 caracteres")
        String nombre,

        @Size(max = 500, message = "máximo 500 caracteres")
        String descripcion,

        @NotNull(message = "es obligatoria")
        @Positive(message = "debe ser un id válido")
        @CategoriaActiva
        Long categoriaId,

        @NotNull(message = "es obligatoria")
        @Positive(message = "debe ser un id válido")
        @UnidadMedidaActiva
        Long unidadMedidaId,

        @NotNull(message = "es obligatorio")
        @PositiveOrZero(message = "no puede ser negativo")
        @Digits(integer = 14, fraction = 4, message = "máximo 14 enteros y 4 decimales")
        BigDecimal stockMinimo,

        @Positive(message = "debe ser mayor que cero")
        @Digits(integer = 14, fraction = 4, message = "máximo 14 enteros y 4 decimales")
        BigDecimal stockMaximo,

        @PositiveOrZero(message = "no puede ser negativo")
        @Digits(integer = 14, fraction = 4, message = "máximo 14 enteros y 4 decimales")
        BigDecimal costoReferencia) {

    /**
     * Regla entre campos: el máximo no puede ser menor que el mínimo.
     *
     * @return {@code true} si no hay máximo o {@code stockMaximo >= stockMinimo}
     */
    @AssertTrue(message = "stockMaximo no puede ser menor que stockMinimo")
    public boolean isRangoStockValido() {
        return stockMaximo == null || stockMinimo == null || stockMaximo.compareTo(stockMinimo) >= 0;
    }
}
