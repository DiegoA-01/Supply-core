package com.proyecto.supply_core.compra.solicitud.dto;

import java.math.BigDecimal;

import com.proyecto.supply_core.catalogo.validation.UnidadMedidaActiva;
import com.proyecto.supply_core.inventario.validation.ProductoActivo;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Línea de una solicitud. La unidad debe medir lo mismo que la del producto (se verifica en el Service).
 *
 * @param productoId     producto activo
 * @param unidadMedidaId unidad activa
 * @param cantidad       cantidad &gt; 0, hasta 4 decimales
 */
public record LineaSolicitudRequest(
        @NotNull(message = "es obligatorio")
        @Positive(message = "debe ser un id válido")
        @ProductoActivo
        Long productoId,

        @NotNull(message = "es obligatoria")
        @Positive(message = "debe ser un id válido")
        @UnidadMedidaActiva
        Long unidadMedidaId,

        @NotNull(message = "es obligatoria")
        @Positive(message = "debe ser mayor que cero")
        @Digits(integer = 14, fraction = 4, message = "máximo 14 enteros y 4 decimales")
        BigDecimal cantidad) {
}
