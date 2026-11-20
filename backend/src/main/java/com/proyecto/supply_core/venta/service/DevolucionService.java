package com.proyecto.supply_core.venta.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.proyecto.supply_core.auditoria.enums.AccionAuditoria;
import com.proyecto.supply_core.auditoria.enums.EntidadAuditada;
import com.proyecto.supply_core.auditoria.service.AuditoriaService;
import com.proyecto.supply_core.caja.entity.MovimientoCaja;
import com.proyecto.supply_core.caja.entity.TurnoCaja;
import com.proyecto.supply_core.caja.enums.AccionAutorizada;
import com.proyecto.supply_core.caja.enums.EstadoTurno;
import com.proyecto.supply_core.caja.enums.MedioPago;
import com.proyecto.supply_core.caja.enums.TipoMovimientoCaja;
import com.proyecto.supply_core.caja.repository.MovimientoCajaRepository;
import com.proyecto.supply_core.caja.repository.TurnoCajaRepository;
import com.proyecto.supply_core.caja.service.AutorizacionService;
import com.proyecto.supply_core.cartera.service.CarteraService;
import com.proyecto.supply_core.catalogo.entity.UnidadMedida;
import com.proyecto.supply_core.catalogo.repository.UnidadMedidaRepository;
import com.proyecto.supply_core.cliente.entity.Cliente;
import com.proyecto.supply_core.cliente.repository.ClienteRepository;
import com.proyecto.supply_core.common.dto.PageResponse;
import com.proyecto.supply_core.common.exception.NegocioException;
import com.proyecto.supply_core.common.exception.NoEncontradoException;
import com.proyecto.supply_core.common.tenant.EmpresaContexto;
import com.proyecto.supply_core.common.util.ConsecutivoGenerator;
import com.proyecto.supply_core.empresa.repository.EmpresaRepository;
import com.proyecto.supply_core.inventario.entity.Bodega;
import com.proyecto.supply_core.inventario.entity.Producto;
import com.proyecto.supply_core.inventario.repository.BodegaRepository;
import com.proyecto.supply_core.inventario.repository.ProductoRepository;
import com.proyecto.supply_core.inventario.service.MovimientoInventarioService;
import com.proyecto.supply_core.security.context.SesionActual;
import com.proyecto.supply_core.usuario.entity.Usuario;
import com.proyecto.supply_core.usuario.repository.UsuarioRepository;
import com.proyecto.supply_core.venta.dto.DevolucionLineaRequest;
import com.proyecto.supply_core.venta.dto.DevolucionLineaResponse;
import com.proyecto.supply_core.venta.dto.DevolucionRequest;
import com.proyecto.supply_core.venta.dto.DevolucionResponse;
import com.proyecto.supply_core.venta.entity.DevolucionVenta;
import com.proyecto.supply_core.venta.entity.DevolucionVentaLinea;
import com.proyecto.supply_core.venta.entity.SaldoFavorCliente;
import com.proyecto.supply_core.venta.entity.Venta;
import com.proyecto.supply_core.venta.entity.VentaLinea;
import com.proyecto.supply_core.venta.enums.EstadoProductoDevuelto;
import com.proyecto.supply_core.venta.enums.EstadoVenta;
import com.proyecto.supply_core.venta.enums.FormaReembolso;
import com.proyecto.supply_core.venta.enums.MedioPagoVenta;
import com.proyecto.supply_core.venta.repository.DevolucionVentaRepository;
import com.proyecto.supply_core.venta.repository.SaldoFavorClienteRepository;
import com.proyecto.supply_core.venta.repository.VentaRepository;

