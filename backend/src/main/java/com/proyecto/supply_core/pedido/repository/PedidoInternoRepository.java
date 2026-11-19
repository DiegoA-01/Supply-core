package com.proyecto.supply_core.pedido.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.proyecto.supply_core.pedido.entity.PedidoInterno;
import com.proyecto.supply_core.pedido.enums.EstadoPedidoInterno;

/** Pedidos internos. La entidad filtra por empresa ({@code @TenantId}). */
public interface PedidoInternoRepository extends JpaRepository<PedidoInterno, Long> {

    /**
     * Pedidos de la empresa, opcionalmente de un usuario y en un estado, del más reciente al más antiguo.
     *
     * @param usuarioId solo los de este usuario, o {@code null} para todos
     * @param estado    solo en este estado, o {@code null} para todos
     * @param pageable  página (orden ya validado)
     * @return página de pedidos
     */
    @Query(value = """
            select p from PedidoInterno p left join fetch p.producto left join fetch p.sede
            where (:usuarioId is null or p.usuarioId = :usuarioId)
              and (:estado is null or p.estado = :estado)
            """, countQuery = """
            select count(p) from PedidoInterno p
            where (:usuarioId is null or p.usuarioId = :usuarioId)
              and (:estado is null or p.estado = :estado)
            """)
    Page<PedidoInterno> buscar(@Param("usuarioId") Long usuarioId, @Param("estado") EstadoPedidoInterno estado,
            Pageable pageable);
}
