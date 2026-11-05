package com.proyecto.supply_core.empresa.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Edición de los datos de la empresa.
 *
 * @param razonSocial        nombre legal
 * @param nit                NIT sin puntos, con dígito de verificación opcional
 * @param direccionPrincipal dirección principal
 * @param monedaBase         código ISO 4217 de 3 letras (p. ej. COP)
 * @param topeAnticipoPct    tope de los anticipos como % del total de la orden (opcional; si no viene no cambia)
 * @param toleranciaFacturaPct   diferencia aceptada entre factura y recepción, en % (opcional)
 * @param toleranciaFacturaMonto diferencia aceptada entre factura y recepción, en monto fijo (opcional)
 * @param slaAprobacionHoras horas para decidir una solicitud pendiente antes de escalarla, de 0 (sin SLA) a 720 (opcional)
 */
public record EmpresaRequest(
        @NotBlank(message = "es obligatoria")
        @Size(max = 200, message = "máximo 200 caracteres")
        String razonSocial,

        @NotBlank(message = "es obligatorio")
        @Pattern(regexp = "^\\d{6,15}(-\\d)?$",
                message = "solo números, con dígito de verificación opcional (ej. 900123456-7)")
        String nit,

        @Size(max = 250, message = "máximo 250 caracteres")
        String direccionPrincipal,

        @NotBlank(message = "es obligatoria")
        @Pattern(regexp = "^[A-Za-z]{3}$", message = "debe ser un código de 3 letras (ej. COP)")
        String monedaBase,
        @DecimalMin(value = "0", message = "no puede ser negativo")
        @DecimalMax(value = "100", message = "no puede superar 100")
        @Digits(integer = 3, fraction = 2, message = "máximo 2 decimales")
        BigDecimal topeAnticipoPct,
        @DecimalMin(value = "0", message = "no puede ser negativa")
        @DecimalMax(value = "100", message = "no puede superar 100")
        @Digits(integer = 3, fraction = 2, message = "máximo 2 decimales")
        BigDecimal toleranciaFacturaPct,
        @DecimalMin(value = "0", message = "no puede ser negativa")
        @Digits(integer = 16, fraction = 2, message = "máximo 16 enteros y 2 decimales")
        BigDecimal toleranciaFacturaMonto,
        @Min(value = 0, message = "no puede ser negativo")
        @Max(value = 720, message = "no puede superar 720 horas (30 días)")
        Integer slaAprobacionHoras) {
}
