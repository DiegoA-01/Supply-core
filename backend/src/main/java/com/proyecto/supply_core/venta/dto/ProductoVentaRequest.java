package com.proyecto.supply_core.venta.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

/**
 * Datos de venta de un producto.
 *
 * @param tarifaImpuestoId     impuesto de venta (obligatorio para tener margen exacto)
 * @param vendible             {@code false} si solo se consume internamente
 * @param precioIncluyeImpuesto {@code true} si el precio de mostrador ya trae el impuesto
 * @param codigoBarras         código de barras, opcional (único en la empresa)
 */
public record ProductoVentaRequest(
        @Positive(message = "debe ser un id válido") Long tarifaImpuestoId,
        boolean vendible,
        boolean precioIncluyeImpuesto,
        @Pattern(regexp = "^[0-9A-Za-z-]{4,50}$", message = "de 4 a 50 números, letras o guiones")
        String codigoBarras) {
}
