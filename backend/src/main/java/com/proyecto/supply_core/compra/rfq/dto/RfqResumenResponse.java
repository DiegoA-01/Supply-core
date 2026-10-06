package com.proyecto.supply_core.compra.rfq.dto;

import java.time.LocalDateTime;

import com.proyecto.supply_core.compra.rfq.entity.Rfq;
import com.proyecto.supply_core.compra.rfq.enums.EstadoRfq;

/**
 * RFQ para listados (interno y portal): sin líneas ni invitados.
 *
 * @param id          id
 * @param consecutivo RFQ-000001
 * @param estado      estado
 * @param fechaLimite fecha límite para cotizar
 * @param abiertaEn   fecha de apertura
 * @param cerradaEn   fecha de cierre
 */
public record RfqResumenResponse(Long id, String consecutivo, EstadoRfq estado, LocalDateTime fechaLimite,
        LocalDateTime abiertaEn, LocalDateTime cerradaEn) {

    /**
     * Convierte la entidad.
     *
     * @param r RFQ
     * @return DTO
     */
    public static RfqResumenResponse of(Rfq r) {
        return new RfqResumenResponse(r.getId(), r.getConsecutivo(), r.getEstado(), r.getFechaLimite(),
                r.getAbiertaEn(), r.getCerradaEn());
    }
}
