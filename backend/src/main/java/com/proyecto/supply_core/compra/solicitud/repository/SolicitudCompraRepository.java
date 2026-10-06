package com.proyecto.supply_core.compra.solicitud.repository;

import java.util.Collection;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.proyecto.supply_core.compra.solicitud.entity.SolicitudCompra;
import com.proyecto.supply_core.compra.solicitud.enums.EstadoSolicitud;

import jakarta.persistence.LockModeType;

/** Acceso a la tabla {@code solicitud_compra}. */
public interface SolicitudCompraRepository extends JpaRepository<SolicitudCompra, Long> {

    /**
     * Busca por id trayendo líneas, productos y unidades.
     *
     * @param id id de la solicitud
     * @return solicitud completa, si existe
     */
    @EntityGraph(attributePaths = { "lineas", "lineas.producto", "lineas.unidadMedida" })
    Optional<SolicitudCompra> findWithLineasById(Long id);

    /**
     * Busca por id bloqueando la fila hasta el fin de la transacción ({@code SELECT ... FOR UPDATE}):
     * evita que dos aprobadores decidan a la vez o que se edite mientras se decide.
     *
     * @param id id de la solicitud
     * @return solicitud bloqueada, si existe
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from SolicitudCompra s where s.id = :id")
    Optional<SolicitudCompra> bloquear(@Param("id") Long id);

    /**
     * Búsqueda paginada con filtros opcionales ({@code null} = no filtrar).
     *
     * @param texto         coincidencia parcial en el consecutivo
     * @param estado        estado
     * @param solicitanteId usuario solicitante
     * @param centroCostoId centro de costo
     * @param pageable      página y orden
     * @return página de solicitudes
     */
    @Query("""
            select s from SolicitudCompra s
            where (:texto is null or s.consecutivo like concat('%', :texto, '%'))
              and (:estado is null or s.estado = :estado)
              and (:solicitanteId is null or s.usuarioSolicitanteId = :solicitanteId)
              and (:centroCostoId is null or s.centroCostoId = :centroCostoId)
            """)
    Page<SolicitudCompra> buscar(@Param("texto") String texto, @Param("estado") EstadoSolicitud estado,
            @Param("solicitanteId") Long solicitanteId, @Param("centroCostoId") Long centroCostoId,
            Pageable pageable);

    /**
     * Solicitudes PENDIENTE_APROBACION cuya política exige uno de los roles dados,
     * sin las del propio usuario (segregación de funciones).
     *
     * @param roles     códigos de rol del aprobador
     * @param usuarioId aprobador (se excluyen sus propias solicitudes)
     * @param pageable  página y orden
     * @return página de pendientes
     */
    @Query("""
            select s from SolicitudCompra s
            where s.estado = com.proyecto.supply_core.compra.solicitud.enums.EstadoSolicitud.PENDIENTE_APROBACION
              and s.usuarioSolicitanteId <> :usuarioId
              and s.politicaAprobacionId in (
                    select p.id from PoliticaAprobacion p where p.rolAprobador.codigo in :roles)
            """)
    Page<SolicitudCompra> pendientesPara(@Param("roles") Collection<String> roles,
            @Param("usuarioId") Long usuarioId, Pageable pageable);
}
