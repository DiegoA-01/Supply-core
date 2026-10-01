package com.proyecto.supply_core.empresa.dto;

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
        String monedaBase) {
}
