package com.proyecto.supply_core.reorden.repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import com.proyecto.supply_core.compra.orden.entity.OrdenCompra;
import com.proyecto.supply_core.compra.orden.enums.EstadoOrden;
import com.proyecto.supply_core.compra.solicitud.entity.SolicitudCompraLinea;
import com.proyecto.supply_core.compra.solicitud.enums.EstadoSolicitud;
import com.proyecto.supply_core.reorden.dto.CantidadPorProducto;
import com.proyecto.supply_core.reorden.dto.EntregaHistorica;

/**
 * Consultas de lo que ya viene en camino para un producto (paso 18-d). Solo lectura: sobre entidades de negocio
 * con {@code @TenantId}, así cada empresa ve solo lo suyo.
 */
public interface ReordenRepository extends Repository<OrdenCompra, Long> {

    /**
     * Suma lo pedido en órdenes en los estados dados, por producto.
     *
     * @param productoIds productos
     * @param estados     estados de orden que cuentan como "por recibir"
     * @return una fila por producto que tenga órdenes
     */
    @Query("""
            select new com.proyecto.supply_core.reorden.dto.CantidadPorProducto(l.producto.id, sum(l.cantidad))
            from OrdenCompraLinea l
            where l.producto.id in :productoIds and l.orden.estado in :estados
            group by l.producto.id
            """)
    List<CantidadPorProducto> pedidoEnOrdenes(@Param("productoIds") Collection<Long> productoIds,
            @Param("estados") Collection<EstadoOrden> estados);

    /**
     * Igual que {@link #pedidoEnOrdenes} pero solo de las órdenes que llegan a bodegas de una sede.
     *
     * @param productoIds productos
     * @param estados     estados de orden que cuentan como "por recibir"
     * @param sedeId      sede de la bodega destino
     * @return una fila por producto que tenga órdenes hacia esa sede
     */
    @Query("""
            select new com.proyecto.supply_core.reorden.dto.CantidadPorProducto(l.producto.id, sum(l.cantidad))
            from OrdenCompraLinea l
            where l.producto.id in :productoIds and l.orden.estado in :estados
              and l.orden.bodegaDestino.sede.id = :sedeId
            group by l.producto.id
            """)
    List<CantidadPorProducto> pedidoEnOrdenesDeSede(@Param("productoIds") Collection<Long> productoIds,
            @Param("estados") Collection<EstadoOrden> estados, @Param("sedeId") Long sedeId);

    /**
     * Igual que {@link #aceptadoEnOrdenes} pero solo de las órdenes que llegan a bodegas de una sede.
     *
     * @param productoIds productos
     * @param estados     estados de orden que cuentan como "por recibir"
     * @param sedeId      sede de la bodega destino
     * @return una fila por producto con recepciones de órdenes hacia esa sede
     */
    @Query("""
            select new com.proyecto.supply_core.reorden.dto.CantidadPorProducto(r.ordenLinea.producto.id,
                   sum(r.cantidadAceptada))
            from RecepcionLinea r
            where r.ordenLinea.producto.id in :productoIds and r.ordenLinea.orden.estado in :estados
              and r.ordenLinea.orden.bodegaDestino.sede.id = :sedeId
            group by r.ordenLinea.producto.id
            """)
    List<CantidadPorProducto> aceptadoEnOrdenesDeSede(@Param("productoIds") Collection<Long> productoIds,
            @Param("estados") Collection<EstadoOrden> estados, @Param("sedeId") Long sedeId);

    /**
     * Suma lo ya aceptado en recepciones de esas mismas órdenes, por producto.
     *
     * @param productoIds productos
     * @param estados     estados de orden que cuentan como "por recibir"
     * @return una fila por producto con recepciones
     */
    @Query("""
            select new com.proyecto.supply_core.reorden.dto.CantidadPorProducto(r.ordenLinea.producto.id,
                   sum(r.cantidadAceptada))
            from RecepcionLinea r
            where r.ordenLinea.producto.id in :productoIds and r.ordenLinea.orden.estado in :estados
            group by r.ordenLinea.producto.id
            """)
    List<CantidadPorProducto> aceptadoEnOrdenes(@Param("productoIds") Collection<Long> productoIds,
            @Param("estados") Collection<EstadoOrden> estados);

    /**
     * Líneas de solicitudes en los estados dados para esos productos, con producto y unidad cargados (la cantidad
     * va en la unidad de la línea y se convierte en Java).
     *
     * @param productoIds productos
     * @param estados     estados de solicitud que cuentan como "ya pedido"
     * @return líneas
     */
    @Query("""
            select l from SolicitudCompraLinea l
              join fetch l.producto join fetch l.unidadMedida
            where l.producto.id in :productoIds and l.solicitud.estado in :estados
            """)
    List<SolicitudCompraLinea> lineasDeSolicitudes(@Param("productoIds") Collection<Long> productoIds,
            @Param("estados") Collection<EstadoSolicitud> estados);

    /**
     * Órdenes recibidas desde una fecha que traían alguno de los productos, con la fecha de su primera recepción.
     * Se usa para mostrar cuánto tardan de verdad las entregas.
     *
     * @param productoIds productos a revisar
     * @param desde       solo órdenes emitidas desde este momento
     * @return una fila por producto y orden
     */
    @Query("""
            select new com.proyecto.supply_core.reorden.dto.EntregaHistorica(l.producto.id, o.creadoEn, min(r.recibidoEn))
            from Recepcion r join r.orden o join o.lineas l
            where l.producto.id in :productoIds and o.creadoEn >= :desde
            group by l.producto.id, o.id, o.creadoEn
            """)
    List<EntregaHistorica> entregasHistoricas(@Param("productoIds") Collection<Long> productoIds,
            @Param("desde") LocalDateTime desde);
}
