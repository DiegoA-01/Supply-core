package com.proyecto.supply_core.catalogoproveedor.repository;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.proyecto.supply_core.catalogoproveedor.entity.CatalogoItem;
import com.proyecto.supply_core.catalogoproveedor.enums.EstadoEquivalencia;

/** Catálogos de proveedores ({@code @TenantId}). */
public interface CatalogoItemRepository extends JpaRepository<CatalogoItem, Long> {

    /**
     * @param proveedorId proveedor
     * @return todo su catálogo (activo o no)
     */
    @Query("select c from CatalogoItem c where c.proveedor.id = :proveedorId")
    List<CatalogoItem> deProveedor(@Param("proveedorId") Long proveedorId);

    /**
     * Catálogo de todos los proveedores para compras, con filtros.
     *
     * @param proveedorId proveedor, opcional
     * @param estado      estado de la equivalencia, opcional
     * @param texto       código, descripción o marca, opcional
     * @param pageable    página
     * @return página de artículos activos
     */
    @Query(value = """
            select c from CatalogoItem c join fetch c.proveedor left join fetch c.producto
            where c.activo = true
              and (:proveedorId is null or c.proveedor.id = :proveedorId)
              and (:estado is null or c.estadoEquivalencia = :estado)
              and (:texto is null or lower(c.descripcion) like lower(concat('%', :texto, '%'))
                   or lower(c.codigoProveedor) like lower(concat('%', :texto, '%'))
                   or lower(c.marca) like lower(concat('%', :texto, '%')))
            """, countQuery = """
            select count(c) from CatalogoItem c
            where c.activo = true
              and (:proveedorId is null or c.proveedor.id = :proveedorId)
              and (:estado is null or c.estadoEquivalencia = :estado)
              and (:texto is null or lower(c.descripcion) like lower(concat('%', :texto, '%'))
                   or lower(c.codigoProveedor) like lower(concat('%', :texto, '%'))
                   or lower(c.marca) like lower(concat('%', :texto, '%')))
            """)
    Page<CatalogoItem> buscar(@Param("proveedorId") Long proveedorId, @Param("estado") EstadoEquivalencia estado,
            @Param("texto") String texto, Pageable pageable);

    /**
     * @return artículos activos con equivalencia confirmada (base de las oportunidades)
     */
    @Query("""
            select c from CatalogoItem c join fetch c.proveedor join fetch c.producto p
              join fetch p.unidadMedida left join fetch p.tarifaImpuesto
            where c.activo = true and c.estadoEquivalencia = com.proyecto.supply_core.catalogoproveedor.enums.EstadoEquivalencia.CONFIRMADA
            """)
    List<CatalogoItem> confirmados();

    /**
     * @param productoId producto
     * @return ofertas activas confirmadas de ese producto
     */
    @Query("""
            select c from CatalogoItem c join fetch c.proveedor
            where c.activo = true and c.producto.id = :productoId
              and c.estadoEquivalencia = com.proyecto.supply_core.catalogoproveedor.enums.EstadoEquivalencia.CONFIRMADA
            order by c.precio
            """)
    List<CatalogoItem> ofertasDe(@Param("productoId") Long productoId);
}
