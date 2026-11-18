package com.proyecto.supply_core.informe.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Pedido de informe de compras que hace el administrador de la empresa.
 *
 * @param desde   primer día del periodo
 * @param hasta   último día del periodo
 * @param mensaje qué quiere saber (opcional)
 */
public record PedirInformeRequest(
        @NotNull(message = "es obligatoria")
        LocalDate desde,
        @NotNull(message = "es obligatoria")
        LocalDate hasta,
        @Size(max = 500, message = "máximo 500 caracteres")
        String mensaje) {

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