/**
 * Devolución de cliente (spec 006), cualquier día, desde el turno abierto de quien la recibe.
 * <ul>
 *   <li>Nunca se devuelve más de lo vendido menos lo ya devuelto: la venta se bloquea mientras se revisa, así dos
 *       devoluciones a la vez van en fila.</li>
 *   <li>El valor sale de la foto fija de la línea vendida (proporcional a la cantidad; la última devolución de una
 *       línea toma lo que falte, para que la suma nunca pase del total de la línea).</li>
 *   <li>Lo que vuelve en buen estado entra a la bodega de la venta ({@code DEVOLUCION_CLIENTE}); lo dañado va a la
 *       bodega de averías de la empresa o no reingresa si no tiene una.</li>
 *   <li>Reembolso: efectivo del turno, el mismo medio de pago (solo si la venta se pagó con uno) o saldo a favor del
 *       cliente (no al consumidor final).</li>
 *   <li>Siempre con autorización de un supervisor, consecutivo propio ({@code DV}) y auditoría.</li>
 * </ul>
 */
@Service
public class DevolucionService {

    /** Tipo de documento con que la devolución referencia el inventario y la caja. */
    static final String REFERENCIA = "DEVOLUCION_VENTA";

    private final DevolucionVentaRepository devolucionRepository;
    private final VentaRepository ventaRepository;
    private final TurnoCajaRepository turnoRepository;
    private final MovimientoCajaRepository movimientoCajaRepository;
    private final SaldoFavorClienteRepository saldoRepository;
    private final ClienteRepository clienteRepository;
    private final ProductoRepository productoRepository;
    private final UnidadMedidaRepository unidadRepository;
    private final BodegaRepository bodegaRepository;
    private final EmpresaRepository empresaRepository;
    private final UsuarioRepository usuarioRepository;
    private final MovimientoInventarioService movimientoInventarioService;
    private final AutorizacionService autorizacionService;
    private final ConsecutivoGenerator consecutivos;
    private final AuditoriaService auditoriaService;
    private final CarteraService carteraService;

    /**
     * @param devolucionRepository        devoluciones
     * @param ventaRepository             ventas
     * @param turnoRepository             turno de quien recibe
     * @param movimientoCajaRepository    libro de caja
     * @param saldoRepository             saldo a favor
     * @param clienteRepository           clientes
     * @param productoRepository          productos
     * @param unidadRepository            unidades
     * @param bodegaRepository            bodegas (nombres)
     * @param empresaRepository           bodega de averías
     * @param usuarioRepository           nombres
     * @param movimientoInventarioService entrada por devolución
     * @param autorizacionService         permiso del supervisor
     * @param consecutivos                consecutivo {@code DV}
     * @param auditoriaService            registro de la devolución
     * @param carteraService              devoluciones de ventas a crédito (spec 007)
     */
    public DevolucionService(DevolucionVentaRepository devolucionRepository, VentaRepository ventaRepository,
            TurnoCajaRepository turnoRepository, MovimientoCajaRepository movimientoCajaRepository,
            SaldoFavorClienteRepository saldoRepository, ClienteRepository clienteRepository,
            ProductoRepository productoRepository, UnidadMedidaRepository unidadRepository,
            BodegaRepository bodegaRepository, EmpresaRepository empresaRepository, UsuarioRepository usuarioRepository,
            MovimientoInventarioService movimientoInventarioService, AutorizacionService autorizacionService,
            ConsecutivoGenerator consecutivos, AuditoriaService auditoriaService, CarteraService carteraService) {
        this.devolucionRepository = devolucionRepository;
        this.ventaRepository = ventaRepository;
        this.turnoRepository = turnoRepository;
        this.movimientoCajaRepository = movimientoCajaRepository;
        this.saldoRepository = saldoRepository;
        this.clienteRepository = clienteRepository;
        this.productoRepository = productoRepository;
        this.unidadRepository = unidadRepository;
        this.bodegaRepository = bodegaRepository;
        this.empresaRepository = empresaRepository;
        this.usuarioRepository = usuarioRepository;
        this.movimientoInventarioService = movimientoInventarioService;
        this.autorizacionService = autorizacionService;
        this.consecutivos = consecutivos;
        this.auditoriaService = auditoriaService;
        this.carteraService = carteraService;
    }

