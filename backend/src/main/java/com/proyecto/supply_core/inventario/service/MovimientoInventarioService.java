package com.proyecto.supply_core.inventario.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.proyecto.supply_core.auditoria.enums.AccionAuditoria;
import com.proyecto.supply_core.auditoria.enums.EntidadAuditada;
import com.proyecto.supply_core.auditoria.service.AuditoriaService;
import com.proyecto.supply_core.common.dto.PageResponse;
import com.proyecto.supply_core.common.exception.NegocioException;
import com.proyecto.supply_core.common.exception.NoEncontradoException;
import com.proyecto.supply_core.common.exception.ParametroInvalidoException;
import com.proyecto.supply_core.common.util.Ordenamiento;
import com.proyecto.supply_core.inventario.dto.MovimientoFiltro;
import com.proyecto.supply_core.inventario.dto.MovimientoRequest;
import com.proyecto.supply_core.inventario.dto.MovimientoResponse;
import com.proyecto.supply_core.inventario.dto.RegistroMovimientoResponse;
import com.proyecto.supply_core.inventario.dto.StockResponse;
import com.proyecto.supply_core.inventario.entity.Bodega;
import com.proyecto.supply_core.inventario.entity.MovimientoInventario;
import com.proyecto.supply_core.inventario.entity.Producto;
import com.proyecto.supply_core.inventario.entity.StockProductoBodega;
import com.proyecto.supply_core.inventario.enums.TipoMovimiento;
import com.proyecto.supply_core.inventario.repository.BodegaRepository;
import com.proyecto.supply_core.inventario.repository.MovimientoInventarioRepository;
import com.proyecto.supply_core.inventario.repository.MovimientoSpecs;
import com.proyecto.supply_core.inventario.repository.ProductoRepository;
import com.proyecto.supply_core.inventario.repository.StockProductoBodegaRepository;
import com.proyecto.supply_core.notificacion.enums.EventoNotificacion;
import com.proyecto.supply_core.notificacion.service.NotificacionService;
import com.proyecto.supply_core.security.context.Roles;
import com.proyecto.supply_core.security.context.SesionActual;

/**
 * ÚNICO punto del sistema que modifica el stock (UC-03 y regla que no se negocia).
 * <p>Cada cambio: valida producto y bodegas activos → bloquea la(s) fila(s) de stock
 * ({@code SELECT ... FOR UPDATE}) → verifica que no quede negativo → actualiza → registra el
 * movimiento con el saldo resultante. Todo en una transacción: si algo falla no cambia nada.</p>
 * <p>Las entradas por compra y devoluciones al proveedor solo llegan desde la recepción
 * ({@link #registrarEntradaCompra}, {@link #registrarDevolucionProveedor}).</p>
 */
@Service
public class MovimientoInventarioService {

    private static final Logger log = LoggerFactory.getLogger(MovimientoInventarioService.class);
    /** Escala de las cantidades ({@code DECIMAL(18,4)}). */
    private static final int ESCALA = 4;
    /** Campos por los que se puede ordenar el kardex. */
    private static final Set<String> CAMPOS_ORDENABLES = Set.of("creadoEn", "cantidad");

    private final StockProductoBodegaRepository stockRepository;
    private final MovimientoInventarioRepository movimientoRepository;
    private final ProductoRepository productoRepository;
    private final BodegaRepository bodegaRepository;
    private final AuditoriaService auditoriaService;
    private final NotificacionService notificacionService;
    /** Aviso por sede; {@code null} en las pruebas que no lo necesitan. */
    private final AlertaSedeService alertaSedeService;

    /**
     * @param stockRepository      existencias por bodega
     * @param movimientoRepository kardex
     * @param productoRepository   acceso a productos
     * @param bodegaRepository     acceso a bodegas
     * @param auditoriaService     registro de ajustes
     * @param notificacionService  aviso a compradores de stock bajo mínimo
     */
    public MovimientoInventarioService(StockProductoBodegaRepository stockRepository,
            MovimientoInventarioRepository movimientoRepository, ProductoRepository productoRepository,
            BodegaRepository bodegaRepository, AuditoriaService auditoriaService,
            NotificacionService notificacionService) {
        this(stockRepository, movimientoRepository, productoRepository, bodegaRepository, auditoriaService,
                notificacionService, null);
    }

