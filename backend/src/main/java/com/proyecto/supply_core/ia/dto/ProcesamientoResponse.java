package com.proyecto.supply_core.ia.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import com.proyecto.supply_core.ia.entity.ExtraccionCampo;
import com.proyecto.supply_core.ia.entity.ProcesamientoIa;
import com.proyecto.supply_core.ia.enums.EstadoProcesamiento;

/**
 * Estado de la lectura de una cotización y los datos leídos con su confianza.
 *
 * @param id              id del procesamiento
 * @param cotizacionId    cotización
 * @param estado          estado
 * @param modelo          IA que la leyó
 * @param intentos        intentos realizados
 * @param error           mensaje si falló o si falta revisión
 * @param creadoEn        fecha de creación
 * @param finalizadoEn    fecha de fin
 * @param umbralConfianza confianza mínima de un dato crítico
 * @param campos          datos leídos
 * @param alertas         alertas abiertas que el comprador debe resolver (p. ej. {@code TOTAL_NO_CUADRA})
 */
public record ProcesamientoResponse(Long id, Long cotizacionId, EstadoProcesamiento estado, String modelo, int intentos,
        String error, LocalDateTime creadoEn, LocalDateTime finalizadoEn, BigDecimal umbralConfianza,
        List<Campo> campos, List<String> alertas) {

    /**
     * Respuesta sin alertas.
     *
     * @param id              id del procesamiento
     * @param cotizacionId    cotización
     * @param estado          estado
     * @param modelo          IA que la leyó
     * @param intentos        intentos realizados
     * @param error           mensaje si falló o si falta revisión
     * @param creadoEn        fecha de creación
     * @param finalizadoEn    fecha de fin
     * @param umbralConfianza confianza mínima de un dato crítico
     * @param campos          datos leídos
     */
    public ProcesamientoResponse(Long id, Long cotizacionId, EstadoProcesamiento estado, String modelo, int intentos,
            String error, LocalDateTime creadoEn, LocalDateTime finalizadoEn, BigDecimal umbralConfianza,
            List<Campo> campos) {
        this(id, cotizacionId, estado, modelo, intentos, error, creadoEn, finalizadoEn, umbralConfianza, campos,
                List.of());
    }

    /**
     * Convierte la entidad.
     *
     * @param p      procesamiento
     * @param campos datos leídos
     * @param umbral confianza mínima
     * @return DTO
     */
    public static ProcesamientoResponse of(ProcesamientoIa p, List<ExtraccionCampo> campos, BigDecimal umbral) {
        return new ProcesamientoResponse(p.getId(), p.getCotizacionId(), p.getEstado(), p.getModelo(), p.getIntentos(),
                p.getError(), p.getCreadoEn(), p.getFinalizadoEn(), umbral,
                campos.stream().map(c -> Campo.of(c, umbral)).toList(),
                campos.stream().filter(c -> c.getCotizacionLineaId() == null && "totalImpreso".equals(c.getCampo())
                        && c.pendiente(umbral)).map(c -> "TOTAL_NO_CUADRA").toList());
    }

    /**
     * Dato leído.
     *
     * @param id                id del campo
     * @param cotizacionLineaId línea a la que pertenece ({@code null} = cabecera)
     * @param campo             nombre del dato
     * @param valorExtraido     lo que leyó la IA
     * @param confianza         confianza 0–1
     * @param critico           si afecta el dinero o la cantidad
     * @param pendiente         {@code true} si falta que una persona lo confirme
     * @param valorConfirmado   valor confirmado o corregido por una persona
     */
    public record Campo(Long id, Long cotizacionLineaId, String campo, String valorExtraido, BigDecimal confianza,
            boolean critico, boolean pendiente, String valorConfirmado) {

        /**
         * Convierte la entidad.
         *
         * @param c      campo
         * @param umbral confianza mínima
         * @return DTO
         */
        static Campo of(ExtraccionCampo c, BigDecimal umbral) {
            return new Campo(c.getId(), c.getCotizacionLineaId(), c.getCampo(), c.getValorExtraido(), c.getConfianza(),
                    c.isCritico(), c.pendiente(umbral), c.getValorConfirmado());
        }
    }
}