    /**
     * @param req venta, líneas, estado de cada producto, forma de reembolso y autorización
     * @return la devolución
     * @throws NoEncontradoException si la venta o una línea no son de la empresa
     * @throws NegocioException      sin turno abierto, venta anulada, cantidad que pasa lo devolvible, reembolso que no
     *                               aplica, autorización inválida o efectivo insuficiente
     */
    @Transactional
    public DevolucionResponse devolver(DevolucionRequest req) {
        Long yo = SesionActual.usuario().id();
        // La venta se bloquea ANTES de cualquier otra lectura: en MySQL (lectura repetible) la foto de la transacción
        // se toma en la primera lectura normal, y así ya incluye las devoluciones que otra caja terminó mientras se esperaba.
        Venta v = ventaRepository.bloquear(req.ventaId()).orElseThrow(() -> new NoEncontradoException("Venta no encontrada"));
        // Turno bloqueado en la misma lectura: si se cerró mientras se esperaba, no se devuelve en un turno cerrado
        TurnoCaja turno = turnoRepository.abiertoDeBloqueado(yo).orElseThrow(
                () -> new NegocioException("TURNO_NO_ABIERTO", "Abra su turno de caja para recibir la devolución"));
        if (v.getEstado() != EstadoVenta.CONFIRMADA) {
            throw new NegocioException("VENTA_ANULADA", "La venta " + v.getConsecutivo() + " está anulada: no tiene qué devolver");
        }
        Map<Long, VentaLinea> lineas = v.getLineas().stream().collect(Collectors.toMap(VentaLinea::getId, Function.identity()));
        Map<Long, DevolucionVentaRepository.Devuelto> previo = devolucionRepository.devueltoPorLinea(v.getId()).stream()
                .collect(Collectors.toMap(DevolucionVentaRepository.Devuelto::ventaLineaId, Function.identity()));
        Map<Long, Producto> productos = productoRepository.findAllById(v.getLineas().stream().map(VentaLinea::getProductoId)
                .collect(Collectors.toSet())).stream().collect(Collectors.toMap(Producto::getId, Function.identity()));
        Long averias = empresaRepository.findById(EmpresaContexto.requerida()).map(e -> e.getBodegaAveriasId()).orElse(null);
        String motivo = req.motivo().trim();

        DevolucionVenta d = new DevolucionVenta();
        Set<Long> vistas = new HashSet<>();
        Set<Long> completas = new HashSet<>();
        BigDecimal total = BigDecimal.ZERO.setScale(2);
        for (DevolucionLineaRequest r : req.lineas()) {
            VentaLinea l = lineas.get(r.ventaLineaId());
            if (l == null) {
                throw new NoEncontradoException("Línea de la venta no encontrada");
            }
            if (!vistas.add(l.getId())) {
                throw new NegocioException("LINEA_REPETIDA", "Cada línea se devuelve una sola vez por devolución");
            }
            DevolucionVentaRepository.Devuelto antes = previo.get(l.getId());
            BigDecimal cantidad = r.cantidad().setScale(4, RoundingMode.HALF_UP);
            BigDecimal restante = l.getCantidad().subtract(antes == null ? BigDecimal.ZERO : antes.cantidad());
            Producto producto = productos.get(l.getProductoId());
            if (cantidad.compareTo(restante) > 0) {
                throw new NegocioException("CANTIDAD_DEVOLUCION", "De " + (producto == null ? "la línea" : producto.getNombre())
                        + " se pueden devolver como máximo " + restante.stripTrailingZeros().toPlainString());
            }
            boolean ultima = cantidad.compareTo(restante) == 0;
            BigDecimal valor = ultima ? l.getTotalLinea().subtract(antes == null ? BigDecimal.ZERO : antes.valor())
                    : l.getTotalLinea().multiply(cantidad).divide(l.getCantidad(), 2, RoundingMode.HALF_UP);
            BigDecimal enUnidadProducto = ultima
                    ? l.getCantidadUnidadProducto().subtract(antes == null ? BigDecimal.ZERO : antes.cantidadUnidadProducto())
                    : l.getCantidadUnidadProducto().multiply(cantidad).divide(l.getCantidad(), 4, RoundingMode.HALF_UP);
            DevolucionVentaLinea x = new DevolucionVentaLinea();
            x.setDevolucion(d);
            x.setVentaLineaId(l.getId());
            x.setCantidad(cantidad);
            x.setCantidadUnidadProducto(enUnidadProducto);
            x.setEstadoProducto(r.estadoProducto());
            x.setBodegaId(r.estadoProducto() == EstadoProductoDevuelto.BUENO ? v.getBodegaId() : averias);
            x.setValor(valor);
            d.getLineas().add(x);
            total = total.add(valor);
            if (ultima) {
                completas.add(l.getId());
            }
        }
        // Con esta devolución la venta queda devuelta del todo: se devuelve también el ajuste del redondeo (D3), para que
        // el reembolso sume exactamente lo que el cliente pagó y no lo que decía la línea antes de redondear.
        boolean ventaCompleta = v.getLineas().stream().allMatch(l -> completas.contains(l.getId())
                || (previo.get(l.getId()) != null && previo.get(l.getId()).cantidad().compareTo(l.getCantidad()) >= 0));
        if (ventaCompleta && v.getRedondeo() != null && v.getRedondeo().signum() != 0) {
            total = total.add(v.getRedondeo()).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
        }

        Set<MedioPagoVenta> mediosVenta = v.getPagos().stream().map(p -> p.getMedio()).collect(Collectors.toSet());
        if (req.formaReembolso() == FormaReembolso.EFECTIVO && mediosVenta.contains(MedioPagoVenta.SALDO_FAVOR)) {
            throw new NegocioException("REEMBOLSO_SALDO",
                    "Parte de la venta se pagó con saldo a favor: devuelva como saldo a favor, no en efectivo");
        }
        // Una venta a crédito rebaja primero su propia deuda; solo el resto se reembolsa por la forma pedida
        BigDecimal aDeuda = mediosVenta.contains(MedioPagoVenta.CREDITO)
                ? carteraService.aplicarDevolucionADeuda(v, total) : BigDecimal.ZERO.setScale(2);
        BigDecimal resto = total.subtract(aDeuda);

        MedioPagoVenta original = medioUnico(v);
        // Mismo medio en una venta a crédito o con saldo a favor: lo que sobre de la deuda queda como saldo a favor
        boolean aSaldo = req.formaReembolso() == FormaReembolso.SALDO_A_FAVOR
                || (req.formaReembolso() == FormaReembolso.MISMO_MEDIO
                        && (original == MedioPagoVenta.CREDITO || original == MedioPagoVenta.SALDO_FAVOR));
        MedioPago medio = aSaldo || resto.signum() == 0 ? null : medioDeReembolso(req.formaReembolso(), v);
        if (medio == MedioPago.EFECTIVO
                && movimientoCajaRepository.saldo(turno.getId(), MedioPago.EFECTIVO).compareTo(resto) < 0) {
            throw new NegocioException("EFECTIVO_INSUFICIENTE", "No hay efectivo suficiente en la caja para devolver "
                    + resto.toPlainString() + ": elija otro medio o saldo a favor");
        }
        if (aSaldo && resto.signum() > 0) {
            Cliente c = clienteRepository.findById(v.getClienteId()).orElseThrow(() -> new NoEncontradoException("Cliente no encontrado"));
            if (c.isEsConsumidorFinal()) {
                throw new NegocioException("SALDO_CONSUMIDOR_FINAL",
                        "El consumidor final no puede tener saldo a favor: registre el cliente o devuelva el dinero");
            }
        }
        Long supervisor = autorizacionService.consumir(req.autorizacion(), AccionAutorizada.DEVOLUCION, turno.getId(), v.getId());

        d.setConsecutivo(consecutivos.siguiente(ConsecutivoGenerator.DEVOLUCION_VENTA));
        d.setVentaId(v.getId());
        d.setTurnoId(turno.getId());
        d.setMotivo(motivo);
        d.setFormaReembolso(req.formaReembolso());
        d.setMedioReembolso(medio);
        d.setTotalReembolso(total);
        d.setUsuarioId(yo);
        d.setAutorizadoPorUsuarioId(supervisor);
        d.setCreadoEn(LocalDateTime.now());
        devolucionRepository.saveAndFlush(d);

        // Stock: lo que reingresa, por producto en orden de id
        Map<Long, Map<Long, BigDecimal>> entradas = new TreeMap<>();
        for (DevolucionVentaLinea x : d.getLineas()) {
            if (x.getBodegaId() != null) {
                Long productoId = lineas.get(x.getVentaLineaId()).getProductoId();
                entradas.computeIfAbsent(productoId, k -> new TreeMap<>()).merge(x.getBodegaId(),
                        x.getCantidadUnidadProducto(), BigDecimal::add);
            }
        }
        entradas.forEach((productoId, porBodega) -> porBodega.forEach((bodegaId, cantidad) ->
                movimientoInventarioService.registrarDevolucionCliente(productoId, bodegaId, cantidad,
                        "Devolución " + d.getConsecutivo() + " de " + v.getConsecutivo() + ": " + motivo, d.getId())));

        if (resto.signum() > 0) {
            if (medio != null) {
                MovimientoCaja m = new MovimientoCaja();
                m.setTurnoId(turno.getId());
                m.setTipo(TipoMovimientoCaja.DEVOLUCION);
                m.setMedioPago(medio);
                m.setMonto(resto.negate());
                m.setReferenciaTipo(REFERENCIA);
                m.setReferenciaId(d.getId());
                m.setMotivo("Devolución " + d.getConsecutivo() + " de " + v.getConsecutivo());
                m.setUsuarioId(yo);
                m.setAutorizadoPorUsuarioId(supervisor);
                movimientoCajaRepository.save(m);
            } else {
                SaldoFavorCliente s = new SaldoFavorCliente();
                s.setClienteId(v.getClienteId());
                s.setDevolucionId(d.getId());
                s.setMonto(resto);
                s.setMotivo("Devolución " + d.getConsecutivo() + " de " + v.getConsecutivo());
                saldoRepository.save(s);
            }
        }
        auditoriaService.exito(AccionAuditoria.VENTA_DEVOLVER, EntidadAuditada.DEVOLUCION_VENTA, d.getId(), d.getConsecutivo()
                + "; venta=" + v.getConsecutivo() + "; total=" + total.toPlainString() + "; aDeuda=" + aDeuda.toPlainString()
                + "; reembolso=" + req.formaReembolso()
                + (medio == null ? "" : " " + medio) + "; autorizó=" + supervisor + "; motivo=" + motivo);
        return respuesta(d, v);
    }

