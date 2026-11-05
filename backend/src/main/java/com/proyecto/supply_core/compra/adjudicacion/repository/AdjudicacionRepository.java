package com.proyecto.supply_core.compra.adjudicacion.repository;

import java.util.Collection;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.proyecto.supply_core.compra.adjudicacion.entity.Adjudicacion;
import com.proyecto.supply_core.compra.adjudicacion.enums.EstadoAdjudicacion;

import jakarta.persistence.LockModeType;

/** Acceso a la tabla {@code adjudicacion}. */
public interface AdjudicacionRepository extends JpaRepository<Adjudicacion, Long> {

    /**
     * Busca por id con RFQ, cotización y proveedor.
     *
     * @param id adjudicación
     * @return adjudicación, si existe
     */
    @EntityGraph(attributePaths = { "rfq", "cotizacion", "cotizacion.proveedor" })
    Optional<Adjudicacion> findDetalleById(Long id);

    /**
     * Bloquea la fila hasta el fin de la transacción (dos aprobadores no deciden a la vez).
     *
     * @param id adjudicación
     * @return adjudicación bloqueada, si existe
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Adjudicacion a where a.id = :id")
    Optional<Adjudicacion> bloquear(@Param("id") Long id);

    /**
     * Indica si la RFQ ya tiene una adjudicación en alguno de los estados.
     *
     * @param rfqId   RFQ
     * @param estados estados que cuentan como vigentes
     * @return {@code true} si existe
     */
    boolean existsByRfq_IdAndEstadoIn(Long rfqId, Collection<EstadoAdjudicacion> estados);

    /**
     * Última adjudicación de la RFQ.
     *
     * @param rfqId RFQ
     * @return adjudicación más reciente, si hay
     */
    @EntityGraph(attributePaths = { "rfq", "cotizacion", "cotizacion.proveedor" })
    Optional<Adjudicacion> findFirstByRfq_IdOrderByIdDesc(Long rfqId);

    /**
     * Adjudicaciones pendientes cuya política exige uno de los roles, sin las del propio usuario.
     *
     * @param roles     roles del aprobador
     * @param usuarioId aprobador (se excluyen sus adjudicaciones)
     * @param pageable  página y orden
     * @return pendientes
     */
    @EntityGraph(attributePaths = { "rfq", "cotizacion", "cotizacion.proveedor" })
    @Query("""
            select a from Adjudicacion a
            where a.estado = com.proyecto.supply_core.compra.adjudicacion.enums.EstadoAdjudicacion.PENDIENTE_APROBACION
              and a.usuarioId <> :usuarioId
              and a.politicaAprobacionId in (
                    select p.id from PoliticaAprobacion p where p.rolAprobador.codigo in :roles)
            """)
    Page<Adjudicacion> pendientesPara(@Param("roles") Collection<String> roles, @Param("usuarioId") Long usuarioId,
            Pageable pageable);
}
