package com.proyecto.supply_core.compra.rfq.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.proyecto.supply_core.compra.rfq.entity.Rfq;
import com.proyecto.supply_core.compra.rfq.enums.EstadoRfq;

/**
 * RFQ vista por un proveedor invitado: sin la lista de invitados ni datos internos de la solicitud.
 *
 * @param id          id
 * @param consecutivo RFQ-000001
 * @param estado      estado
 * @param fechaLimite fecha límite para cotizar
 * @param condiciones condiciones comerciales
 * @param lineas      productos a cotizar
 */
public record RfqPortalResponse(Long id, String consecutivo, EstadoRfq estado, LocalDateTime fechaLimite,
        String condiciones, List<RfqLineaResponse> lineas) {

    /**
     * Convierte la entidad (líneas cargadas).
     *
     * @param r RFQ
     * @return DTO
     */
    public static RfqPortalResponse of(Rfq r) {
        return new RfqPortalResponse(r.getId(), r.getConsecutivo(), r.getEstado(), r.getFechaLimite(),
                r.getCondiciones(), r.getLineas().stream().map(RfqLineaResponse::of).toList());
    }
}
