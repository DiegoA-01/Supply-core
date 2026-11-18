package com.proyecto.supply_core.delegacion.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Datos para delegar las decisiones de aprobación en otro usuario durante un rango de fechas. Quien delega es el
 * usuario de la sesión.
 *
 * @param delegadoId   usuario interno activo que recibe la delegación (no puede ser quien delega)
 * @param vigenteDesde primer día en que puede decidir
 * @param vigenteHasta último día en que puede decidir (no antes de hoy ni de {@code vigenteDesde})
 * @param motivo       por qué delega, por ejemplo vacaciones (opcional)
 */
public record DelegacionRequest(
        @NotNull(message = "es obligatorio")
        @Positive(message = "debe ser un id válido")
        Long delegadoId,

        @NotNull(message = "es obligatoria")
        LocalDate vigenteDesde,

        @NotNull(message = "es obligatoria")
        LocalDate vigenteHasta,

        @Size(max = 250, message = "máximo 250 caracteres")
        String motivo) {

    /**
     * Regla entre fechas: la delegación no puede terminar antes de empezar.
     *
     * @return {@code true} si las fechas faltan (otra validación las exige) o están en orden
     */
    @AssertTrue(message = "la vigencia no puede terminar antes de empezar")
    public boolean isVigenciaEnOrden() {
        return vigenteDesde == null || vigenteHasta == null || !vigenteHasta.isBefore(vigenteDesde);
    }
}
