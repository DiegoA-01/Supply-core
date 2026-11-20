package com.proyecto.supply_core.venta.dto;

import java.math.BigDecimal;

import com.proyecto.supply_core.venta.enums.EstadoProductoDevuelto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Línea que se devuelve.
 *
 * @param ventaLineaId   línea de la venta original
 * @param cantidad       cantidad en la unidad en que se vendió (> 0)
 * @param estadoProducto estado en que vuelve
 */
public record DevolucionLineaRequest(
        @NotNull(message = "es obligatoria") @Positive(message = "debe ser un id válido") Long ventaLineaId,
        @NotNull(message = "es obligatoria") @Positive(message = "debe ser mayor que cero")
        @Digits(integer = 14, fraction = 4, message = "admite hasta 4 decimales") BigDecimal cantidad,
        @NotNull(message = "es obligatorio") EstadoProductoDevuelto estadoProducto) {
}
