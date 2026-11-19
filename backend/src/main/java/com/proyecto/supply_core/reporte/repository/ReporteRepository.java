package com.proyecto.supply_core.reporte.repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import com.proyecto.supply_core.compra.orden.entity.OrdenCompra;
import com.proyecto.supply_core.compra.orden.enums.EstadoOrden;
import com.proyecto.supply_core.inventario.enums.TipoMovimiento;
import com.proyecto.supply_core.reporte.dto.GastoFila;
import com.proyecto.supply_core.reporte.dto.GastoMesFila;
import com.proyecto.supply_core.reporte.dto.InventarioFila;
import com.proyecto.supply_core.reporte.dto.OrdenFila;
import com.proyecto.supply_core.reporte.dto.RotacionMovimiento;
import com.proyecto.supply_core.reporte.dto.RotacionUltima;
import com.proyecto.supply_core.reporte.dto.SumaConsecutivo;

/**
 * Consultas agregadas de solo lectura para los reportes. Todas parten de entidades con {@code @TenantId}, así que
 * Hibernate agrega la empresa del contexto a cada consulta. Los filtros opcionales valen {@code null} para "todos".
 * Las fechas de emisión llegan como rango semiabierto {@code [desde, hasta)}.
 */
public interface ReporteRepository extends Repository<OrdenCompra, Long> {

    /**
     * Gasto de las órdenes vigentes agrupado por proveedor.
     *
     * @param excluidos   estados que no cuentan como gasto
     * @param desde       emisión desde (incluida)
     * @param hasta       emisión hasta (excluida)
     * @param categoriaId categoría de las líneas
     * @param proveedorId proveedor de la orden
     * @param sedeId      sede de la bodega destino de la orden (opcional)
     * @return filas, de mayor a menor subtotal
     */
    @Query("""
            select new com.proyecto.supply_core.reporte.dto.GastoFila(pr.id, pr.razonSocial, count(distinct o.id),
                   sum(l.subtotal), sum(l.subtotal * l.impuestoPct / 100))
            from OrdenCompra o join o.proveedor pr join o.lineas l join l.producto p
            where o.estado not in :excluidos
              and (:desde is null or o.creadoEn >= :desde)
              and (:hasta is null or o.creadoEn < :hasta)
              and (:categoriaId is null or p.categoria.id = :categoriaId)
              and (:proveedorId is null or pr.id = :proveedorId)
              and (:sedeId is null or o.bodegaDestino.sede.id = :sedeId)
            group by pr.id, pr.razonSocial
            order by sum(l.subtotal) desc
            """)
    List<GastoFila> gastoPorProveedor(@Param("excluidos") Collection<EstadoOrden> excluidos,
            @Param("desde") LocalDateTime desde, @Param("hasta") LocalDateTime hasta,
            @Param("categoriaId") Long categoriaId, @Param("proveedorId") Long proveedorId,
            @Param("sedeId") Long sedeId);

    /**
     * Gasto de las órdenes vigentes agrupado por categoría del producto.
     *
     * @param excluidos   estados que no cuentan como gasto
     * @param desde       emisión desde (incluida)
     * @param hasta       emisión hasta (excluida)
     * @param categoriaId categoría de las líneas
     * @param proveedorId proveedor de la orden
     * @param sedeId      sede de la bodega destino de la orden (opcional)
     * @return filas, de mayor a menor subtotal
     */
    @Query("""
            select new com.proyecto.supply_core.reporte.dto.GastoFila(c.id, c.nombre, count(distinct o.id),
                   sum(l.subtotal), sum(l.subtotal * l.impuestoPct / 100))
            from OrdenCompra o join o.lineas l join l.producto p join p.categoria c
            where o.estado not in :excluidos
              and (:desde is null or o.creadoEn >= :desde)
              and (:hasta is null or o.creadoEn < :hasta)
              and (:categoriaId is null or c.id = :categoriaId)
              and (:proveedorId is null or o.proveedor.id = :proveedorId)
              and (:sedeId is null or o.bodegaDestino.sede.id = :sedeId)
            group by c.id, c.nombre
            order by sum(l.subtotal) desc
            """)
    List<GastoFila> gastoPorCategoria(@Param("excluidos") Collection<EstadoOrden> excluidos,
            @Param("desde") LocalDateTime desde, @Param("hasta") LocalDateTime hasta,
            @Param("categoriaId") Long categoriaId, @Param("proveedorId") Long proveedorId,
            @Param("sedeId") Long sedeId);

