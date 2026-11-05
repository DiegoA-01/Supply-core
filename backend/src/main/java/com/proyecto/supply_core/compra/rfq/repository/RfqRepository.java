package com.proyecto.supply_core.compra.rfq.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.proyecto.supply_core.compra.rfq.entity.Rfq;
import com.proyecto.supply_core.compra.rfq.enums.EstadoRfq;

import jakarta.persistence.LockModeType;

/** Acceso a la tabla {@code rfq}. */
public interface RfqRepository extends JpaRepository<Rfq, Long> {

    /**
     * Busca por id trayendo solicitud, líneas (producto y unidad) e invitados.
     *
     * @param id id de la RFQ
     * @return RFQ completa, si existe
     */
    @EntityGraph(attributePaths = { "solicitud", "lineas", "lineas.producto", "lineas.unidadMedida", "invitados",
            "invitados.proveedor" })
    Optional<Rfq> findWithDetalleById(Long id);

    /**
     * Bloquea la fila hasta el fin de la transacción (cambios de estado sin carreras).
     *
     * @param id id de la RFQ
     * @return RFQ bloqueada, si existe
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Rfq r where r.id = :id")
    Optional<Rfq> bloquear(@Param("id") Long id);

    /**
     * Indica si la solicitud ya tiene una RFQ que no esté en el estado dado (normalmente CANCELADA).
     *
     * @param solicitudId solicitud
     * @param estado      estado que no cuenta
     * @return {@code true} si ya hay una RFQ vigente
     */
    boolean existsBySolicitud_IdAndEstadoNot(Long solicitudId, EstadoRfq estado);

    /**
     * RFQ abiertas cuya fecha límite ya pasó (job de cierre automático).
     *
     * @param estado estado buscado (ABIERTA)
     * @param ahora  momento de corte
     * @return ids de las RFQ vencidas
     */
    @Query("select r.id from Rfq r where r.estado = :estado and r.fechaLimite <= :ahora")
    List<Long> vencidas(@Param("estado") EstadoRfq estado, @Param("ahora") LocalDateTime ahora);

    /**
     * Búsqueda paginada con filtros opcionales ({@code null} = no filtrar).
     *
     * @param texto       coincidencia parcial en el consecutivo
     * @param estado      estado
     * @param solicitudId solicitud de origen
     * @param pageable    página y orden
     * @return página de RFQ
     */
    @Query("""
            select r from Rfq r
            where (:texto is null or r.consecutivo like concat('%', :texto, '%'))
              and (:estado is null or r.estado = :estado)
              and (:solicitudId is null or r.solicitud.id = :solicitudId)
            """)
    Page<Rfq> buscar(@Param("texto") String texto, @Param("estado") EstadoRfq estado,
            @Param("solicitudId") Long solicitudId, Pageable pageable);

    /**
     * RFQ visibles para un proveedor: aquellas en las que está invitado y ya no son BORRADOR.
     *
     * @param proveedorId proveedor
     * @param pageable    página y orden
     * @return página de RFQ
     */
    @Query("""
            select r from Rfq r join r.invitados i
            where i.proveedor.id = :proveedorId
              and r.estado <> com.proyecto.supply_core.compra.rfq.enums.EstadoRfq.BORRADOR
            """)
    Page<Rfq> invitadasDe(@Param("proveedorId") Long proveedorId, Pageable pageable);
}
