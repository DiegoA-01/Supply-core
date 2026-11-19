package com.proyecto.supply_core.reorden.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.proyecto.supply_core.auditoria.enums.AccionAuditoria;
import com.proyecto.supply_core.auditoria.enums.EntidadAuditada;
import com.proyecto.supply_core.auditoria.service.AuditoriaService;
import com.proyecto.supply_core.catalogo.service.UnidadMedidaService;
import com.proyecto.supply_core.common.exception.NegocioException;
import com.proyecto.supply_core.compra.orden.enums.EstadoOrden;
import com.proyecto.supply_core.compra.solicitud.dto.LineaSolicitudRequest;
import com.proyecto.supply_core.compra.solicitud.dto.SolicitudRequest;
import com.proyecto.supply_core.compra.solicitud.dto.SolicitudResponse;
import com.proyecto.supply_core.compra.solicitud.entity.SolicitudCompraLinea;
import com.proyecto.supply_core.compra.solicitud.enums.EstadoSolicitud;
import com.proyecto.supply_core.compra.solicitud.service.SolicitudService;
import com.proyecto.supply_core.contrato.entity.Contrato;
import com.proyecto.supply_core.contrato.entity.ContratoPrecio;
import com.proyecto.supply_core.contrato.repository.ContratoRepository;
import com.proyecto.supply_core.inventario.entity.Producto;
import com.proyecto.supply_core.inventario.repository.ProductoRepository;
import com.proyecto.supply_core.inventario.repository.StockProductoBodegaRepository;
import com.proyecto.supply_core.inventario.repository.StockProductoBodegaRepository.TotalPorProducto;
import com.proyecto.supply_core.reorden.dto.BorradorReordenRequest;
import com.proyecto.supply_core.reorden.dto.CantidadPorProducto;
import com.proyecto.supply_core.reorden.dto.SugerenciaReorden;
import com.proyecto.supply_core.reorden.repository.ReordenRepository;

/**
 * Reorden sugerida (paso 18-d). Para cada producto activo por debajo de su stock mínimo calcula cuánto pedir:
 * <pre>
 * cantidad = stock máximo - stock actual - por recibir de órdenes - ya pedido en solicitudes
 * </pre>
 * <ul>
 *   <li><b>Por recibir de órdenes:</b> lo pedido en órdenes EMITIDA, ACEPTADA_PROVEEDOR, EN_PREPARACION, DESPACHADA
 *       o RECIBIDA_PARCIAL menos lo ya aceptado en sus recepciones. Lo rechazado sigue contando como pendiente.</li>
 *   <li><b>Ya pedido en solicitudes:</b> solicitudes PENDIENTE_APROBACION, APROBADA o EN_RFQ, convertidas a la unidad
 *       del producto. Las ADJUDICADA no cuentan para no duplicar con su orden.</li>
 *   <li>Sin stock máximo no se puede calcular: se marca {@code sinMaximo}. Si lo que viene en camino ya alcanza el
 *       máximo, la cantidad es 0 y se marca {@code yaCubierto}.</li>
 *   <li>Como pista se muestra el contrato vigente más barato del producto. No se elige proveedor.</li>
 * </ul>
 * <p><b>Solo sugiere.</b> El borrador que se puede crear es una solicitud BORRADOR del usuario: no compra ni aprueba
 * nada y sigue el flujo normal al enviarse (regla de oro 1: la persona decide).</p>
 */
@Service
public class ReordenService {

    /** Estados de orden cuya mercancía aún se espera. */
    static final Set<EstadoOrden> ORDENES_POR_RECIBIR = EnumSet.of(EstadoOrden.EMITIDA, EstadoOrden.ACEPTADA_PROVEEDOR,
            EstadoOrden.EN_PREPARACION, EstadoOrden.DESPACHADA, EstadoOrden.RECIBIDA_PARCIAL);
    /** Estados de solicitud que ya pidieron la mercancía y todavía no son una orden. */
    static final Set<EstadoSolicitud> SOLICITUDES_EN_CURSO = EnumSet.of(EstadoSolicitud.PENDIENTE_APROBACION,
            EstadoSolicitud.APROBADA, EstadoSolicitud.EN_RFQ);
    /** Texto del motivo de los borradores creados desde la reorden. */
    static final String MOTIVO_BORRADOR = "Reposición sugerida por stock bajo el mínimo";

    private final ProductoRepository productoRepository;
    private final StockProductoBodegaRepository stockRepository;
    private final ReordenRepository reordenRepository;
    private final ContratoRepository contratoRepository;
    private final UnidadMedidaService unidadMedidaService;
    private final SolicitudService solicitudService;
    private final AuditoriaService auditoriaService;