    /**
     * @param stockRepository      existencias por bodega
     * @param movimientoRepository kardex
     * @param productoRepository   acceso a productos
     * @param bodegaRepository     acceso a bodegas
     * @param auditoriaService     registro de ajustes
     * @param notificacionService  aviso a compradores de stock bajo mínimo
     * @param alertaSedeService    aviso cuando una sede se queda corta
     */
    @org.springframework.beans.factory.annotation.Autowired
    public MovimientoInventarioService(StockProductoBodegaRepository stockRepository,
            MovimientoInventarioRepository movimientoRepository, ProductoRepository productoRepository,
            BodegaRepository bodegaRepository, AuditoriaService auditoriaService,
            NotificacionService notificacionService, AlertaSedeService alertaSedeService) {
        this.alertaSedeService = alertaSedeService;
        this.stockRepository = stockRepository;
        this.movimientoRepository = movimientoRepository;
        this.productoRepository = productoRepository;
        this.bodegaRepository = bodegaRepository;
        this.auditoriaService = auditoriaService;
        this.notificacionService = notificacionService;
    }

    /**
     * Registra un movimiento manual del almacenista (el request ya validó las reglas por tipo).
     *
     * @param req salida, ajuste o traslado
     * @return movimiento, stock total del producto y alerta de mínimo
     * @throws NoEncontradoException si el producto o una bodega no existen
     * @throws NegocioException      si están inactivos o el stock no alcanza
     */
    @Transactional
    public RegistroMovimientoResponse registrar(MovimientoRequest req) {
        if (!req.tipo().esManual()) { // respaldo si alguien llama al Service sin @Valid
            throw new NegocioException("TIPO_NO_MANUAL", req.tipo() + " solo se registra desde la recepción");
        }
        return aplicar(req.tipo(), req.productoId(), req.bodegaOrigenId(), req.bodegaDestinoId(), req.cantidad(),
                req.motivo(), null, null);
    }

    /**
     * Entrada por recepción de una orden de compra (la llama {@code RecepcionService}).
     * Es el único camino por el que una compra aumenta el stock.
     *
     * @param productoId     producto recibido
     * @param bodegaId       bodega donde se recibe
     * @param cantidad       cantidad aceptada (> 0)
     * @param referenciaTipo documento de origen (p. ej. {@code RECEPCION})
     * @param referenciaId   id del documento de origen
     * @return movimiento registrado
     */
    @Transactional
    public RegistroMovimientoResponse registrarEntradaCompra(Long productoId, Long bodegaId, BigDecimal cantidad,
            String referenciaTipo, Long referenciaId) {
        return registrarEntradaCompra(productoId, bodegaId, cantidad, null, referenciaTipo, referenciaId);
    }

    /**
     * Entrada por recepción con el costo de la compra, que queda en el movimiento (spec 008).
     *
     * @param productoId     producto recibido
     * @param bodegaId       bodega donde se recibe
     * @param cantidad       cantidad aceptada (> 0)
     * @param costoCompra    costo unitario de la compra ({@code null} = el promedio vigente)
     * @param referenciaTipo documento de origen (p. ej. {@code RECEPCION})
     * @param referenciaId   id del documento de origen
     * @return movimiento registrado
     */
    @Transactional
    public RegistroMovimientoResponse registrarEntradaCompra(Long productoId, Long bodegaId, BigDecimal cantidad,
            BigDecimal costoCompra, String referenciaTipo, Long referenciaId) {
        return aplicar(TipoMovimiento.ENTRADA_COMPRA, productoId, null, bodegaId, cantidad, null,
                referenciaTipo, referenciaId, costoCompra);
    }

    /**
     * Salida por devolución al proveedor de mercancía que ya había entrado al stock. Lo rechazado en la
     * recepción nunca entró, así que no pasa por aquí.
     *
     * @param productoId     producto devuelto
     * @param bodegaId       bodega de la que sale
     * @param cantidad       cantidad devuelta (> 0)
     * @param motivo         motivo obligatorio
     * @param referenciaTipo documento de origen
     * @param referenciaId   id del documento de origen
     * @return movimiento registrado
     */
    @Transactional
    public RegistroMovimientoResponse registrarDevolucionProveedor(Long productoId, Long bodegaId, BigDecimal cantidad,
            String motivo, String referenciaTipo, Long referenciaId) {
        if (motivo == null || motivo.isBlank()) {
            throw new NegocioException("MOTIVO_OBLIGATORIO", "La devolución al proveedor exige motivo");
        }
        return aplicar(TipoMovimiento.DEVOLUCION_PROVEEDOR, productoId, bodegaId, null, cantidad, motivo,
                referenciaTipo, referenciaId);
    }

