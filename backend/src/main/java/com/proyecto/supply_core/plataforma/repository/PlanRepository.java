package com.proyecto.supply_core.plataforma.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.proyecto.supply_core.plataforma.entity.Plan;

/** Acceso a las tablas {@code plan} y {@code plan_modulo}. */
public interface PlanRepository extends JpaRepository<Plan, Long> {

    /**
     * Busca un plan por código sin distinguir mayúsculas.
     *
     * @param codigo código del plan
     * @return plan, si existe
     */
    Optional<Plan> findByCodigoIgnoreCase(String codigo);

    /**
     * Indica si ya existe un plan con ese código.
     *
     * @param codigo código del plan
     * @return {@code true} si existe
     */
    boolean existsByCodigoIgnoreCase(String codigo);

    /**
     * Todos los planes por código.
     *
     * @return planes con sus módulos
     */
    List<Plan> findAllByOrderByCodigoAsc();
}