    /**
     * @param ventaId  venta ({@code null} = todas)
     * @param pageable página y orden
     * @return devoluciones
     */
    @Transactional(readOnly = true)
    public PageResponse<DevolucionResponse> buscar(Long ventaId, Pageable pageable) {
        return PageResponse.of(devolucionRepository.buscar(ventaId, pageable),
                d -> respuesta(d, ventaRepository.findById(d.getVentaId()).orElseThrow()));
    }

    /**
     * @param id devolución
     * @return devolución con sus líneas
     * @throws NoEncontradoException si no es de la empresa
     */
    @Transactional(readOnly = true)
    public DevolucionResponse obtener(Long id) {
        DevolucionVenta d = devolucionRepository.findById(id).orElseThrow(() -> new NoEncontradoException("Devolución no encontrada"));
        return respuesta(d, ventaRepository.findById(d.getVentaId()).orElseThrow());
    }

    /**
     * @param v venta
     * @return el único medio con que se pagó ({@code null} si fueron varios)
     */
    static MedioPagoVenta medioUnico(Venta v) {
        Set<MedioPagoVenta> medios = v.getPagos().stream().map(p -> p.getMedio()).collect(Collectors.toSet());
        return medios.size() == 1 ? medios.iterator().next() : null;
    }

    /**
     * @param forma forma pedida
     * @param v     venta original
     * @return medio de caja por el que sale el dinero ({@code null} si queda como saldo a favor)
     * @throws NegocioException si se pide el mismo medio y la venta se pagó con varios
     */
    static MedioPago medioDeReembolso(FormaReembolso forma, Venta v) {
        return switch (forma) {
            case EFECTIVO -> MedioPago.EFECTIVO;
            case SALDO_A_FAVOR -> null;
            case MISMO_MEDIO -> {
                Set<MedioPagoVenta> medios = v.getPagos().stream().map(p -> p.getMedio()).collect(Collectors.toSet());
                if (medios.size() != 1 || !medios.iterator().next().entraACaja()) {
                    throw new NegocioException("MEDIO_AMBIGUO",
                            "La venta se pagó con varios medios: devuelva en efectivo o como saldo a favor");
                }
                yield medios.iterator().next().enCaja();
            }
        };
    }

