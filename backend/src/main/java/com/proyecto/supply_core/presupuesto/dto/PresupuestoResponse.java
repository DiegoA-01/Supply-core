package com.proyecto.supply_core.presupuesto.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

import com.proyecto.supply_core.presupuesto.entity.Presupuesto;

/**
 * Presupuesto de un centro de costo en un mes, con lo que ya está comprometido.
 *
 * @param id             id
 * @param centroCostoId  centro de costo
 * @param anio           año
 * @param mes            mes del 1 al 12
 * @param monto          tope del mes
 * @param comprometido   suma del monto estimado de las solicitudes vigentes del mes
 * @param disponible     monto menos comprometido (puede ser negativo si se aprobaron excepciones)
 * @param usoPct         porcentaje comprometido del tope, con 2 decimales
 * @param creadoEn       fecha de creación
 * @param actualizadoEn  fecha de la última corrección ({@code null} si nunca se corrigió)
 */
public record PresupuestoResponse(Long id, Long centroCostoId, Integer anio, Integer mes, BigDecimal monto,
        BigDecimal comprometido, BigDecimal disponible, BigDecimal usoPct, LocalDateTime creadoEn,
        LocalDateTime actualizadoEn) {

    /**
     * Convierte la entidad calculando el disponible y el porcentaje de uso.
     *
     * @param p            presupuesto
     * @param comprometido monto ya comprometido en el mes
     * @return DTO
     */
    public static PresupuestoResponse of(Presupuesto p, BigDecimal comprometido) {
        BigDecimal uso = comprometido.multiply(BigDecimal.valueOf(100)).divide(p.getMonto(), 2, RoundingMode.HALF_UP);
        return new PresupuestoResponse(p.getId(), p.getCentroCostoId(), p.getAnio(), p.getMes(), p.getMonto(),
                comprometido, p.getMonto().subtract(comprometido), uso, p.getCreadoEn(), p.getActualizadoEn());
    }
}
