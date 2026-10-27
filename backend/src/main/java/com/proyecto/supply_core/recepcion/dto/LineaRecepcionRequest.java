package com.proyecto.supply_core.recepcion.dto;

import java.math.BigDecimal;

import com.proyecto.supply_core.recepcion.enums.MotivoRechazo;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Lo que llegó de una línea de la orden. Las cantidades van en la unidad del producto.
 *
 * @param ordenLineaId       línea de la orden
 * @param cantidadRecibida   lo que llegó físicamente (puede ser 0 si la línea no llegó)
 * @param cantidadRechazada  parte de lo recibido que no sirve (opcional, por defecto 0)
 * @param motivoRechazo      obligatorio si se rechaza algo
 * @param observacionCalidad detalle de la calidad o del rechazo (opcional)
 */
public record LineaRecepcionRequest(
        @NotNull(message = "es obligatoria")
        @Positive(message = "debe ser un id válido")
        Long ordenLineaId,

        @NotNull(message = "es obligatoria")
        @DecimalMin(value = "0", message = "no puede ser negativa")
        @Digits(integer = 14, fraction = 4, message = "máximo 14 enteros y 4 decimales")
        BigDecimal cantidadRecibida,

        @DecimalMin(value = "0", message = "no puede ser negativa")
        @Digits(integer = 14, fraction = 4, message = "máximo 14 enteros y 4 decimales")
        BigDecimal cantidadRechazada,

        MotivoRechazo motivoRechazo,

        @Size(max = 500, message = "máximo 500 caracteres")
        String observacionCalidad) {

    /**
     * Lo rechazado, tratando el valor ausente como cero.
     *
     * @return cantidad rechazada (nunca {@code null})
     */
    public BigDecimal rechazada() {
        return cantidadRechazada == null ? BigDecimal.ZERO : cantidadRechazada;
    }

    /**
     * Si se rechaza algo hay que decir por qué.
     *
     * @return {@code true} si no rechaza nada o trae motivo
     */
    @AssertTrue(message = "debe indicar el motivo cuando se rechaza mercancía")
    public boolean isMotivoPresenteSiRechaza() {
        return rechazada().signum() == 0 || motivoRechazo != null;
    }

    /**
     * No se puede rechazar más de lo que llegó.
     *
     * @return {@code true} si lo rechazado no supera lo recibido
     */
    @AssertTrue(message = "lo rechazado no puede superar lo recibido")
    public boolean isRechazoNoSuperaRecibido() {
        return cantidadRecibida == null || rechazada().compareTo(cantidadRecibida) <= 0;
    }
}
