package com.proyecto.supply_core.recepcion.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.proyecto.supply_core.recepcion.entity.RecepcionLinea;
import com.proyecto.supply_core.recepcion.enums.EstadoRecepcionLinea;
import com.proyecto.supply_core.recepcion.enums.MotivoRechazo;

/**
 * Una línea de una recepción.
 *
 * @param id                  id de la línea de recepción
 * @param ordenLineaId        línea de la orden
 * @param productoId          producto
 * @param productoCodigo      código del producto
 * @param productoNombre      nombre del producto
 * @param cantidadRecibida    lo que llegó
 * @param cantidadRechazada   lo rechazado
 * @param cantidadAceptada    lo que subió al stock
 * @param cantidadExcedente   lo que llegó de más
 * @param cantidadFaltante    lo que aún faltaba de la línea después de esta recepción
 * @param estado              ACEPTADA, EN_REVISION o EXCEDENTE_DEVUELTO
 * @param motivoRechazo       motivo del rechazo, si hubo
 * @param observacionCalidad  detalle de calidad
 * @param resueltoPorUsuarioId quién resolvió el excedente
 * @param resueltoEn          cuándo se resolvió
 * @param motivoResolucion    por qué se resolvió así
 */
public record LineaRecepcionResponse(Long id, Long ordenLineaId, Long productoId, String productoCodigo,
        String productoNombre, BigDecimal cantidadRecibida, BigDecimal cantidadRechazada, BigDecimal cantidadAceptada,
        BigDecimal cantidadExcedente, BigDecimal cantidadFaltante, EstadoRecepcionLinea estado,
        MotivoRechazo motivoRechazo, String observacionCalidad, Long resueltoPorUsuarioId, LocalDateTime resueltoEn,
        String motivoResolucion) {

    /**
     * Convierte la entidad.
     *
     * @param l línea con su línea de orden y producto cargados
     * @return DTO
     */
    public static LineaRecepcionResponse of(RecepcionLinea l) {
        var producto = l.getOrdenLinea().getProducto();
        return new LineaRecepcionResponse(l.getId(), l.getOrdenLinea().getId(), producto.getId(), producto.getCodigo(),
                producto.getNombre(), l.getCantidadRecibida(), l.getCantidadRechazada(), l.getCantidadAceptada(),
                l.getCantidadExcedente(), l.getCantidadFaltante(), l.getEstado(), l.getMotivoRechazo(),
                l.getObservacionCalidad(), l.getResueltoPorUsuarioId(), l.getResueltoEn(), l.getMotivoResolucion());
    }
}
