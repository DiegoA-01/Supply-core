package com.proyecto.supply_core.venta.repository;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.proyecto.supply_core.venta.entity.Venta;
import com.proyecto.supply_core.venta.enums.EstadoVenta;

import jakarta.persistence.LockModeType;

/** Ventas de la empresa del contexto ({@code @TenantId}). */
public interface VentaRepository extends JpaRepository<Venta, Long> {

    /**
     * @param clave clave de idempotencia que mandó la pantalla
     * @return la venta ya creada con esa clave, si existe
     */
    Optional<Venta> findByClaveIdempotencia(String clave);

    /**
     * @param id venta
     * @return la venta bloqueada para anularla o devolverla (dos devoluciones de la misma venta van en fila)
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from Venta v where v.id = :id")
    Optional<Venta> bloquear(@Param("id") Long id);

    /**
     * @param cajeroId cajero
     * @param desde    inicio del día
     * @param hasta    inicio del día siguiente
     * @return ventas del cajero anuladas ese día (spec 010)
     */
    @Query("select count(v) from Venta v where v.cajeroUsuarioId = :cajeroId "
            + "and v.estado = com.proyecto.supply_core.venta.enums.EstadoVenta.ANULADA "
            + "and v.anuladaEn >= :desde and v.anuladaEn < :hasta")
    long anuladasDelDia(@Param("cajeroId") Long cajeroId, @Param("desde") LocalDateTime desde,
            @Param("hasta") LocalDateTime hasta);

    /**
     * @param cajeroId cajero
     * @param desde    inicio del día
     * @param hasta    inicio del día siguiente
     * @return ventas del cajero de ese día con un descuento o un precio autorizado por un supervisor (spec 010)
     */
    @Query("select count(v) from Venta v where v.cajeroUsuarioId = :cajeroId and v.autorizadaPorUsuarioId is not null "
            + "and v.confirmadaEn >= :desde and v.confirmadaEn < :hasta")
    long autorizadasDelDia(@Param("cajeroId") Long cajeroId, @Param("desde") LocalDateTime desde,
            @Param("hasta") LocalDateTime hasta);

    /**
     * Historial con filtros opcionales ({@code null} = sin filtro).
     *
     * @param cajeroId    cajero
     * @param vendedorId  vendedor
     * @param cajaId      caja
     * @param turnoId     turno
     * @param clienteId   cliente
     * @param estado      estado
     * @param consecutivo consecutivo exacto
     * @param desde       desde (incluido)
     * @param hasta       hasta (excluido)
     * @param pageable    página y orden
     * @return ventas
     */
    @Query("""
            select v from Venta v
            where (:cajeroId is null or v.cajeroUsuarioId = :cajeroId)
              and (:vendedorId is null or v.vendedorUsuarioId = :vendedorId)
              and (:cajaId is null or v.cajaId = :cajaId)
              and (:turnoId is null or v.turnoId = :turnoId)
              and (:clienteId is null or v.clienteId = :clienteId)
              and (:estado is null or v.estado = :estado)
              and (:consecutivo is null or v.consecutivo = :consecutivo)
              and (:desde is null or v.confirmadaEn >= :desde)
              and (:hasta is null or v.confirmadaEn < :hasta)
            """)
    Page<Venta> buscar(@Param("cajeroId") Long cajeroId, @Param("vendedorId") Long vendedorId,
            @Param("cajaId") Long cajaId, @Param("turnoId") Long turnoId, @Param("clienteId") Long clienteId,
            @Param("estado") EstadoVenta estado, @Param("consecutivo") String consecutivo,
            @Param("desde") LocalDateTime desde, @Param("hasta") LocalDateTime hasta, Pageable pageable);
}
