package com.proyecto.supply_core.plataforma.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.proyecto.supply_core.plataforma.entity.Suscripcion;

/** Acceso a la tabla {@code suscripcion}. */
public interface SuscripcionRepository extends JpaRepository<Suscripcion, Long> {

    /**
     * Suscripción de una empresa.
     *
     * @param empresaId empresa
     * @return suscripción, si existe
     */
    Optional<Suscripcion> findByEmpresaId(Long empresaId);

    /**
     * Todas las suscripciones, la de la empresa más nueva primero.
     *
     * @return suscripciones con su plan
     */
    List<Suscripcion> findAllByOrderByEmpresaIdDesc();

    /**
     * Cuántas empresas usan un plan.
     *
     * @param planId plan
     * @return cantidad de suscripciones
     */
    long countByPlanId(Long planId);
}
