package com.proyecto.supply_core.inventario.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.proyecto.supply_core.inventario.entity.Producto;

/** Acceso a la tabla {@code producto}. Las consultas traen categoría y unidad. */
public interface ProductoRepository extends JpaRepository<Producto, Long> {

    /**
     * Indica si el código ya está registrado.
     *
     * @param codigo código en mayúsculas
     * @return {@code true} si existe
     */
    boolean existsByCodigoIgnoreCase(String codigo);

    /**
     * Indica si otro producto usa el código (ediciones).
     *
     * @param codigo código en mayúsculas
     * @param id     producto que se está editando
     * @return {@code true} si otro lo tiene
     */
    boolean existsByCodigoIgnoreCaseAndIdNot(String codigo, Long id);

    /**
     * Busca por id trayendo categoría y unidad.
     *
     * @param id id del producto
     * @return producto, si existe
     */
    @EntityGraph(attributePaths = { "categoria", "unidadMedida" })
    Optional<Producto> findWithRelacionesById(Long id);

    /**
     * Búsqueda paginada con filtros opcionales ({@code null} = no filtrar).
     *
     * @param texto       coincidencia parcial en código o nombre
     * @param categoriaId categoría
     * @param activo      estado
     * @param pageable    página y orden
     * @return página de productos
     */
    @EntityGraph(attributePaths = { "categoria", "unidadMedida" })
    @Query("""
            select p from Producto p
            where (:texto is null or lower(p.codigo) like lower(concat('%', :texto, '%'))
                                  or lower(p.nombre) like lower(concat('%', :texto, '%')))
              and (:categoriaId is null or p.categoria.id = :categoriaId)
              and (:activo is null or p.activo = :activo)
            """)
    Page<Producto> buscar(@Param("texto") String texto, @Param("categoriaId") Long categoriaId,
            @Param("activo") Boolean activo, Pageable pageable);

    /**
     * Productos activos cuyo stock total (todas las bodegas) es menor que su stock mínimo.
     *
     * @return productos con alerta de reposición, por código
     */
    @EntityGraph(attributePaths = { "categoria", "unidadMedida" })
    @Query("""
            select p from Producto p
            where p.activo = true
              and p.stockMinimo > (select coalesce(sum(s.cantidad), 0) from StockProductoBodega s
                                   where s.producto = p)
            order by p.codigo
            """)
    List<Producto> bajoStockMinimo();
}
