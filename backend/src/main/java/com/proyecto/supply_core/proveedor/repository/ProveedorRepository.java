package com.proyecto.supply_core.proveedor.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.proyecto.supply_core.proveedor.entity.Proveedor;
import com.proyecto.supply_core.proveedor.enums.EstadoProveedor;

/** Acceso a la tabla {@code proveedor}. */
public interface ProveedorRepository extends JpaRepository<Proveedor, Long> {

    /**
     * Indica si el NIT ya está registrado.
     *
     * @param nit NIT normalizado
     * @return {@code true} si existe
     */
    boolean existsByNitIgnoreCase(String nit);

    /**
     * Busca por id trayendo sus categorías.
     *
     * @param id id del proveedor
     * @return proveedor con categorías, si existe
     */
    @EntityGraph(attributePaths = "categorias")
    Optional<Proveedor> findWithCategoriasById(Long id);

    /**
     * Búsqueda paginada con filtros opcionales ({@code null} = no filtrar).
     *
     * @param texto    coincidencia parcial en razón social o NIT
     * @param estado      estado del proveedor
     * @param categoriaId categoría que puede cotizar
     * @param pageable    página y orden
     * @return página de proveedores
     */
    @Query("""
            select p from Proveedor p
            where (:texto is null or lower(p.razonSocial) like lower(concat('%', :texto, '%'))
                                  or p.nit like concat('%', :texto, '%'))
              and (:estado is null or p.estado = :estado)
              and (:categoriaId is null or exists (
                    select 1 from Proveedor p2 join p2.categorias c where p2 = p and c.id = :categoriaId))
            """)
    Page<Proveedor> buscar(@Param("texto") String texto, @Param("estado") EstadoProveedor estado,
            @Param("categoriaId") Long categoriaId, Pageable pageable);

    /**
     * Proveedores HABILITADOS que cotizan una categoría (invitados a una RFQ, paso 11).
     *
     * @param categoriaId categoría
     * @return proveedores ordenados por razón social
     */
    @Query("""
            select p from Proveedor p join p.categorias c
            where c.id = :categoriaId
              and p.estado = com.proyecto.supply_core.proveedor.enums.EstadoProveedor.HABILITADO
            order by p.razonSocial
            """)
    List<Proveedor> findHabilitadosPorCategoria(@Param("categoriaId") Long categoriaId);
}