    /**
     * @param productoRepository  productos bajo el mínimo
     * @param stockRepository     stock total por producto
     * @param reordenRepository   lo que ya viene en camino
     * @param contratoRepository  contratos vigentes (pista de proveedor)
     * @param unidadMedidaService conversión de lo pedido en solicitudes a la unidad del producto
     * @param solicitudService    creación del borrador
     * @param auditoriaService    registro de borradores generados
     */
    public ReordenService(ProductoRepository productoRepository, StockProductoBodegaRepository stockRepository,
            ReordenRepository reordenRepository, ContratoRepository contratoRepository,
            UnidadMedidaService unidadMedidaService, SolicitudService solicitudService,
            AuditoriaService auditoriaService) {
        this.productoRepository = productoRepository;
        this.stockRepository = stockRepository;
        this.reordenRepository = reordenRepository;
        this.contratoRepository = contratoRepository;
        this.unidadMedidaService = unidadMedidaService;
        this.solicitudService = solicitudService;
        this.auditoriaService = auditoriaService;
    }

    /**
     * Sugerencias de reposición de los productos activos bajo su stock mínimo, por código de producto.
     *
     * @return sugerencias, vacío si ningún producto está bajo el mínimo
     */
    @Transactional(readOnly = true)
    public List<SugerenciaReorden> sugerencias() {
        return filas().stream().map(Fila::sugerencia).toList();
    }

    /**
     * Crea una solicitud en BORRADOR del usuario de la sesión con las cantidades sugeridas.
     *
     * @param req fecha requerida y, opcionalmente, los productos a incluir
     * @return solicitud en BORRADOR
     * @throws NegocioException si no hay nada que pedir, un producto pedido no tiene cantidad sugerida, o el usuario
     *                          no tiene centro de costo activo
     */
    @Transactional
    public SolicitudResponse crearBorrador(BorradorReordenRequest req) {
        List<Fila> candidatas = filas().stream().filter(f -> f.sugerencia().cantidadSugerida() != null
                && f.sugerencia().cantidadSugerida().signum() > 0).toList();
        List<Fila> elegidas = candidatas;
        if (req.productoIds() != null && !req.productoIds().isEmpty()) {
            Set<Long> pedidos = new HashSet<>(req.productoIds());
            elegidas = candidatas.stream().filter(f -> pedidos.contains(f.producto().getId())).toList();
            Set<Long> faltan = new HashSet<>(pedidos);
            elegidas.forEach(f -> faltan.remove(f.producto().getId()));
            if (!faltan.isEmpty()) {
                throw new NegocioException("PRODUCTO_SIN_SUGERENCIA", "Estos productos no tienen cantidad sugerida "
                        + "(no están bajo el mínimo, no tienen máximo o ya están cubiertos): " + faltan);
            }
        }
        if (elegidas.isEmpty()) {
            throw new NegocioException("SIN_SUGERENCIAS", "No hay productos con cantidad sugerida para pedir");
        }
        List<LineaSolicitudRequest> lineas = elegidas.stream().map(f -> new LineaSolicitudRequest(f.producto().getId(),
                f.producto().getUnidadMedida().getId(), f.sugerencia().cantidadSugerida())).toList();
        SolicitudResponse borrador = solicitudService.crear(new SolicitudRequest(MOTIVO_BORRADOR,
                req.fechaRequerida(), lineas));
        auditoriaService.exito(AccionAuditoria.REORDEN_GENERAR, EntidadAuditada.SOLICITUD_COMPRA, borrador.id(),
                borrador.consecutivo() + "; productos=" + elegidas.stream().map(f -> f.producto().getCodigo())
                        .collect(Collectors.joining(",")));
        return borrador;
    }

    // ----------------------------------------------------------------- apoyo

    /**
     * Producto junto con su sugerencia (el producto trae la unidad que necesita el borrador).
     *
     * @param producto   producto bajo el mínimo
     * @param sugerencia cálculo de la reposición
     */
    private record Fila(Producto producto, SugerenciaReorden sugerencia) {
    }

    /**
     * Calcula la sugerencia de cada producto bajo su mínimo.
     *
     * @return filas por código de producto
     */
    private List<Fila> filas() {
        List<Producto> productos = productoRepository.bajoStockMinimo();
        if (productos.isEmpty()) {
            return List.of();
        }
        List<Long> ids = productos.stream().map(Producto::getId).toList();
        Map<Long, BigDecimal> stock = stockRepository.totalesDe(ids).stream()
                .collect(Collectors.toMap(TotalPorProducto::getProductoId, TotalPorProducto::getTotal));
        Map<Long, BigDecimal> pedido = porProducto(reordenRepository.pedidoEnOrdenes(ids, ORDENES_POR_RECIBIR));
        Map<Long, BigDecimal> aceptado = porProducto(reordenRepository.aceptadoEnOrdenes(ids, ORDENES_POR_RECIBIR));
        Map<Long, BigDecimal> enSolicitudes = enSolicitudes(ids);
        Map<Long, SugerenciaReorden.ContratoSugerido> contratos = contratosSugeridos(ids);

        List<Fila> filas = new ArrayList<>();
        for (Producto p : productos) {
            BigDecimal actual = stock.getOrDefault(p.getId(), BigDecimal.ZERO);
            BigDecimal porRecibir = pedido.getOrDefault(p.getId(), BigDecimal.ZERO)
                    .subtract(aceptado.getOrDefault(p.getId(), BigDecimal.ZERO)).max(BigDecimal.ZERO);
            BigDecimal solicitado = enSolicitudes.getOrDefault(p.getId(), BigDecimal.ZERO);
            BigDecimal cantidad = null;
            boolean cubierto = false;
            if (p.getStockMaximo() != null) {
                BigDecimal falta = p.getStockMaximo().subtract(actual).subtract(porRecibir).subtract(solicitado);
                cubierto = falta.signum() <= 0;
                cantidad = cubierto ? BigDecimal.ZERO.setScale(4) : falta.setScale(4, RoundingMode.HALF_UP);
            }
            filas.add(new Fila(p, new SugerenciaReorden(p.getId(), p.getCodigo(), p.getNombre(),
                    p.getUnidadMedida().getCodigo(), actual, p.getStockMinimo(), p.getStockMaximo(), porRecibir,
                    solicitado, cantidad, p.getStockMaximo() == null, cubierto, contratos.get(p.getId()))));
        }
        return filas;
    }

