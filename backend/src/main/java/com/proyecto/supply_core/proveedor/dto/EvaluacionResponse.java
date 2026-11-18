package com.proyecto.supply_core.proveedor.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.proyecto.supply_core.proveedor.entity.EvaluacionProveedor;

/**
 * Evaluación de un proveedor en una orden. Indicador {@code null} = sin datos.
 *
 * @param id                   id
 * @param ordenCompraId        orden evaluada
 * @param puntualidad          0–100 o {@code null}
 * @param cumplimientoCantidad 0–100 o {@code null}
 * @param incidencias          0–100 o {@code null}
 * @param calidad              0–100 o {@code null}
 * @param precio               0–100 o {@code null}
 * @param respuesta            0–100 o {@code null}
 * @param score                promedio de los indicadores con datos o {@code null}
 * @param creadoEn             fecha de la evaluación
 */
public record EvaluacionResponse(Long id, Long ordenCompraId, BigDecimal puntualidad, BigDecimal cumplimientoCantidad,
        BigDecimal incidencias, BigDecimal calidad, BigDecimal precio, BigDecimal respuesta, BigDecimal score,
        LocalDateTime creadoEn) {

    /**
     * Convierte la entidad.
     *
     * @param e evaluación
     * @return DTO
     */
    public static EvaluacionResponse of(EvaluacionProveedor e) {
        return new EvaluacionResponse(e.getId(), e.getOrdenCompraId(), e.getPuntualidad(), e.getCumplimientoCantidad(),
                e.getIncidencias(), e.getCalidad(), e.getPrecio(), e.getRespuesta(), e.getScore(), e.getCreadoEn());
    }
}
