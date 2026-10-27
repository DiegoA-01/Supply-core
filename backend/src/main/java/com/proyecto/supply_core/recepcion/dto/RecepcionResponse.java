package com.proyecto.supply_core.recepcion.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.proyecto.supply_core.recepcion.entity.Recepcion;

/**
 * Una recepción con sus líneas.
 *
 * @param id               id
 * @param ordenId          orden recibida
 * @param ordenConsecutivo OC-000001
 * @param bodegaId         bodega donde se recibió
 * @param bodegaNombre     nombre de la bodega
 * @param usuarioId        quién recibió
 * @param recibidoEn       cuándo
 * @param observaciones    comentario general
 * @param lineas           líneas recibidas
 */
public record RecepcionResponse(Long id, Long ordenId, String ordenConsecutivo, Long bodegaId, String bodegaNombre,
        Long usuarioId, LocalDateTime recibidoEn, String observaciones, List<LineaRecepcionResponse> lineas) {

    /**
     * Convierte la entidad.
     *
     * @param r recepción con orden, bodega y líneas cargadas
     * @return DTO
     */
    public static RecepcionResponse of(Recepcion r) {
        return new RecepcionResponse(r.getId(), r.getOrden().getId(), r.getOrden().getConsecutivo(),
                r.getBodega().getId(), r.getBodega().getNombre(), r.getUsuarioId(), r.getRecibidoEn(),
                r.getObservaciones(), r.getLineas().stream().map(LineaRecepcionResponse::of).toList());
    }
}
