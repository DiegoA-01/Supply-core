package com.proyecto.supply_core.informe.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Envío de un informe de compras por parte del área de compras.
 *
 * @param desde    primer día del periodo
 * @param hasta    último día del periodo
 * @param analisis análisis y recomendaciones del jefe de compras
 * @param pedidoId pedido del administrador que se responde ({@code null} si se envía sin pedido)
 */
public record EnviarInformeRequest(
        @NotNull(message = "es obligatoria")
        LocalDate desde,
        @NotNull(message = "es obligatoria")
        LocalDate hasta,
        @NotBlank(message = "es obligatorio")
        @Size(min = 10, max = 5000, message = "entre 10 y 5000 caracteres")
        String analisis,
        @Positive(message = "debe ser un id válido")
        Long pedidoId) {

    /**
     * El periodo debe ir de una fecha a otra igual o posterior.
     *
     * @return {@code true} si el rango es válido o falta alguna fecha (eso lo avisa {@code @NotNull})
     */
    @AssertTrue(message = "la fecha final no puede ser anterior a la inicial")
    public boolean isRangoValido() {
        return desde == null || hasta == null || !hasta.isBefore(desde);
    }
}
