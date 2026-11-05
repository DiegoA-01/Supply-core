package com.proyecto.supply_core.plataforma.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.proyecto.supply_core.plataforma.entity.AuditoriaPlataforma;

/** Acceso a la tabla {@code auditoria_plataforma}. */
public interface AuditoriaPlataformaRepository extends JpaRepository<AuditoriaPlataforma, Long> {

    /**
     * Registros de auditoría, el más reciente primero, con filtro opcional por empresa.
     *
     * @param empresaId empresa afectada ({@code null} = todas)
     * @param pageable  página
     * @return página de registros
     */
    @Query("""
            select a from AuditoriaPlataforma a
            where (:empresaId is null or a.empresaId = :empresaId)
            order by a.creadoEn desc, a.id desc
            """)
    Page<AuditoriaPlataforma> buscar(@Param("empresaId") Long empresaId, Pageable pageable);
}
