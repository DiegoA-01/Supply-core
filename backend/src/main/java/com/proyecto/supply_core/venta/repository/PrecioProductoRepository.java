package com.proyecto.supply_core.venta.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.proyecto.supply_core.venta.entity.PrecioProducto;

import jakarta.persistence.LockModeType;

/** Historial de precios ({@code @TenantId}). */
public interface PrecioProductoRepository extends JpaRepository<PrecioProducto, Long> {

    /**
     * @param listaId lista
     * @return precios vigentes de la lista
     */
    @Query("select p from PrecioProducto p where p.lista.id = :listaId and p.vigenteHasta is null")
    List<PrecioProducto> vigentes(@Param("listaId") Long listaId);

    /**
     * Precio vigente de un producto, bloqueado para cambiarlo sin carreras.
     *
     * @param listaId    lista
     * @param productoId producto
     * @return el vigente, si tiene
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PrecioProducto p where p.lista.id = :listaId and p.producto.id = :productoId "
            + "and p.vigenteHasta is null")
    Optional<PrecioProducto> vigenteParaCambiar(@Param("listaId") Long listaId, @Param("productoId") Long productoId);

    /**
     * @param listaId    lista
     * @param productoId producto
     * @return todos sus precios en la lista, del más reciente al más antiguo
     */
    @Query("select p from PrecioProducto p where p.lista.id = :listaId and p.producto.id = :productoId "
            + "order by p.vigenteDesde desc, p.id desc")
    List<PrecioProducto> historial(@Param("listaId") Long listaId, @Param("productoId") Long productoId);

    /**
     * @return productos activos y vendibles de la empresa, con categoría, unidad e impuesto, por nombre
     */
    @Query("""
            select p from Producto p join fetch p.categoria join fetch p.unidadMedida left join fetch p.tarifaImpuesto
            where p.activo = true and p.vendible = true
            order by p.nombre
            """)
    List<com.proyecto.supply_core.inventario.entity.Producto> productosVendibles();

    /**
     * @param codigoBarras código de barras
     * @param productoId   producto que se edita
     * @return {@code true} si otro producto de la empresa ya lo usa
     */
    @Query("select count(p) > 0 from Producto p where p.codigoBarras = :codigoBarras and p.id <> :productoId")
    boolean codigoBarrasEnUso(@Param("codigoBarras") String codigoBarras, @Param("productoId") Long productoId);
}