    /**
     * Salida por un despacho confirmado (venta o entrega). Solo la llama {@code DespachoService}.
     *
     * @param productoId     producto despachado
     * @param bodegaId       bodega de la que sale
     * @param cantidad       cantidad en la unidad del producto (> 0)
     * @param referenciaTipo documento de origen ({@code DESPACHO})
     * @param referenciaId   id del despacho
     * @return movimiento registrado
     * @throws NegocioException si el stock no alcanza (STOCK_INSUFICIENTE)
     */
    @Transactional
    public RegistroMovimientoResponse registrarSalidaVenta(Long productoId, Long bodegaId, BigDecimal cantidad,
            String referenciaTipo, Long referenciaId) {
        return aplicar(TipoMovimiento.SALIDA_VENTA, productoId, bodegaId, null, cantidad, null,
                referenciaTipo, referenciaId);
    }

    /**
     * Entrada que devuelve al stock lo de un despacho anulado. Solo la llama {@code DespachoService}.
     *
     * @param productoId     producto
     * @param bodegaId       bodega a la que vuelve
     * @param cantidad       cantidad en la unidad del producto (> 0)
     * @param motivo         motivo de la anulación (obligatorio)
     * @param referenciaTipo documento de origen ({@code DESPACHO})
     * @param referenciaId   id del despacho
     * @return movimiento registrado
     */
    @Transactional
    public RegistroMovimientoResponse registrarAnulacionDespacho(Long productoId, Long bodegaId, BigDecimal cantidad,
            String motivo, String referenciaTipo, Long referenciaId) {
        if (motivo == null || motivo.isBlank()) {
            throw new NegocioException("MOTIVO_OBLIGATORIO", "La anulación de un despacho exige motivo");
        }
        return aplicar(TipoMovimiento.ANULACION_DESPACHO, productoId, null, bodegaId, cantidad, motivo,
                referenciaTipo, referenciaId);
    }

    /**
     * Entrada que devuelve al stock lo de una venta anulada en su turno (spec 006). Solo la llama la anulación.
     *
     * @param productoId producto
     * @param bodegaId   bodega de la caja
     * @param cantidad   cantidad en la unidad del producto (> 0)
     * @param motivo     motivo de la anulación
     * @param ventaId    venta anulada
     * @return movimiento registrado
     */
    @Transactional
    public RegistroMovimientoResponse registrarAnulacionVenta(Long productoId, Long bodegaId, BigDecimal cantidad,
            String motivo, Long ventaId) {
        return aplicar(TipoMovimiento.ANULACION_VENTA, productoId, null, bodegaId, cantidad, motivo, "VENTA", ventaId);
    }

    /**
     * Entrada por la mercancía que devuelve un cliente (spec 006). Solo la llama la devolución.
     *
     * @param productoId   producto
     * @param bodegaId     bodega de venta (buen estado) o de averías (dañado)
     * @param cantidad     cantidad en la unidad del producto (> 0)
     * @param motivo       motivo de la devolución
     * @param devolucionId devolución
     * @return movimiento registrado
     */
    @Transactional
    public RegistroMovimientoResponse registrarDevolucionCliente(Long productoId, Long bodegaId, BigDecimal cantidad,
            String motivo, Long devolucionId) {
        return aplicar(TipoMovimiento.DEVOLUCION_CLIENTE, productoId, null, bodegaId, cantidad, motivo, "DEVOLUCION_VENTA",
                devolucionId);
    }

    /**
     * Kardex con filtros, más recientes primero.
     *
     * @param filtro   filtros validados
     * @param pageable página y orden (solo campos de {@link #CAMPOS_ORDENABLES})
     * @return página de movimientos
     * @throws ParametroInvalidoException si se pide ordenar por un campo no permitido
     */
    @Transactional(readOnly = true)
    public PageResponse<MovimientoResponse> buscar(MovimientoFiltro filtro, Pageable pageable) {
        return PageResponse.of(movimientoRepository.findAll(MovimientoSpecs.conFiltro(filtro),
                Ordenamiento.validar(pageable, CAMPOS_ORDENABLES)), MovimientoResponse::of);
    }

    /**
     * Existencias por producto y bodega.
     *
     * @param productoId filtro opcional
     * @param bodegaId   filtro opcional
     * @return filas de stock
     */
    @Transactional(readOnly = true)
    public List<StockResponse> existencias(Long productoId, Long bodegaId) {
        return stockRepository.buscar(productoId, bodegaId).stream().map(StockResponse::of).toList();
    }