    /**
     * Gasto de las órdenes vigentes agrupado por mes de emisión.
     *
     * @param excluidos   estados que no cuentan como gasto
     * @param desde       emisión desde (incluida)
     * @param hasta       emisión hasta (excluida)
     * @param categoriaId categoría de las líneas
     * @param proveedorId proveedor de la orden
     * @param sedeId      sede de la bodega destino de la orden (opcional)
     * @return filas en orden cronológico
     */
    @Query("""
            select new com.proyecto.supply_core.reporte.dto.GastoMesFila(year(o.creadoEn), month(o.creadoEn),
                   count(distinct o.id), sum(l.subtotal), sum(l.subtotal * l.impuestoPct / 100))
            from OrdenCompra o join o.lineas l join l.producto p
            where o.estado not in :excluidos
              and (:desde is null or o.creadoEn >= :desde)
              and (:hasta is null or o.creadoEn < :hasta)
              and (:categoriaId is null or p.categoria.id = :categoriaId)
              and (:proveedorId is null or o.proveedor.id = :proveedorId)
              and (:sedeId is null or o.bodegaDestino.sede.id = :sedeId)
            group by year(o.creadoEn), month(o.creadoEn)
            order by year(o.creadoEn), month(o.creadoEn)
            """)
    List<GastoMesFila> gastoPorMes(@Param("excluidos") Collection<EstadoOrden> excluidos,
            @Param("desde") LocalDateTime desde, @Param("hasta") LocalDateTime hasta,
            @Param("categoriaId") Long categoriaId, @Param("proveedorId") Long proveedorId,
            @Param("sedeId") Long sedeId);

    /**
     * Suma de pagos REGISTRADOS con fecha de pago en el rango.
     *
     * @param desde       fecha de pago desde (incluida)
     * @param hasta       fecha de pago hasta (incluida)
     * @param proveedorId proveedor de la orden
     * @param sedeId      sede de la bodega destino de la orden (opcional)
     * @return pagado (0 si no hay pagos)
     */
    @Query("""
            select coalesce(sum(pg.valor), 0) from Pago pg
            where pg.estado = com.proyecto.supply_core.pago.enums.EstadoPago.REGISTRADO
              and (:desde is null or pg.fechaPago >= :desde)
              and (:hasta is null or pg.fechaPago <= :hasta)
              and (:proveedorId is null or pg.orden.proveedor.id = :proveedorId)
              and (:sedeId is null or pg.orden.bodegaDestino.sede.id = :sedeId)
            """)
    BigDecimal pagado(@Param("desde") LocalDate desde, @Param("hasta") LocalDate hasta,
            @Param("proveedorId") Long proveedorId, @Param("sedeId") Long sedeId);

    /**
     * Productos activos con su stock sumado de todas las bodegas.
     *
     * @param categoriaId categoría del producto
     * @param sedeId      solo el stock de las bodegas de esta sede (opcional)
     * @return filas por nombre de producto
     */
    @Query("""
            select new com.proyecto.supply_core.reporte.dto.InventarioFila(p.id, p.codigo, p.nombre, c.nombre,
                   u.codigo, coalesce(sum(case when :sedeId is null or se.id = :sedeId then s.cantidad else 0 end), 0),
                   p.stockMinimo, p.stockMaximo, p.costoReferencia)
            from Producto p join p.categoria c join p.unidadMedida u
                 left join StockProductoBodega s on s.producto = p
                 left join s.bodega b left join b.sede se
            where p.activo = true
              and (:categoriaId is null or c.id = :categoriaId)
            group by p.id, p.codigo, p.nombre, c.nombre, u.codigo, p.stockMinimo, p.stockMaximo, p.costoReferencia
            order by p.nombre
            """)
    List<InventarioFila> inventario(@Param("categoriaId") Long categoriaId, @Param("sedeId") Long sedeId);

    /**
     * Lo movido por producto y tipo de movimiento en un período (base de la rotación).
     *
     * @param tipos tipos que interesan (salidas, anulaciones de despacho, compras y devoluciones)
     * @param desde fecha y hora desde (incluida)
     * @param hasta fecha y hora hasta (excluida)
     * @param sedeId sede de la bodega de origen o destino (opcional)
     * @return una fila por producto y tipo con movimientos
     */
    @Query("""
            select new com.proyecto.supply_core.reporte.dto.RotacionMovimiento(m.producto.id, m.tipo, sum(m.cantidad))
            from MovimientoInventario m
                 left join m.bodegaOrigen bo left join bo.sede so
                 left join m.bodegaDestino bd left join bd.sede sd
            where m.tipo in :tipos
              and m.creadoEn >= :desde
              and m.creadoEn < :hasta
              and (:sedeId is null or so.id = :sedeId or sd.id = :sedeId)
            group by m.producto.id, m.tipo
            """)
    List<RotacionMovimiento> movimientosPorProducto(@Param("tipos") Collection<TipoMovimiento> tipos,
            @Param("desde") LocalDateTime desde, @Param("hasta") LocalDateTime hasta, @Param("sedeId") Long sedeId);

