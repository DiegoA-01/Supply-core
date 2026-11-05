package com.proyecto.supply_core.compra.analisis.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.proyecto.supply_core.compra.analisis.entity.Analisis;
import com.proyecto.supply_core.compra.analisis.enums.EstadoAnalisis;

/** Acceso a la tabla {@code analisis_cotizacion}. */
public interface AnalisisRepository extends JpaRepository<Analisis, Long> {

    /**
     * Último análisis de una RFQ.
     *
     * @param rfqId RFQ
     * @return análisis más reciente, si hay
     */
    Optional<Analisis> findFirstByRfq_IdOrderByIdDesc(Long rfqId);

    /**
     * Análisis de una RFQ en un estado.
     *
     * @param rfqId  RFQ
     * @param estado estado
     * @return análisis, si hay
     */
    Optional<Analisis> findFirstByRfq_IdAndEstado(Long rfqId, EstadoAnalisis estado);
}
