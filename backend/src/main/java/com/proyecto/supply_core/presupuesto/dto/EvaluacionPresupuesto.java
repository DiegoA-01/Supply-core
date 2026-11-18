package com.proyecto.supply_core.presupuesto.dto;

import java.math.BigDecimal;

/**
 * Resultado de comparar el monto de una solicitud con el presupuesto de su centro de costo y mes.
 *
 * @param hayPresupuesto {@code true} si el centro de costo tiene presupuesto definido ese mes
 * @param presupuesto    tope del mes ({@code null} si no hay presupuesto)
 * @param comprometido   monto ya comprometido por otras solicitudes ({@code null} si no hay presupuesto)
 * @param disponible     tope menos comprometido ({@code null} si no hay presupuesto)
 * @param excede         {@code true} si comprometido más el monto de la solicitud supera el tope
 */
public record EvaluacionPresupuesto(boolean hayPresupuesto, BigDecimal presupuesto, BigDecimal comprometido,
        BigDecimal disponible, boolean excede) {

    /** Resultado cuando el centro de costo no tiene presupuesto ese mes: no hay control. */
    public static final EvaluacionPresupuesto SIN_PRESUPUESTO = new EvaluacionPresupuesto(false, null, null, null,
            false);
}
