package com.proyecto.supply_core.documento.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.proyecto.supply_core.documento.entity.Documento;

/** Acceso a la tabla {@code documento} (solo metadata). */
public interface DocumentoRepository extends JpaRepository<Documento, Long> {

    /**
     * Documentos de un registro, más recientes primero.
     *
     * @param entidadTipo tipo de entidad ({@code EntidadDocumento.name()})
     * @param entidadId   id del registro
     * @return documentos
     */
    List<Documento> findByEntidadTipoAndEntidadIdOrderByCreadoEnDesc(String entidadTipo, Long entidadId);
}