    /**
     * @param d devolución
     * @param v venta original
     * @return respuesta con nombres
     */
    private DevolucionResponse respuesta(DevolucionVenta d, Venta v) {
        Map<Long, VentaLinea> lineas = v.getLineas().stream().collect(Collectors.toMap(VentaLinea::getId, Function.identity()));
        Map<Long, Producto> productos = productoRepository.findAllById(v.getLineas().stream().map(VentaLinea::getProductoId)
                .collect(Collectors.toSet())).stream().collect(Collectors.toMap(Producto::getId, Function.identity()));
        Map<Long, String> unidades = unidadRepository.findAllById(v.getLineas().stream().map(VentaLinea::getUnidadMedidaId)
                .collect(Collectors.toSet())).stream().collect(Collectors.toMap(UnidadMedida::getId, UnidadMedida::getCodigo));
        Map<Long, String> bodegas = new HashMap<>();
        d.getLineas().stream().map(DevolucionVentaLinea::getBodegaId).filter(java.util.Objects::nonNull).distinct()
                .forEach(id -> bodegas.put(id, bodegaRepository.findById(id).map(Bodega::getNombre).orElse(null)));
        Map<Long, String> nombres = usuarioRepository.findAllById(Set.of(d.getUsuarioId(), d.getAutorizadoPorUsuarioId()))
                .stream().collect(Collectors.toMap(Usuario::getId, Usuario::getNombreCompleto));
        List<DevolucionLineaResponse> salida = d.getLineas().stream().map(x -> {
            VentaLinea l = lineas.get(x.getVentaLineaId());
            Producto p = l == null ? null : productos.get(l.getProductoId());
            return new DevolucionLineaResponse(x.getVentaLineaId(), l == null ? null : l.getProductoId(),
                    p == null ? null : p.getNombre(), l == null ? null : unidades.get(l.getUnidadMedidaId()), x.getCantidad(),
                    x.getEstadoProducto(), x.getBodegaId() == null ? null : bodegas.get(x.getBodegaId()), x.getValor());
        }).toList();
        String cliente = clienteRepository.findById(v.getClienteId()).map(Cliente::getNombre).orElse(null);
        return new DevolucionResponse(d.getId(), d.getConsecutivo(), v.getId(), v.getConsecutivo(), cliente, d.getMotivo(),
                d.getFormaReembolso(), d.getMedioReembolso(), d.getTotalReembolso(), nombres.get(d.getUsuarioId()),
                nombres.get(d.getAutorizadoPorUsuarioId()), d.getCreadoEn(), salida);
    }
}
