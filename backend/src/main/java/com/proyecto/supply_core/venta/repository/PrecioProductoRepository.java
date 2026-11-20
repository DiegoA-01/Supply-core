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
     * Precio vigente de un producto en una unidad, bloqueado para cambiarlo sin carreras. Si todavía no hay ninguno,
     * la regla única {@code uq_precio_vigente} (V31) impide que dos personas creen el primero a la vez.
     *
     * @param listaId    lista
     * @param productoId producto
     * @param unidadId   unidad del precio
     * @return el vigente, si tiene
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PrecioProducto p where p.lista.id = :listaId and p.producto.id = :productoId "
            + "and p.unidadMedidaId = :unidadId and p.vigenteHasta is null")
    Optional<PrecioProducto> vigenteParaCambiar(@Param("listaId") Long listaId, @Param("productoId") Long productoId,
            @Param("unidadId") Long unidadId);

    /**
     * @param listaId    lista
     * @param productoId producto
     * @return precios vigentes del producto en la lista, uno por unidad
     */
    @Query("select p from PrecioProducto p where p.lista.id = :listaId and p.producto.id = :productoId "
            + "and p.vigenteHasta is null")
    List<PrecioProducto> vigentesDeProducto(@Param("listaId") Long listaId, @Param("productoId") Long productoId);

    /**
     * @param listaId     lista
     * @param productoIds productos
     * @return precios vigentes de esos productos en la lista
     */
    @Query("select p from PrecioProducto p where p.lista.id = :listaId and p.producto.id in :productoIds "
            + "and p.vigenteHasta is null")
    List<PrecioProducto> vigentesDe(@Param("listaId") Long listaId,
            @Param("productoIds") java.util.Collection<Long> productoIds);

    /**
     * @param listaId    lista
     * @param productoId producto
     * @param unidadId   unidad del precio
     * @return todos sus precios en la lista y la unidad, del más reciente al más antiguo
     */
    @Query("select p from PrecioProducto p where p.lista.id = :listaId and p.producto.id = :productoId "
            + "and p.unidadMedidaId = :unidadId order by p.vigenteDesde desc, p.id desc")
    List<PrecioProducto> historial(@Param("listaId") Long listaId, @Param("productoId") Long productoId,
            @Param("unidadId") Long unidadId);

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

    /**
     * Productos activos y vendibles para vender: coincide el código (empieza por), el código de barras (exacto) o el
     * nombre (contiene). Primero las coincidencias exactas de código o código de barras, después los que empiezan por
     * el texto (en el nombre o el código) y al final los que solo lo contienen; dentro de cada grupo, por nombre.
     *
     * @param exacto   texto buscado en minúsculas
     * @param prefijo  texto escapado + {@code "%"} (comodines escapados con {@code !})
     * @param contiene {@code "%"} + texto escapado + {@code "%"}
     * @param barras   texto buscado tal cual (código de barras)
     * @param pagina   cuántos traer
     * @return productos con su unidad e impuesto
     */
    @Query("""
            select p from Producto p join fetch p.unidadMedida left join fetch p.tarifaImpuesto
            where p.activo = true and p.vendible = true
              and (lower(p.codigo) like :prefijo escape '!' or p.codigoBarras = :barras
                   or lower(p.nombre) like :contiene escape '!')
            order by case when lower(p.codigo) = :exacto or p.codigoBarras = :barras then 0
                          when lower(p.nombre) like :prefijo escape '!' or lower(p.codigo) like :prefijo escape '!' then 1
                          else 2 end, p.nombre
            """)
    List<com.proyecto.supply_core.inventario.entity.Producto> buscarParaVender(@Param("exacto") String exacto,
            @Param("prefijo") String prefijo, @Param("contiene") String contiene, @Param("barras") String barras,
            org.springframework.data.domain.Pageable pagina);

    /**
     * @param bodegaId    bodega
     * @param productoIds productos
     * @return existencias de esos productos en la bodega (los que no tienen fila no aparecen)
     */
    @Query("""
            select s.producto.id as productoId, s.cantidad as total
            from StockProductoBodega s where s.bodega.id = :bodegaId and s.producto.id in :productoIds
            """)
    List<com.proyecto.supply_core.inventario.repository.StockProductoBodegaRepository.TotalPorProducto> stockEnBodega(
            @Param("bodegaId") Long bodegaId, @Param("productoIds") java.util.Collection<Long> productoIds);
}
