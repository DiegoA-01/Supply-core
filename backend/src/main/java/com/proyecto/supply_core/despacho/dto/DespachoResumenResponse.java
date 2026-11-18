package com.proyecto.supply_core.despacho.dto;

import java.time.LocalDateTime;

import com.proyecto.supply_core.despacho.entity.Despacho;
import com.proyecto.supply_core.despacho.enums.EstadoDespacho;

/**
 * Despacho para listados (sin líneas).
 *
 * @param id                id
 * @param consecutivo       DES-000001
 * @param estado            estado
 * @param bodegaId          bodega de salida
 * @param cliente           cliente o destinatario
 * @param referenciaExterna factura o ticket externo
 * @param confirmadoEn      fecha de confirmación
 * @param creadoEn          fecha de creación
 */
public record DespachoResumenResponse(Long id, String consecutivo, EstadoDespacho estado, Long bodegaId,
        String cliente, String referenciaExterna, LocalDateTime confirmadoEn, LocalDateTime creadoEn) {

    /**
     * Convierte la entidad.
     *
     * @param d despacho
     * @return DTO
     */
    public static DespachoResumenResponse of(Despacho d) {
        return new DespachoResumenResponse(d.getId(), d.getConsecutivo(), d.getEstado(), d.getBodega().getId(),
                d.getCliente(), d.getReferenciaExterna(), d.getConfirmadoEn(), d.getCreadoEn());
    }
}
