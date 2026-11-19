package com.proyecto.supply_core.inventario.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.proyecto.supply_core.inventario.entity.ProductoSede;

/** Mínimos y máximos por sede. La entidad filtra por empresa ({@code @TenantId}). */
public interface ProductoSedeRepository extends JpaRepository<ProductoSede, Long> {

    /**
     * @param productoId producto
     * @param sedeId     sede
     * @return la configuración, si la sede tiene una para el producto
     */
    @Query("select ps from ProductoSede ps where ps.producto.id = :productoId and ps.sede.id = :sedeId")
    Optional<ProductoSede> deProductoEnSede(@Param("productoId") Long productoId, @Param("sedeId") Long sedeId);

    /**
     * @param productoId producto
     * @return su configuración en cada sede que la tenga
     */
    @Query("select ps from ProductoSede ps join fetch ps.sede where ps.producto.id = :productoId")
    List<ProductoSede> deProducto(@Param("productoId") Long productoId);

    /**
     * @param sedeId      sede
     * @param productoIds productos
     * @return configuración de esos productos en la sede
     */
    @Query("select ps from ProductoSede ps where ps.sede.id = :sedeId and ps.producto.id in :productoIds")
    List<ProductoSede> deSede(@Param("sedeId") Long sedeId, @Param("productoIds") Collection<Long> productoIds);

    /**
     * Sedes activas de la empresa (la entidad filtra por empresa).
     *
     * @return sedes ordenadas por nombre
     */
    @Query("select s from Sede s where s.activo = true order by s.nombre")
    List<com.proyecto.supply_core.empresa.entity.Sede> sedesActivas();
}