    /**
     * Convierte las filas de una consulta agrupada en un mapa por producto.
     *
     * @param filas filas por producto
     * @return cantidad por id de producto
     */
    /**
     * Lo que falta por recibir de órdenes vivas (pedido menos aceptado, nunca negativo), por producto. Lo usa también
     * el plan de reabastecimiento.
     *
     * @param ids    productos
     * @param sedeId solo órdenes que llegan a bodegas de esta sede, o {@code null} para toda la empresa
     * @return cantidad pendiente por producto (solo los que tienen algo pendiente)
     */
    public Map<Long, BigDecimal> porRecibir(List<Long> ids, Long sedeId) {
        Map<Long, BigDecimal> pedido = porProducto(sedeId == null
                ? reordenRepository.pedidoEnOrdenes(ids, ORDENES_POR_RECIBIR)
                : reordenRepository.pedidoEnOrdenesDeSede(ids, ORDENES_POR_RECIBIR, sedeId));
        Map<Long, BigDecimal> aceptado = porProducto(sedeId == null
                ? reordenRepository.aceptadoEnOrdenes(ids, ORDENES_POR_RECIBIR)
                : reordenRepository.aceptadoEnOrdenesDeSede(ids, ORDENES_POR_RECIBIR, sedeId));
        Map<Long, BigDecimal> pendiente = new HashMap<>();
        pedido.forEach((id, cantidad) -> {
            BigDecimal falta = cantidad.subtract(aceptado.getOrDefault(id, BigDecimal.ZERO));
            if (falta.signum() > 0) {
                pendiente.put(id, falta);
            }
        });
        return pendiente;
    }

    private static Map<Long, BigDecimal> porProducto(List<CantidadPorProducto> filas) {
        Map<Long, BigDecimal> mapa = new HashMap<>();
        filas.forEach(f -> mapa.put(f.productoId(), f.cantidad()));
        return mapa;
    }

    /**
     * Suma lo pedido en solicitudes en curso, pasado a la unidad de cada producto.
     *
     * @param ids productos
     * @return cantidad por id de producto
     */
    public Map<Long, BigDecimal> enSolicitudes(List<Long> ids) {
        Map<Long, BigDecimal> mapa = new HashMap<>();
        for (SolicitudCompraLinea l : reordenRepository.lineasDeSolicitudes(ids, SOLICITUDES_EN_CURSO)) {
            BigDecimal cantidad = unidadMedidaService.convertir(l.getCantidad(), l.getUnidadMedida().getId(),
                    l.getProducto().getUnidadMedida().getId());
            mapa.merge(l.getProducto().getId(), cantidad, BigDecimal::add);
        }
        return mapa;
    }

    /**
     * Para cada producto, el contrato vigente de menor precio.
     *
     * @param ids productos
     * @return contrato sugerido por id de producto (faltan los que no tienen contrato)
     */
    private Map<Long, SugerenciaReorden.ContratoSugerido> contratosSugeridos(List<Long> ids) {
        Map<Long, SugerenciaReorden.ContratoSugerido> mapa = new HashMap<>();
        Set<Long> buscados = new HashSet<>(ids);
        for (Contrato c : contratoRepository.vigentesConProductos(ids, LocalDate.now())) {
            for (ContratoPrecio p : c.getPrecios()) {
                Long productoId = p.getProducto().getId();
                SugerenciaReorden.ContratoSugerido actual = mapa.get(productoId);
                if (buscados.contains(productoId)
                        && (actual == null || p.getPrecioUnitario().compareTo(actual.precioUnitario()) < 0)) {
                    mapa.put(productoId, new SugerenciaReorden.ContratoSugerido(c.getId(), c.getNumero(),
                            c.getProveedor().getId(), c.getProveedor().getRazonSocial(), p.getPrecioUnitario()));
                }
            }
        }
        return mapa;
    }
}
