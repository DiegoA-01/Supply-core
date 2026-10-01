package com.proyecto.supply_core.inventario.repository;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.proyecto.supply_core.inventario.entity.StockProductoBodega;

import jakarta.persistence.LockModeType;

/**
 * Acceso a la tabla {@code stock_producto_bodega}. Para modificar el stock se usa SIEMPRE
 * {@link #crearSiNoExiste} + {@link #bloquear} dentro de la transacción del movimiento.
 */
public interface StockProductoBodegaRepository extends JpaRepository<StockProductoBodega, Long> {

    /**
     * Proyección: stock total de un producto (para calcular varios productos en una sola consulta).
     */
    interface TotalPorProducto {
        /**
         * @return id del producto
         */
        Long getProductoId();

        /**
         * @return suma de la cantidad en todas las bodegas
         */
        BigDecimal getTotal();
    }

    /**
     * Crea la fila con cantidad 0 si no existe; si ya existe no hace nada.
     * Es atómico en MySQL, así dos movimientos simultáneos sobre un par nuevo no chocan.
     *
     * @param productoId producto
     * @param bodegaId   bodega
     */
    @Modifying
    @Query(value = """
            INSERT INTO stock_producto_bodega (producto_id, bodega_id, cantidad) VALUES (:productoId, :bodegaId, 0)
            ON DUPLICATE KEY UPDATE cantidad = cantidad
            """, nativeQuery = true)
    void crearSiNoExiste(@Param("productoId") Long productoId, @Param("bodegaId") Long bodegaId);

    /**
     * Lee la fila bloqueándola ({@code SELECT ... FOR UPDATE}) hasta el fin de la transacción:
     * un segundo movimiento sobre el mismo producto y bodega espera, así el stock nunca queda negativo.
     *
     * @param productoId producto
     * @param bodegaId   bodega
     * @return fila bloqueada
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from StockProductoBodega s where s.producto.id = :productoId and s.bodega.id = :bodegaId")
    Optional<StockProductoBodega> bloquear(@Param("productoId") Long productoId, @Param("bodegaId") Long bodegaId);

    /**
     * Stock total de un producto en todas las bodegas.
     *
     * @param productoId producto
     * @return total (0 si no tiene filas)
     */
    @Query("select coalesce(sum(s.cantidad), 0) from StockProductoBodega s where s.producto.id = :productoId")
    BigDecimal totalDeProducto(@Param("productoId") Long productoId);

    /**
     * Totales de varios productos en una sola consulta.
     *
     * @param ids productos
     * @return total por producto (los que no tienen filas no aparecen)
     */
    @Query("""
            select s.producto.id as productoId, sum(s.cantidad) as total
            from StockProductoBodega s where s.producto.id in :ids group by s.producto.id
            """)
    List<TotalPorProducto> totalesDe(@Param("ids") Collection<Long> ids);

    /**
     * Existencias con filtros opcionales.
     *
     * @param productoId producto ({@code null} = todos)
     * @param bodegaId   bodega ({@code null} = todas)
     * @return filas con producto y bodega cargados
     */
    @EntityGraph(attributePaths = { "producto", "producto.unidadMedida", "bodega" })
    @Query("""
            select s from StockProductoBodega s
            where (:productoId is null or s.producto.id = :productoId)
              and (:bodegaId is null or s.bodega.id = :bodegaId)
            order by s.producto.codigo, s.bodega.nombre
            """)
    List<StockProductoBodega> buscar(@Param("productoId") Long productoId, @Param("bodegaId") Long bodegaId);

    /**
     * Indica si una bodega tiene stock (para no desactivarla).
     *
     * @param bodegaId bodega
     * @param minimo   cantidad de referencia (0)
     * @return {@code true} si alguna fila supera el mínimo
     */
    boolean existsByBodegaIdAndCantidadGreaterThan(Long bodegaId, BigDecimal minimo);
}
