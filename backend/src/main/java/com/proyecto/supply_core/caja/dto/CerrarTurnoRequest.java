package com.proyecto.supply_core.caja.dto;

import java.math.BigDecimal;
import java.util.Map;

import com.proyecto.supply_core.caja.enums.MedioPago;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Cierre ciego: lo que el cajero contó, sin haber visto lo esperado.
 *
 * @param contadoEfectivo efectivo contado
 * @param contadoOtros    total de vouchers y transferencias por medio (sin el efectivo); medios ausentes = 0
 * @param observacion     nota del cajero, opcional
 */
public record CerrarTurnoRequest(
        @NotNull(message = "es obligatorio")
        @DecimalMin(value = "0", message = "no puede ser negativo")
        @Digits(integer = 16, fraction = 2, message = "máximo 2 decimales")
        BigDecimal contadoEfectivo,
        Map<MedioPago, BigDecimal> contadoOtros,
        @Size(max = 500, message = "máximo 500 caracteres") String observacion) {
}
