package com.proyecto.supply_core.cartera.repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.proyecto.supply_core.cartera.entity.CuentaPorCobrar;
import com.proyecto.supply_core.cartera.enums.EstadoCuenta;

import jakarta.persistence.LockModeType;

/** Cuentas por cobrar de la empresa del contexto ({@code @TenantId}). */
public interface CuentaPorCobrarRepository extends JpaRepository<CuentaPorCobrar, Long> {

    /**
     * @param clienteId cliente
     * @return sus cuentas abiertas, la más antigua primero, bloqueadas para aplicarles un abono
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from CuentaPorCobrar c where c.clienteId = :clienteId "
            + "and c.estado = com.proyecto.supply_core.cartera.enums.EstadoCuenta.ABIERTA order by c.venceEn, c.id")
    List<CuentaPorCobrar> abiertasBloqueadas(@Param("clienteId") Long clienteId);

    /**
     * @param ventaId venta
     * @return la cuenta de esa venta, bloqueada
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from CuentaPorCobrar c where c.ventaId = :ventaId")
    Optional<CuentaPorCobrar> deVentaBloqueada(@Param("ventaId") Long ventaId);

    /**
     * @param estado estado
     * @return cuentas con ese estado (para las edades de la cartera)
     */
    List<CuentaPorCobrar> findByEstado(EstadoCuenta estado);

    /**
     * @param clienteId cliente
     * @return todas sus cuentas, las más recientes primero
     */
    List<CuentaPorCobrar> findByClienteIdOrderByIdDesc(Long clienteId);

    /**
     * Saldo abierto leído con bloqueo: ve lo último confirmado aunque la transacción ya haya leído antes.
     *
     * @param empresaId empresa (SQL nativo: va a mano)
     * @param clienteId cliente
     * @return saldo por cobrar
     */
    @Query(value = "SELECT COALESCE(SUM(saldo), 0) FROM cuenta_por_cobrar WHERE empresa_id = :empresaId "
            + "AND cliente_id = :clienteId AND estado = 'ABIERTA' FOR UPDATE", nativeQuery = true)
    BigDecimal saldoBloqueado(@Param("empresaId") Long empresaId, @Param("clienteId") Long clienteId);

    /**
     * Cuentas vencidas con saldo, leídas con bloqueo.
     *
     * @param empresaId empresa (SQL nativo: va a mano)
     * @param clienteId cliente
     * @param hoy       fecha de hoy
     * @return cuántas cuentas vencidas tiene
     */
    @Query(value = "SELECT COUNT(*) FROM cuenta_por_cobrar WHERE empresa_id = :empresaId AND cliente_id = :clienteId "
            + "AND estado = 'ABIERTA' AND saldo > 0 AND vence_en < :hoy FOR UPDATE", nativeQuery = true)
    long vencidasBloqueado(@Param("empresaId") Long empresaId, @Param("clienteId") Long clienteId,
            @Param("hoy") LocalDate hoy);
}
