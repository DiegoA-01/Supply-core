package com.proyecto.supply_core.compra.analisis.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.proyecto.supply_core.compra.analisis.entity.ConfigPesos;

/** Acceso a la tabla {@code config_pesos_evaluacion}. */
public interface ConfigPesosRepository extends JpaRepository<ConfigPesos, Long> {

    /**
     * Versión vigente (activa más reciente) con sus pesos.
     *
     * @param empresaId empresa
     * @return versión vigente, si hay
     */
    @EntityGraph(attributePaths = "detalles")
    Optional<ConfigPesos> findFirstByEmpresaIdAndActivoTrueOrderByVigenteDesdeDesc(Long empresaId);

    /**
     * Una versión con sus pesos.
     *
     * @param id versión
     * @return versión, si existe
     */
    @EntityGraph(attributePaths = "detalles")
    Optional<ConfigPesos> findWithDetallesById(Long id);
}
