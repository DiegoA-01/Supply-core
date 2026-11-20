package com.proyecto.supply_core.venta.repository;

import java.math.BigDecimal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.proyecto.supply_core.venta.entity.SaldoFavorCliente;

/** Saldo a favor de los clientes de la empresa del contexto ({@code @TenantId}). */
public interface SaldoFavorClienteRepository extends JpaRepository<SaldoFavorCliente, Long> {

    /**
     * @param clienteId cliente
     * @return saldo a favor actual (suma de sus movimientos)
     */
    @Query("select coalesce(sum(s.monto), 0) from SaldoFavorCliente s where s.clienteId = :clienteId")
    BigDecimal saldoDe(@Param("clienteId") Long clienteId);

    /**
     * Saldo a favor leído con bloqueo (para gastarlo en una venta sin que dos cajas lo usen a la vez).
     *
     * @param empresaId empresa (SQL nativo: va a mano)
     * @param clienteId cliente
     * @return saldo a favor actual
     */
    @Query(value = "SELECT COALESCE(SUM(monto), 0) FROM saldo_favor_cliente WHERE empresa_id = :empresaId "
            + "AND cliente_id = :clienteId FOR UPDATE", nativeQuery = true)
    BigDecimal saldoBloqueado(@Param("empresaId") Long empresaId, @Param("clienteId") Long clienteId);
}
