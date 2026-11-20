package com.proyecto.supply_core.venta.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.proyecto.supply_core.venta.entity.PedidoVenta;
import com.proyecto.supply_core.venta.enums.EstadoPedidoVenta;

import jakarta.persistence.LockModeType;

/** Pedidos de venta de la empresa del contexto ({@code @TenantId}). */
public interface PedidoVentaRepository extends JpaRepository<PedidoVenta, Long> {

    /**
     * @param id pedido
     * @return el pedido bloqueado para cobrarlo, editarlo o descartarlo (dos cajeros no cobran el mismo)
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PedidoVenta p where p.id = :id")
    Optional<PedidoVenta> bloquear(@Param("id") Long id);

    /**
     * @param estado     estado ({@code null} = todos)
     * @param vendedorId vendedor ({@code null} = todos)
     * @param pageable   página y orden
     * @return pedidos
     */
    @Query("""
            select p from PedidoVenta p
            where (:estado is null or p.estado = :estado)
              and (:vendedorId is null or p.vendedorUsuarioId = :vendedorId)
            """)
    Page<PedidoVenta> buscar(@Param("estado") EstadoPedidoVenta estado, @Param("vendedorId") Long vendedorId,
            Pageable pageable);
}
