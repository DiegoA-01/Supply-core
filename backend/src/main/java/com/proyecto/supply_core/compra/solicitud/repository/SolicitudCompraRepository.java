package com.proyecto.supply_core.compra.solicitud.repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
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
     * Suma el monto estimado de las solicitudes de un centro de costo cuya fecha requerida está en el rango
     * (ambos extremos incluidos) y cuyo estado es uno de los dados. Base del control de presupuesto (18-a).
     *
     * @param centroCostoId centro de costo
     * @param desde         primer día del rango
     * @param hasta         último día del rango
     * @param estados       estados que comprometen presupuesto
     * @param excluirId     solicitud que no se cuenta ({@code null} = contar todas)
     * @return suma, o {@code null} si no hay solicitudes
     */
    @Query("""
            select sum(s.montoEstimado) from SolicitudCompra s
            where s.centroCostoId = :centroCostoId
              and s.fechaRequerida >= :desde and s.fechaRequerida <= :hasta
              and s.estado in :estados
              and (:excluirId is null or s.id <> :excluirId)
            """)
    BigDecimal comprometido(@Param("centroCostoId") Long centroCostoId, @Param("desde") LocalDate desde,
            @Param("hasta") LocalDate hasta, @Param("estados") Collection<EstadoSolicitud> estados,
            @Param("excluirId") Long excluirId);

    /**
     * Consecutivos de otras solicitudes abiertas del mismo centro de costo que ya piden alguno de los productos.
     * Sirve para bloquear solicitudes duplicadas (18-a).
     *
     * @param centroCostoId centro de costo
     * @param productoIds   productos de la solicitud que se envía
     * @param estados       estados que cuentan como "ya pedido"
     * @param excluirId     la propia solicitud
     * @return consecutivos sin repetir, ordenados
     */
    @Query("""
            select distinct s.consecutivo from SolicitudCompra s join s.lineas l
            where s.centroCostoId = :centroCostoId
              and s.id <> :excluirId
              and s.estado in :estados
              and l.producto.id in :productoIds
            order by s.consecutivo
            """)
    List<String> consecutivosConProductos(@Param("centroCostoId") Long centroCostoId,
            @Param("productoIds") Collection<Long> productoIds, @Param("estados") Collection<EstadoSolicitud> estados,
            @Param("excluirId") Long excluirId);

    /**
     * Ids de las solicitudes PENDIENTE_APROBACION que ya no se han escalado y se enviaron en o antes del corte
     * (SLA de aprobación, 18-c). El Service las relee con bloqueo antes de tocarlas.
     *
     * @param corte momento a partir del cual una solicitud ya merece aviso
     * @return ids, la más antigua primero
     */
    @Query("""
            select s.id from SolicitudCompra s
            where s.estado = com.proyecto.supply_core.compra.solicitud.enums.EstadoSolicitud.PENDIENTE_APROBACION
              and s.escaladaEn is null and s.enviadoEn <= :corte
            order by s.enviadoEn
            """)
    List<Long> idsPendientesDesde(@Param("corte") LocalDateTime corte);

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
              and ((s.apruebaDueno = false and s.politicaAprobacionId in (
                        select p.id from PoliticaAprobacion p where p.rolAprobador.codigo in :roles))
                   or (s.apruebaDueno = true and exists (
                        select r.id from Rol r where r.codigo = 'ADMIN_SISTEMA' and r.codigo in :roles)))
            """)
    Page<SolicitudCompra> pendientesPara(@Param("roles") Collection<String> roles,
            @Param("usuarioId") Long usuarioId, Pageable pageable);
}
