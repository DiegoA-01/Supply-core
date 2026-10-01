package com.proyecto.supply_core.auditoria.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.proyecto.supply_core.auditoria.entity.Auditoria;

/**
 * Acceso a la tabla {@code auditoria}. Las búsquedas con filtros opcionales usan
 * {@link JpaSpecificationExecutor} (ver {@code AuditoriaSpecs}).
 */
public interface AuditoriaRepository extends JpaRepository<Auditoria, Long>, JpaSpecificationExecutor<Auditoria> {
}
