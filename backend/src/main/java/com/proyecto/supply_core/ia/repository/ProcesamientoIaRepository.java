package com.proyecto.supply_core.ia.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.proyecto.supply_core.ia.entity.ProcesamientoIa;

/** Acceso a la tabla {@code procesamiento_ia}. */
public interface ProcesamientoIaRepository extends JpaRepository<ProcesamientoIa, Long> {

    /**
     * Procesamiento de una cotización.
     *
     * @param cotizacionId cotización
     * @return procesamiento, si existe
     */
    Optional<ProcesamientoIa> findByCotizacionId(Long cotizacionId);

    /**
     * Procesamientos de varias cotizaciones (estado de lectura de una RFQ).
     *
     * @param cotizacionIds cotizaciones
     * @return procesamientos existentes
     */
    List<ProcesamientoIa> findByCotizacionIdIn(Collection<Long> cotizacionIds);
}
