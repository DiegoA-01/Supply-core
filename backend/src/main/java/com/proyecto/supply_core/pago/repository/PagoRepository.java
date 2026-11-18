package com.proyecto.supply_core.pago.repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.proyecto.supply_core.pago.entity.Pago;
import com.proyecto.supply_core.pago.enums.EstadoPago;

import jakarta.persistence.LockModeType;

/** Acceso a la tabla {@code pago}. */
public interface PagoRepository extends JpaRepository<Pago, Long> {

    /**
     * Busca por id con la orden y su proveedor.
     *
     * @param id pago
     * @return pago, si existe
     */
    @EntityGraph(attributePaths = { "orden", "orden.proveedor" })
    Optional<Pago> findDetalleById(Long id);

    /**
     * Bloquea la fila hasta el fin de la transacción.
     *
     * @param id pago
     * @return pago bloqueado, si existe
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Pago p where p.id = :id")
    Optional<Pago> bloquear(@Param("id") Long id);

    /**
     * Suma de los pagos REGISTRADOS de todas las versiones de una orden.
     *
     * @param consecutivo OC-000001
     * @return pagado (0 si no hay pagos)
     */
    @Query("""
            select coalesce(sum(p.valor), 0) from Pago p
            where p.orden.consecutivo = :consecutivo
              and p.estado = com.proyecto.supply_core.pago.enums.EstadoPago.REGISTRADO
            """)
    BigDecimal pagadoPorConsecutivo(@Param("consecutivo") String consecutivo);

    /**
     * Pagos de todas las versiones de una orden, del más antiguo al más reciente.
     *
     * @param consecutivo OC-000001
     * @return pagos (incluye anulados)
     */
    @EntityGraph(attributePaths = { "orden", "orden.proveedor" })
    List<Pago> findByOrden_ConsecutivoOrderByFechaPagoAscIdAsc(String consecutivo);

    /**
     * Búsqueda con filtros opcionales ({@code null} = no filtrar).
     *
     * @param estado             estado
     * @param evidenciaPendiente solo con (o sin) comprobante pendiente
     * @param proveedorId        proveedor de la orden
     * @param desde              fecha de pago desde (incluida)
     * @param hasta              fecha de pago hasta (incluida)
     * @param pageable           página y orden
     * @return página de pagos
     */
    @EntityGraph(attributePaths = { "orden", "orden.proveedor" })
    @Query("""
            select p from Pago p
            where (:estado is null or p.estado = :estado)
              and (:evidenciaPendiente is null or p.evidenciaPendiente = :evidenciaPendiente)
              and (:proveedorId is null or p.orden.proveedor.id = :proveedorId)
              and (:desde is null or p.fechaPago >= :desde)
              and (:hasta is null or p.fechaPago <= :hasta)
            """)
    Page<Pago> buscar(@Param("estado") EstadoPago estado, @Param("evidenciaPendiente") Boolean evidenciaPendiente,
            @Param("proveedorId") Long proveedorId, @Param("desde") LocalDate desde, @Param("hasta") LocalDate hasta,
            Pageable pageable);

    /**
     * Suma de los anticipos REGISTRADOS de todas las versiones de una orden.
     *
     * @param consecutivo OC-000001
     * @return anticipado (0 si no hay)
     */
    @Query("""
            select coalesce(sum(p.valor), 0) from Pago p
            where p.orden.consecutivo = :consecutivo
              and p.estado = com.proyecto.supply_core.pago.enums.EstadoPago.REGISTRADO
              and p.tipo = com.proyecto.supply_core.pago.enums.TipoPago.ANTICIPO
            """)
    BigDecimal anticipadoPorConsecutivo(@Param("consecutivo") String consecutivo);

    /**
     * Indica si la orden ya tiene un pago final registrado (no anulado).
     *
     * @param consecutivo OC-000001
     * @return {@code true} si existe
     */
    @Query("""
            select count(p) > 0 from Pago p
            where p.orden.consecutivo = :consecutivo
              and p.estado = com.proyecto.supply_core.pago.enums.EstadoPago.REGISTRADO
              and p.tipo = com.proyecto.supply_core.pago.enums.TipoPago.PAGO_FINAL
            """)
    boolean hayPagoFinalRegistrado(@Param("consecutivo") String consecutivo);
}
