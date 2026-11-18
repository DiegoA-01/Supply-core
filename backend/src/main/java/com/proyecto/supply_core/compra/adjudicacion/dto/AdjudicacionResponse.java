package com.proyecto.supply_core.compra.adjudicacion.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.proyecto.supply_core.compra.adjudicacion.entity.Adjudicacion;
import com.proyecto.supply_core.compra.adjudicacion.enums.EstadoAdjudicacion;

/**
 * Adjudicación.
 *
 * @param id                      id
 * @param rfqId                   RFQ
 * @param rfqConsecutivo          consecutivo de la RFQ
 * @param analisisId              análisis en que se basó
 * @param cotizacionId            cotización elegida
 * @param proveedorId             proveedor elegido
 * @param proveedorRazonSocial    razón social
 * @param montoTotal              total con impuestos
 * @param sigueRecomendacion      si eligió la recomendada
 * @param justificacion           justificación
 * @param estado                  estado
 * @param politicaAprobacionId    política que exigió aprobación
 * @param usuarioId               comprador que adjudicó
 * @param decididoEn              fecha de adjudicación
 * @param aprobadoPorUsuarioId    quién aprobó o rechazó
 * @param aprobadoEn              fecha de la decisión
 * @param observacionesAprobacion comentario del aprobador
 */
public record AdjudicacionResponse(Long id, Long rfqId, String rfqConsecutivo, Long analisisId, Long cotizacionId,
        Long proveedorId, String proveedorRazonSocial, BigDecimal montoTotal, boolean sigueRecomendacion,
        String justificacion, EstadoAdjudicacion estado, Long politicaAprobacionId, Long usuarioId,
        LocalDateTime decididoEn, Long aprobadoPorUsuarioId, LocalDateTime aprobadoEn, String observacionesAprobacion) {

    /**
     * Convierte la entidad (RFQ, cotización y proveedor cargados).
     *
     * @param a adjudicación
     * @return DTO
     */
    public static AdjudicacionResponse of(Adjudicacion a) {
        return new AdjudicacionResponse(a.getId(), a.getRfq().getId(), a.getRfq().getConsecutivo(), a.getAnalisisId(),
                a.getCotizacion().getId(), a.getCotizacion().getProveedor().getId(),
                a.getCotizacion().getProveedor().getRazonSocial(), a.getMontoTotal(), a.isSigueRecomendacion(),
                a.getJustificacion(), a.getEstado(), a.getPoliticaAprobacionId(), a.getUsuarioId(), a.getDecididoEn(),
                a.getAprobadoPorUsuarioId(), a.getAprobadoEn(), a.getObservacionesAprobacion());
    }
}
