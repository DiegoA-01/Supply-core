package com.proyecto.supply_core.empresa.dto;

import java.math.BigDecimal;

import com.proyecto.supply_core.empresa.enums.TipoPolitica;
import com.proyecto.supply_core.empresa.validation.RolInterno;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * Alta o edición de una política de aprobación. Rango {@code [montoDesde, montoHasta)}.
 * La no superposición con otras políticas se valida en el Service (depende de las existentes).
 *
 * @param tipo         qué aprueba (SOLICITUD o ADJUDICACION)
 * @param rolAprobador código del rol que aprueba (interno, no PROVEEDOR)
 * @param montoDesde   monto inicial inclusive (≥ 0)
 * @param montoHasta   monto final exclusivo; {@code null} = sin tope
 */
public record PoliticaAprobacionRequest(
        @NotNull(message = "es obligatorio")
        TipoPolitica tipo,

        @NotBlank(message = "es obligatorio")
        @RolInterno
        String rolAprobador,

        @NotNull(message = "es obligatorio")
        @PositiveOrZero(message = "no puede ser negativo")
        @Digits(integer = 16, fraction = 2, message = "máximo 16 enteros y 2 decimales")
        BigDecimal montoDesde,

        @Positive(message = "debe ser mayor que cero")
        @Digits(integer = 16, fraction = 2, message = "máximo 16 enteros y 2 decimales")
        BigDecimal montoHasta) {

    /**
     * Regla entre campos: el tope debe ser mayor que el inicio.
     *
     * @return {@code true} si no hay tope o {@code montoHasta > montoDesde}
     */
    @AssertTrue(message = "montoHasta debe ser mayor que montoDesde")
    public boolean isRangoValido() {
        return montoHasta == null || montoDesde == null || montoHasta.compareTo(montoDesde) > 0;
    }
}
