package com.proyecto.supply_core.empresa.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.proyecto.supply_core.empresa.entity.PoliticaAprobacion;
import com.proyecto.supply_core.empresa.enums.TipoPolitica;

/** Acceso a la tabla {@code politica_aprobacion}. Siempre trae el rol aprobador. */
public interface PoliticaAprobacionRepository extends JpaRepository<PoliticaAprobacion, Long> {

    /**
     * Todas las políticas de la empresa, por tipo y monto inicial.
     *
     * @param empresaId empresa
     * @return políticas con su rol
     */
    @EntityGraph(attributePaths = "rolAprobador")
    List<PoliticaAprobacion> findByEmpresaIdOrderByTipoAscMontoDesdeAsc(Long empresaId);

    /**
     * Políticas activas de un tipo (para detectar superposición y resolver la aplicable).
     *
     * @param empresaId empresa
     * @param tipo      tipo de operación
     * @return políticas activas con su rol
     */
    @EntityGraph(attributePaths = "rolAprobador")
    List<PoliticaAprobacion> findByEmpresaIdAndTipoAndActivoTrue(Long empresaId, TipoPolitica tipo);

    /**
     * Busca una política trayendo su rol.
     *
     * @param id id de la política
     * @return política, si existe
     */
    @EntityGraph(attributePaths = "rolAprobador")
    Optional<PoliticaAprobacion> findWithRolById(Long id);
}
