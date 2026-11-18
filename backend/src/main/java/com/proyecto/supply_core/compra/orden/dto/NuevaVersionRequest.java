package com.proyecto.supply_core.compra.orden.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * Cambios para una versión nueva. Lo que no se envía se copia de la versión actual. Los precios no cambian
 * (vienen de la cotización adjudicada).
 *
 * @param motivo               por qué cambia (obligatorio)
 * @param bodegaDestinoId      nueva bodega (opcional)
 * @param lugarEntrega         nuevo lugar (opcional)
 * @param fechaEntregaEsperada nueva fecha (opcional)
 * @param cantidades           nuevas cantidades por línea (opcional; 0 = quitar la línea)
 */
public record NuevaVersionRequest(
        @NotBlank(message = "es obligatorio")
        @Size(min = 5, max = 500, message = "entre 5 y 500 caracteres")
        String motivo,

        @Positive(message = "debe ser un id válido")
        Long bodegaDestinoId,

        @Size(max = 250, message = "máximo 250 caracteres")
        String lugarEntrega,

        @Future(message = "debe ser una fecha futura")
        LocalDate fechaEntregaEsperada,

        @Size(max = 200, message = "máximo 200 líneas")
        List<@NotNull(message = "no puede estar vacía") @Valid CambioCantidad> cantidades) {

    /**
     * Regla entre líneas: cada línea una sola vez.
     *
     * @return {@code true} si no hay líneas repetidas
     */
    @AssertTrue(message = "no puede repetir una línea")
    public boolean isCantidadesSinRepetir() {
        if (cantidades == null) {
            return true;
        }
        var ids = cantidades.stream().filter(Objects::nonNull).map(CambioCantidad::lineaId).filter(Objects::nonNull)
                .toList();
        return ids.size() == ids.stream().distinct().count();
    }

    /**
     * Nueva cantidad de una línea.
     *
     * @param lineaId  línea de la versión actual
     * @param cantidad nueva cantidad en la unidad del producto (0 = quitar)
     */
    public record CambioCantidad(
            @NotNull(message = "es obligatoria")
            @Positive(message = "debe ser un id válido")
            Long lineaId,

            @NotNull(message = "es obligatoria")
            @PositiveOrZero(message = "no puede ser negativa")
            @Digits(integer = 14, fraction = 4, message = "máximo 14 enteros y 4 decimales")
            BigDecimal cantidad) {
    }
}
