package com.proyecto.supply_core.factura.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

/**
 * Datos de la factura que entregó el proveedor. El total debe ser subtotal más impuestos.
 *
 * @param numero       número impreso en la factura
 * @param fechaFactura fecha de la factura (hoy o antes)
 * @param subtotal     neto sin impuestos
 * @param impuestos    impuestos (IVA)
 * @param total        total a pagar
 */
public record RegistrarFacturaRequest(
        @NotBlank(message = "es obligatorio")
        @Size(max = 60, message = "máximo 60 caracteres")
        String numero,

        @NotNull(message = "es obligatoria")
        @PastOrPresent(message = "no puede ser una fecha futura")
        LocalDate fechaFactura,

        @NotNull(message = "es obligatorio")
        @DecimalMin(value = "0", message = "no puede ser negativo")
        @Digits(integer = 16, fraction = 2, message = "máximo 16 enteros y 2 decimales")
        BigDecimal subtotal,

        @NotNull(message = "es obligatorio")
        @DecimalMin(value = "0", message = "no puede ser negativo")
        @Digits(integer = 16, fraction = 2, message = "máximo 16 enteros y 2 decimales")
        BigDecimal impuestos,

        @NotNull(message = "es obligatorio")
        @DecimalMin(value = "0.01", message = "debe ser mayor que cero")
        @Digits(integer = 16, fraction = 2, message = "máximo 16 enteros y 2 decimales")
        BigDecimal total) {

    /**
     * El total tiene que ser exactamente el subtotal más los impuestos.
     *
     * @return {@code true} si cuadra la suma
     */
    @AssertTrue(message = "el total debe ser igual al subtotal más los impuestos")
    public boolean isTotalConsistente() {
        return subtotal == null || impuestos == null || total == null
                || subtotal.add(impuestos).compareTo(total) == 0;
    }
}