    /**
     * Última salida de cada producto, en cualquier fecha.
     *
     * @param tipos tipos que cuentan como salida
     * @param sedeId sede de la bodega de origen (opcional)
     * @return una fila por producto que alguna vez salió
     */
    @Query("""
            select new com.proyecto.supply_core.reporte.dto.RotacionUltima(m.producto.id, max(m.creadoEn))
            from MovimientoInventario m
                 left join m.bodegaOrigen bo left join bo.sede so
            where m.tipo in :tipos
              and (:sedeId is null or so.id = :sedeId)
            group by m.producto.id
            """)
    List<RotacionUltima> ultimaSalida(@Param("tipos") Collection<TipoMovimiento> tipos, @Param("sedeId") Long sedeId);

    /**
     * Gasto en órdenes vigentes por sede de la bodega destino. Las bodegas sin sede salen juntas con id vacío.
     *
     * @param excluidos estados que no cuentan como gasto
     * @param desde     emisión desde (incluida), opcional
     * @param hasta     emisión hasta (excluida), opcional
     * @return una fila por sede con compras, de mayor a menor
     */
    @Query("""
            select new com.proyecto.supply_core.reporte.dto.GastoFila(s.id, coalesce(s.nombre, 'Sin sede'),
                   count(distinct o.id), sum(l.subtotal), sum(l.subtotal * l.impuestoPct / 100))
            from OrdenCompra o join o.lineas l left join o.bodegaDestino b left join b.sede s
            where o.estado not in :excluidos
              and (:desde is null or o.creadoEn >= :desde)
              and (:hasta is null or o.creadoEn < :hasta)
            group by s.id, s.nombre
            order by sum(l.subtotal) desc
            """)
    List<GastoFila> gastoPorSede(@Param("excluidos") Collection<EstadoOrden> excluidos,
            @Param("desde") LocalDateTime desde, @Param("hasta") LocalDateTime hasta);

    /**
     * Cuenta la sede en la empresa de la sesión (la entidad filtra por empresa): sirve para responder 404 si el
     * filtro pide una sede ajena o inexistente.
     *
     * @param sedeId sede pedida
     * @return 1 si es de la empresa, 0 si no
     */
    @Query("select count(s) from Sede s where s.id = :sedeId")
    long contarSede(@Param("sedeId") Long sedeId);

    /**
     * Órdenes vigentes de un proveedor.
     *
     * @param proveedorId proveedor
     * @param excluidos   estados que no cuentan
     * @param desde       emisión desde (incluida)
     * @param hasta       emisión hasta (excluida)
     * @return órdenes, de la más reciente a la más antigua
     */
    @Query("""
            select new com.proyecto.supply_core.reporte.dto.OrdenFila(o.id, o.consecutivo, o.creadoEn, o.estado, o.total)
            from OrdenCompra o
            where o.proveedor.id = :proveedorId
              and o.estado not in :excluidos
              and (:desde is null or o.creadoEn >= :desde)
              and (:hasta is null or o.creadoEn < :hasta)
            order by o.creadoEn desc, o.id desc
            """)
    List<OrdenFila> ordenesDelProveedor(@Param("proveedorId") Long proveedorId,
            @Param("excluidos") Collection<EstadoOrden> excluidos, @Param("desde") LocalDateTime desde,
            @Param("hasta") LocalDateTime hasta);

    /**
     * Pagos REGISTRADOS de un proveedor, sumados por orden (todas las versiones).
     *
     * @param proveedorId proveedor
     * @return una fila por consecutivo
     */
    @Query("""
            select new com.proyecto.supply_core.reporte.dto.SumaConsecutivo(pg.orden.consecutivo, sum(pg.valor))
            from Pago pg
            where pg.orden.proveedor.id = :proveedorId
              and pg.estado = com.proyecto.supply_core.pago.enums.EstadoPago.REGISTRADO
            group by pg.orden.consecutivo
            """)
    List<SumaConsecutivo> pagadoPorOrden(@Param("proveedorId") Long proveedorId);

    /**
     * Facturas no anuladas de un proveedor, sumadas por orden.
     *
     * @param proveedorId proveedor
     * @return una fila por consecutivo
     */
    @Query("""
            select new com.proyecto.supply_core.reporte.dto.SumaConsecutivo(f.orden.consecutivo, sum(f.total))
            from FacturaProveedor f
            where f.proveedor.id = :proveedorId
              and f.estado <> com.proyecto.supply_core.factura.enums.EstadoFactura.ANULADA
            group by f.orden.consecutivo
            """)
    List<SumaConsecutivo> facturadoPorOrden(@Param("proveedorId") Long proveedorId);
}
