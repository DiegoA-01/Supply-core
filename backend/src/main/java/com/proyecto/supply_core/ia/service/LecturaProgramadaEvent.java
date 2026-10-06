package com.proyecto.supply_core.ia.service;

/**
 * Evento: hay una cotización por archivo lista para que la IA la lea. Lo atiende
 * {@link ProcesadorExtraccion} después del commit y en otro hilo.
 *
 * @param procesamientoId id del procesamiento
 */
public record LecturaProgramadaEvent(Long procesamientoId) {
}
