package com.proyecto.supply_core.factura.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.proyecto.supply_core.factura.entity.FacturaProveedor;
import com.proyecto.supply_core.factura.enums.EstadoFactura;

import jakarta.persistence.LockModeType;

/** Acceso a la tabla {@code factura_proveedor}. */
public interface FacturaProveedorRepository extends JpaRepository<FacturaProveedor, Long> {

    /**
     * Busca por id con la orden y su proveedor.
     *
     * @param id factura
     * @return factura, si existe en la empresa
     */
    @EntityGraph(attributePaths = { "orden", "proveedor" })
    Optional<FacturaProveedor> findDetalleById(Long id);

    /**
     * Bloquea la fila hasta el fin de la transacción.
     *
     * @param id factura
     * @return factura bloqueada, si existe
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select f from FacturaProveedor f where f.id = :id")
    Optional<FacturaProveedor> bloquear(@Param("id") Long id);

    /**
     * Facturas de todas las versiones de una orden, de la más antigua a la más reciente (incluye anuladas).
     *
     * @param consecutivo OC-000001
     * @return facturas
     */
    @EntityGraph(attributePaths = { "orden", "proveedor" })
    List<FacturaProveedor> findByOrden_ConsecutivoOrderByCreadoEnAscIdAsc(String consecutivo);

    /**
     * La factura vigente de una orden: la que no está anulada.
     *
     * @param consecutivo OC-000001
     * @param excluido    estado que no cuenta (ANULADA)
     * @return factura vigente, si hay
     */
    @EntityGraph(attributePaths = { "orden", "proveedor" })
    Optional<FacturaProveedor> findFirstByOrden_ConsecutivoAndEstadoNot(String consecutivo, EstadoFactura excluido);

    /**
     * Indica si el proveedor ya tiene una factura con ese número que no esté anulada.
     *
     * @param proveedorId proveedor
     * @param numero      número de la factura
     * @param excluido    estado que no cuenta (ANULADA)
     * @return {@code true} si existe
     */
    boolean existsByProveedor_IdAndNumeroIgnoreCaseAndEstadoNot(Long proveedorId, String numero, EstadoFactura excluido);

    /**
     * Búsqueda con filtros opcionales ({@code null} = no filtrar).
     *
     * @param estado      estado
     * @param proveedorId proveedor
     * @param pageable    página y orden
     * @return página de facturas
     */
    @EntityGraph(attributePaths = { "orden", "proveedor" })
    @Query("""
            select f from FacturaProveedor f
            where (:estado is null or f.estado = :estado)
              and (:proveedorId is null or f.proveedor.id = :proveedorId)
            """)
    Page<FacturaProveedor> buscar(@Param("estado") EstadoFactura estado, @Param("proveedorId") Long proveedorId,
            Pageable pageable);
}
