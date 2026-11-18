package com.proyecto.supply_core.informe.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.proyecto.supply_core.compra.adjudicacion.entity.Adjudicacion;
import com.proyecto.supply_core.compra.cotizacion.entity.Cotizacion;
import com.proyecto.supply_core.compra.rfq.entity.Rfq;
import com.proyecto.supply_core.compra.solicitud.entity.SolicitudCompra;
import com.proyecto.supply_core.informe.entity.InformeCompras;

/**
 * Informes de compras y las consultas de solo lectura con que se arma la foto del periodo. Todas pasan por
 * entidades con {@code @TenantId}: solo devuelven datos de la empresa de la sesión.
 */
public interface InformeComprasRepository extends JpaRepository<InformeCompras, Long> {

    /**
     * Informes de la empresa, del más reciente al más antiguo.
     *
     * @param pageable página
     * @return página de informes
     */
    @Query("select i from InformeCompras i order by i.creadoEn desc, i.id desc")
    Page<InformeCompras> recientes(Pageable pageable);

    /**
     * Solicitudes enviadas a aprobación en el periodo (los borradores no cuentan).
     *
     * @param desde inicio (incluido)
     * @param hasta fin (excluido)
     * @return solicitudes, la más reciente primero
     */
    @Query("""
            select s from SolicitudCompra s
            where s.enviadoEn >= :desde and s.enviadoEn < :hasta
            order by s.enviadoEn desc
            """)
    List<SolicitudCompra> solicitudesEnviadas(@Param("desde") LocalDateTime desde, @Param("hasta") LocalDateTime hasta);

    /**
     * RFQ creadas en el periodo, con su solicitud.
     *
     * @param desde inicio (incluido)
     * @param hasta fin (excluido)
     * @return RFQ, la más reciente primero
     */
    @Query("""
            select r from Rfq r join fetch r.solicitud
            where r.creadoEn >= :desde and r.creadoEn < :hasta
            order by r.creadoEn desc
            """)
    List<Rfq> rfqsCreadas(@Param("desde") LocalDateTime desde, @Param("hasta") LocalDateTime hasta);

    /**
     * Cotizaciones que los proveedores enviaron en el periodo.
     *
     * @param desde inicio (incluido)
     * @param hasta fin (excluido)
     * @return cotizaciones con su RFQ y proveedor, la más reciente primero
     */
    @Query("""
            select c from Cotizacion c join fetch c.rfq join fetch c.proveedor
            where c.enviadoEn >= :desde and c.enviadoEn < :hasta
            order by c.enviadoEn desc
            """)
    List<Cotizacion> cotizacionesRecibidas(@Param("desde") LocalDateTime desde, @Param("hasta") LocalDateTime hasta);

    /**
     * Adjudicaciones decididas en el periodo.
     *
     * @param desde inicio (incluido)
     * @param hasta fin (excluido)
     * @return adjudicaciones con su RFQ y la cotización elegida, la más reciente primero
     */
    @Query("""
            select a from Adjudicacion a join fetch a.rfq join fetch a.cotizacion c join fetch c.proveedor
            where a.decididoEn >= :desde and a.decididoEn < :hasta
            order by a.decididoEn desc
            """)
    List<Adjudicacion> adjudicaciones(@Param("desde") LocalDateTime desde, @Param("hasta") LocalDateTime hasta);

    /**
     * Cuántas cotizaciones enviadas (no borradores) tiene una RFQ.
     *
     * @param rfqId RFQ
     * @return cantidad
     */
    @Query("""
            select count(c) from Cotizacion c
            where c.rfq.id = :rfqId
              and c.estado <> com.proyecto.supply_core.compra.cotizacion.enums.EstadoCotizacion.BORRADOR
            """)
    long cotizacionesDeRfq(@Param("rfqId") Long rfqId);
}
