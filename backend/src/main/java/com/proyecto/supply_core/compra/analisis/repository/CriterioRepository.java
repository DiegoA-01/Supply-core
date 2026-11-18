package com.proyecto.supply_core.compra.analisis.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.proyecto.supply_core.compra.analisis.entity.CriterioEvaluacion;

/** Acceso a la tabla {@code criterio_evaluacion}. */
public interface CriterioRepository extends JpaRepository<CriterioEvaluacion, Long> {

    /**
     * Todos los criterios ordenados por código.
     *
     * @return criterios
     */
    List<CriterioEvaluacion> findAllByOrderByCodigoAsc();
}