    // ----------------------------------------------------------------- núcleo

    /**
     * Aplica un movimiento de cualquier tipo de forma atómica y segura ante concurrencia.
     *
     * @param tipo           tipo de movimiento
     * @param productoId     producto
     * @param origenId       bodega de origen ({@code null} si el tipo no la usa)
     * @param destinoId      bodega de destino ({@code null} si el tipo no la usa)
     * @param cantidadBruta  cantidad recibida
     * @param motivo         motivo (puede ser {@code null})
     * @param referenciaTipo documento de origen o {@code null}
     * @param referenciaId   id del documento de origen o {@code null}
     * @return movimiento, total del producto y alerta
     * @throws NoEncontradoException si producto o bodega no existen
     * @throws NegocioException      si están inactivos, la cantidad no es positiva o el stock no alcanza
     */
    private RegistroMovimientoResponse aplicar(TipoMovimiento tipo, Long productoId, Long origenId, Long destinoId,
            BigDecimal cantidadBruta, String motivo, String referenciaTipo, Long referenciaId) {
        return aplicar(tipo, productoId, origenId, destinoId, cantidadBruta, motivo, referenciaTipo, referenciaId, null);
    }

    /**
     * Igual que la otra versión, con el costo unitario del movimiento.
     *
     * @param tipo           tipo de movimiento
     * @param productoId     producto
     * @param origenId       bodega de origen o {@code null}
     * @param destinoId      bodega de destino o {@code null}
     * @param cantidadBruta  cantidad (> 0)
     * @param motivo         motivo o {@code null}
     * @param referenciaTipo documento de origen o {@code null}
     * @param referenciaId   id del documento de origen o {@code null}
     * @param costo          costo unitario ({@code null} = el costo vigente del producto)
     * @return movimiento, total del producto y alerta
     */
    private RegistroMovimientoResponse aplicar(TipoMovimiento tipo, Long productoId, Long origenId, Long destinoId,
            BigDecimal cantidadBruta, String motivo, String referenciaTipo, Long referenciaId, BigDecimal costo) {
        BigDecimal cantidad = cantidadBruta.setScale(ESCALA, RoundingMode.HALF_UP);
        if (cantidad.signum() <= 0) {
            throw new NegocioException("CANTIDAD_INVALIDA", "La cantidad debe ser mayor que cero");
        }
        Producto producto = productoActivo(productoId);
        Bodega origen = tipo.usaOrigen() ? bodegaActiva(origenId) : null;
        Bodega destino = tipo.usaDestino() ? bodegaActiva(destinoId) : null;

        // Bloqueo en orden de id de bodega: dos traslados cruzados (A→B y B→A) no se bloquean mutuamente
        StockProductoBodega stockOrigen = null;
        StockProductoBodega stockDestino = null;
        if (origen != null && destino != null && destino.getId() < origen.getId()) {
            stockDestino = bloquear(producto, destino);
            stockOrigen = bloquear(producto, origen);
        } else {
            stockOrigen = origen == null ? null : bloquear(producto, origen);
            stockDestino = destino == null ? null : bloquear(producto, destino);
        }

        if (stockOrigen != null) {
            BigDecimal saldo = stockOrigen.getCantidad().subtract(cantidad);
            if (saldo.signum() < 0) {
                throw new NegocioException("STOCK_INSUFICIENTE", "Stock insuficiente de " + producto.getCodigo()
                        + " en " + origen.getNombre() + ": disponible " + stockOrigen.getCantidad().toPlainString()
                        + ", solicitado " + cantidad.toPlainString());
            }
            stockOrigen.setCantidad(saldo);
        }
        if (stockDestino != null) {
            stockDestino.setCantidad(stockDestino.getCantidad().add(cantidad));
        }

        MovimientoInventario m = new MovimientoInventario();
        m.setProducto(producto);
        m.setBodegaOrigen(origen);
        m.setBodegaDestino(destino);
        m.setUsuarioId(SesionActual.usuario().id());
        m.setTipo(tipo);
        m.setCantidad(cantidad);
        m.setStockResultante(stockOrigen != null ? stockOrigen.getCantidad() : stockDestino.getCantidad());
        m.setStockResultanteDestino(tipo == TipoMovimiento.TRASLADO ? stockDestino.getCantidad() : null);
        m.setMotivo(motivo == null || motivo.isBlank() ? null : motivo.trim());
        m.setReferenciaTipo(referenciaTipo);
        m.setReferenciaId(referenciaId);
        m.setCostoUnitario(costo != null ? costo : producto.costoVigente());
        MovimientoInventario guardado = movimientoRepository.save(m);

        if (tipo == TipoMovimiento.AJUSTE_POSITIVO || tipo == TipoMovimiento.AJUSTE_NEGATIVO) {
            auditoriaService.exito(AccionAuditoria.INVENTARIO_AJUSTE, EntidadAuditada.MOVIMIENTO_INVENTARIO,
                    guardado.getId(), tipo + " " + cantidad.toPlainString() + " de " + producto.getCodigo()
                            + "; motivo=" + guardado.getMotivo());
        }

        if (alertaSedeService != null && origen != null) {
            alertaSedeService.revisar(producto, origen, destino, cantidad);
        }
        BigDecimal total = stockRepository.totalDeProducto(producto.getId());
        boolean bajoMinimo = total.compareTo(producto.getStockMinimo()) < 0;
        if (bajoMinimo) {
            log.warn("Producto {} bajo stock mínimo: total {} < mínimo {}", producto.getCodigo(),
                    total.toPlainString(), producto.getStockMinimo().toPlainString());
            if (cruzoMinimo(tipo, cantidad, total, producto.getStockMinimo())) {
                notificacionService.notificarRol(Roles.COMPRADOR, EventoNotificacion.STOCK_BAJO_MINIMO,
                        "El producto " + producto.getCodigo() + " - " + producto.getNombre()
                                + " quedó bajo el stock mínimo: total " + total.toPlainString() + " < mínimo "
                                + producto.getStockMinimo().toPlainString());
            }
        }
        return new RegistroMovimientoResponse(MovimientoResponse.of(guardado), total, bajoMinimo);
    }

