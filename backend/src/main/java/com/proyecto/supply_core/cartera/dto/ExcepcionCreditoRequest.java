package com.proyecto.supply_core.cartera.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Excepción de crédito: el cliente compra a crédito aunque esté en mora, hasta una fecha.
 *
 * @param hasta  último día de la excepción (hoy o después)
 * @param motivo por qué se habilita
 */
public record ExcepcionCreditoRequest(
        @NotNull(message = "es obligatoria") @FutureOrPresent(message = "no puede ser en el pasado") LocalDate hasta,
        @NotBlank(message = "es obligatorio") @Size(min = 5, max = 300, message = "debe tener entre 5 y 300 caracteres") String motivo) {
}
