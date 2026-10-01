package com.proyecto.supply_core.inventario.dto;

import java.math.BigDecimal;

import com.proyecto.supply_core.inventario.enums.TipoMovimiento;
import com.proyecto.supply_core.inventario.validation.MovimientoConsistente;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Movimiento manual de inventario registrado por el almacenista (UC-03).
 * La cantidad va en la unidad del producto. Las reglas por tipo las aplica {@link MovimientoConsistente}.
 *
 * @param productoId      producto
 * @param tipo            SALIDA_CONSUMO, AJUSTE_POSITIVO, AJUSTE_NEGATIVO o TRASLADO
 * @param bodegaOrigenId  bodega de la que sale (salida, ajuste negativo, traslado)
 * @param bodegaDestinoId bodega a la que entra (ajuste positivo, traslado)
 * @param cantidad        cantidad (> 0, hasta 4 decimales)
 * @param motivo          obligatorio en ajustes
 */
@MovimientoConsistente
public record MovimientoRequest(
        @NotNull(message = "es obligatorio")
        @Positive(message = "debe ser un id válido")
        Long productoId,

        @NotNull(message = "es obligatorio")
        TipoMovimiento tipo,

        @Positive(message = "debe ser un id válido")
        Long bodegaOrigenId,

        @Positive(message = "debe ser un id válido")
        Long bodegaDestinoId,

        @NotNull(message = "es obligatoria")
        @Positive(message = "debe ser mayor que cero")
        @Digits(integer = 14, fraction = 4, message = "máximo 14 enteros y 4 decimales")
        BigDecimal cantidad,

        @Size(max = 250, message = "máximo 250 caracteres")
        String motivo) {
}
