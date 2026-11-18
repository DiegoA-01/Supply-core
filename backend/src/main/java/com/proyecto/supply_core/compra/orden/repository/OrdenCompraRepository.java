package com.proyecto.supply_core.compra.orden.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.proyecto.supply_core.compra.orden.entity.OrdenCompra;
import com.proyecto.supply_core.compra.orden.enums.EstadoOrden;

import jakarta.persistence.LockModeType;

/** Acceso a la tabla {@code orden_compra}. */
public interface OrdenCompraRepository extends JpaRepository<OrdenCompra, Long> {

    /**
     * Busca por id con proveedor, bodega y líneas (con producto y su unidad).
     *
     * @param id orden
     * @return orden, si existe
     */
    @EntityGraph(attributePaths = { "proveedor", "bodegaDestino", "lineas", "lineas.producto",
            "lineas.producto.unidadMedida" })
    Optional<OrdenCompra> findDetalleById(Long id);

    /**
     * Bloquea la fila hasta el fin de la transacción.
     *
     * @param id orden
     * @return orden bloqueada, si existe
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from OrdenCompra o where o.id = :id")
    Optional<OrdenCompra> bloquear(@Param("id") Long id);

    /**
     * Indica si la adjudicación ya tiene una orden en alguno de los estados (vigente).
     *
     * @param adjudicacionId adjudicación
     * @param estados        estados que cuentan
     * @return {@code true} si existe
     */
    boolean existsByAdjudicacionIdAndEstadoNotIn(Long adjudicacionId, Collection<EstadoOrden> estados);

    /**
     * Todas las versiones de un consecutivo, de la primera a la última.
     *
     * @param consecutivo OC-000001
     * @return versiones
     */
    List<OrdenCompra> findByConsecutivoOrderByVersionAsc(String consecutivo);

    /**
     * Búsqueda con filtros opcionales ({@code null} = no filtrar). Las versiones REEMPLAZADAS se excluyen
     * salvo que se pidan por estado.
     *
     * @param texto       coincidencia en el consecutivo
     * @param estado      estado
     * @param proveedorId proveedor
     * @param pageable    página y orden
     * @return página de órdenes
     */
    @EntityGraph(attributePaths = { "proveedor", "bodegaDestino" })
    @Query("""
            select o from OrdenCompra o
            where (:texto is null or o.consecutivo like concat('%', :texto, '%'))
              and ((:estado is null and o.estado <> com.proyecto.supply_core.compra.orden.enums.EstadoOrden.REEMPLAZADA)
                   or o.estado = :estado)
              and (:proveedorId is null or o.proveedor.id = :proveedorId)
            """)
    Page<OrdenCompra> buscar(@Param("texto") String texto, @Param("estado") EstadoOrden estado,
            @Param("proveedorId") Long proveedorId, Pageable pageable);
}