    /**
     * Indica si este movimiento hizo que el total pasara de "en o sobre el mínimo" a "bajo el mínimo".
     * Así se avisa una sola vez al cruzar el umbral y no en cada salida posterior.
     *
     * @param tipo       tipo de movimiento
     * @param cantidad   cantidad movida
     * @param totalAhora total del producto después del movimiento
     * @param minimo     stock mínimo del producto
     * @return {@code true} si antes del movimiento no estaba bajo el mínimo
     */
    static boolean cruzoMinimo(TipoMovimiento tipo, BigDecimal cantidad, BigDecimal totalAhora, BigDecimal minimo) {
        BigDecimal totalAntes = totalAhora;
        if (tipo.usaOrigen() && !tipo.usaDestino()) {
            totalAntes = totalAhora.add(cantidad);
        } else if (tipo.usaDestino() && !tipo.usaOrigen()) {
            totalAntes = totalAhora.subtract(cantidad);
        }
        return totalAhora.compareTo(minimo) < 0 && totalAntes.compareTo(minimo) >= 0;
    }

    /**
     * Crea la fila de stock si no existe y la bloquea hasta el fin de la transacción.
     *
     * @param producto producto
     * @param bodega   bodega
     * @return fila bloqueada
     */
    private StockProductoBodega bloquear(Producto producto, Bodega bodega) {
        stockRepository.crearSiNoExiste(producto.getId(), bodega.getId());
        return stockRepository.bloquear(producto.getId(), bodega.getId())
                .orElseThrow(() -> new IllegalStateException("No se pudo bloquear el stock de "
                        + producto.getCodigo() + " en " + bodega.getNombre()));
    }

    /**
     * Carga el producto y verifica que esté activo (UC-03: precondición).
     *
     * @param id id del producto
     * @return producto
     * @throws NoEncontradoException si no existe
     * @throws NegocioException      si está inactivo
     */
    private Producto productoActivo(Long id) {
        Producto p = productoRepository.findWithRelacionesById(id).orElseThrow(() -> new NoEncontradoException("Producto", id));
        if (!p.isActivo()) {
            throw new NegocioException("PRODUCTO_INACTIVO", "El producto " + p.getCodigo() + " está inactivo");
        }
        return p;
    }

    /**
     * Carga la bodega y verifica que esté activa.
     *
     * @param id id de la bodega
     * @return bodega
     * @throws NoEncontradoException si no existe
     * @throws NegocioException      si está inactiva
     */
    private Bodega bodegaActiva(Long id) {
        Bodega b = bodegaRepository.findById(id).orElseThrow(() -> new NoEncontradoException("Bodega", id));
        if (!b.isActivo()) {
            throw new NegocioException("BODEGA_INACTIVA", "La bodega " + b.getNombre() + " está inactiva");
        }
        return b;
    }
}
