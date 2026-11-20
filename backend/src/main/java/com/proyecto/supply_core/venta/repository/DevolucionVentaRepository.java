package com.proyecto.supply_core.venta.repository;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.proyecto.supply_core.venta.entity.DevolucionVenta;

/** Devoluciones de la empresa del contexto ({@code @TenantId}). */
public interface DevolucionVentaRepository extends JpaRepository<DevolucionVenta, Long> {

    /**
     * Lo ya devuelto de cada línea de una venta.
     *
     * @param ventaLineaId línea vendida
     * @param cantidad     cantidad devuelta en la unidad vendida
     * @param cantidadUnidadProducto cantidad devuelta en la unidad del producto
     * @param valor        valor devuelto
     */
    record Devuelto(Long ventaLineaId, BigDecimal cantidad, BigDecimal cantidadUnidadProducto, BigDecimal valor) {
    }

    /**
     * @param ventaId venta
     * @return lo devuelto por línea (solo las líneas con devoluciones)
     */
    @Query("""
            select new com.proyecto.supply_core.venta.repository.DevolucionVentaRepository$Devuelto(
                l.ventaLineaId, sum(l.cantidad), sum(l.cantidadUnidadProducto), sum(l.valor))
            from DevolucionVentaLinea l join l.devolucion d
            where d.ventaId = :ventaId
            group by l.ventaLineaId
            """)
    List<Devuelto> devueltoPorLinea(@Param("ventaId") Long ventaId);

    /**
     * @param ventaId venta
     * @return {@code true} si la venta tiene devoluciones
     */
    boolean existsByVentaId(Long ventaId);

    /**
     * @param ventaId  venta ({@code null} = todas)
     * @param pageable página y orden
     * @return devoluciones
     */
    @Query("select d from DevolucionVenta d where (:ventaId is null or d.ventaId = :ventaId)")
    Page<DevolucionVenta> buscar(@Param("ventaId") Long ventaId, Pageable pageable);
}
