package com.proyecto.supply_core.cliente.dto;

import java.math.BigDecimal;

import com.proyecto.supply_core.cliente.enums.TipoDocumento;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Datos de un cliente para crearlo o editarlo. El cupo, el plazo y la lista de precios solo los cambian el dueño o el
 * supervisor de ventas; si vienen vacíos al editar, se conservan.
 *
 * @param tipoDocumento   CC, NIT, CE o PAS
 * @param numeroDocumento número (se quitan puntos y espacios; el NIT lleva su dígito de verificación)
 * @param nombre          nombre o razón social
 * @param telefono        teléfono, opcional (exige autorización de datos)
 * @param correo          correo, opcional (exige autorización de datos)
 * @param direccion       dirección, opcional (exige autorización de datos)
 * @param aceptaDatos     {@code true} si el cliente autoriza el tratamiento de sus datos; {@code false} la retira
 *                        (entonces no se guardan datos de contacto); {@code null} = sin cambio
 * @param listaPrecioId   lista de precios del cliente; {@code null} = la General o sin cambio
 * @param cupoCredito     cupo de crédito; {@code null} = 0 al crear o sin cambio al editar
 * @param plazoDias       plazo en días (0 a 365); {@code null} = 0 al crear o sin cambio al editar
 * @param activo          {@code false} para desactivarlo; {@code null} = sin cambio
 */
public record ClienteRequest(
        @NotNull(message = "es obligatorio") TipoDocumento tipoDocumento,
        @NotBlank(message = "es obligatorio") @Size(max = 25, message = "máximo 25 caracteres") String numeroDocumento,
        @NotBlank(message = "es obligatorio") @Size(max = 200, message = "máximo 200 caracteres") String nombre,
        @Size(max = 30, message = "máximo 30 caracteres") String telefono,
        @Email(message = "no es un correo válido") @Size(max = 150, message = "máximo 150 caracteres") String correo,
        @Size(max = 250, message = "máximo 250 caracteres") String direccion,
        Boolean aceptaDatos,
        @Positive(message = "debe ser un id válido") Long listaPrecioId,
        @DecimalMin(value = "0", message = "no puede ser negativo")
        @Digits(integer = 16, fraction = 2, message = "máximo 2 decimales")
        BigDecimal cupoCredito,
        @Min(value = 0, message = "no puede ser negativo") @Max(value = 365, message = "máximo 365 días") Integer plazoDias,
        Boolean activo) {
}
