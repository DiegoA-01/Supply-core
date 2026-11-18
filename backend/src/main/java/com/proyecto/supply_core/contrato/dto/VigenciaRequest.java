package com.proyecto.supply_core.contrato.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.NotNull;

/**
 * Nueva fecha de fin de un contrato (extenderlo o acortarlo).
 *
 * @param vigenteHasta último día de vigencia; no puede ser antes de hoy ni del inicio del contrato
 */
public record VigenciaRequest(
        @NotNull(message = "es obligatoria")
        LocalDate vigenteHasta) {
}
