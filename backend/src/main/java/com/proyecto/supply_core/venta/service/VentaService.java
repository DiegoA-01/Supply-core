package com.proyecto.supply_core.venta.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.proyecto.supply_core.auditoria.enums.AccionAuditoria;
import com.proyecto.supply_core.auditoria.enums.EntidadAuditada;
import com.proyecto.supply_core.auditoria.service.AuditoriaService;
import com.proyecto.supply_core.caja.entity.Caja;
import com.proyecto.supply_core.caja.entity.MovimientoCaja;
import com.proyecto.supply_core.caja.entity.TurnoCaja;
import com.proyecto.supply_core.caja.enums.AccionAutorizada;
import com.proyecto.supply_core.caja.enums.EstadoTurno;
import com.proyecto.supply_core.caja.enums.TipoMovimientoCaja;
import com.proyecto.supply_core.caja.repository.CajaRepository;
import com.proyecto.supply_core.caja.repository.MovimientoCajaRepository;
import com.proyecto.supply_core.caja.repository.TurnoCajaRepository;
import com.proyecto.supply_core.caja.service.AutorizacionService;
import com.proyecto.supply_core.cartera.service.CarteraService;
import com.proyecto.supply_core.catalogo.entity.UnidadMedida;
import com.proyecto.supply_core.catalogo.repository.UnidadMedidaRepository;
import com.proyecto.supply_core.catalogo.service.UnidadMedidaService;
import com.proyecto.supply_core.cliente.entity.Cliente;
import com.proyecto.supply_core.cliente.repository.ClienteRepository;
import com.proyecto.supply_core.cliente.service.ClienteService;
import com.proyecto.supply_core.common.dto.PageResponse;
import com.proyecto.supply_core.common.exception.AccesoDenegadoException;
import com.proyecto.supply_core.common.exception.NegocioException;
import com.proyecto.supply_core.common.exception.NoEncontradoException;
import com.proyecto.supply_core.common.exception.ParametroInvalidoException;
import com.proyecto.supply_core.common.tenant.EmpresaContexto;
import com.proyecto.supply_core.inventario.entity.Producto;
import com.proyecto.supply_core.inventario.repository.ProductoRepository;
import com.proyecto.supply_core.inventario.service.MovimientoInventarioService;
import com.proyecto.supply_core.notificacion.enums.EventoNotificacion;
import com.proyecto.supply_core.notificacion.service.NotificacionService;
import com.proyecto.supply_core.security.context.Roles;
import com.proyecto.supply_core.security.context.SesionActual;
import com.proyecto.supply_core.usuario.entity.Usuario;
import com.proyecto.supply_core.usuario.repository.UsuarioRepository;
import com.proyecto.supply_core.empresa.entity.Empresa;
import com.proyecto.supply_core.empresa.repository.EmpresaRepository;
import com.proyecto.supply_core.venta.dto.LineaCotizada;
import com.proyecto.supply_core.venta.dto.CotizacionVenta;
import com.proyecto.supply_core.venta.dto.CotizarRequest;
import com.proyecto.supply_core.venta.dto.ImpuestoTiquete;
import com.proyecto.supply_core.venta.dto.LineaVentaRequest;
import com.proyecto.supply_core.venta.dto.TiqueteResponse;
import com.proyecto.supply_core.venta.dto.PagoVentaRequest;
import com.proyecto.supply_core.venta.dto.VentaLineaResponse;
import com.proyecto.supply_core.venta.dto.VentaPagoResponse;
import com.proyecto.supply_core.venta.dto.VentaRequest;
import com.proyecto.supply_core.venta.dto.VentaResponse;
import com.proyecto.supply_core.venta.dto.VentaResumen;
import com.proyecto.supply_core.venta.entity.ListaPrecio;
import com.proyecto.supply_core.venta.entity.PedidoVenta;
import com.proyecto.supply_core.venta.entity.PrecioProducto;
import com.proyecto.supply_core.venta.entity.Venta;
import com.proyecto.supply_core.venta.entity.VentaLinea;
import com.proyecto.supply_core.venta.entity.VentaPago;
import com.proyecto.supply_core.venta.enums.EstadoPedidoVenta;
import com.proyecto.supply_core.venta.enums.EstadoVenta;
import com.proyecto.supply_core.venta.enums.MedioPagoVenta;
import com.proyecto.supply_core.venta.repository.DevolucionVentaRepository;
import com.proyecto.supply_core.venta.repository.ListaPrecioRepository;
import com.proyecto.supply_core.venta.repository.PedidoVentaRepository;
import com.proyecto.supply_core.venta.repository.PrecioProductoRepository;
import com.proyecto.supply_core.venta.repository.VentaRepository;

/**
 * La venta (spec 004): cobrar descuenta el stock, registra el dinero en la caja y deja la foto fija de precios,
 * impuestos, descuentos y costos, todo en una sola transacción: o pasa completa o no pasa nada.
 * <ul>
 *   <li>El precio sale de la lista del cliente (o de la General si esa lista no tiene precio); lo que mande la pantalla
 *       como monto no existe: solo manda productos, cantidades, descuentos y pagos.</li>
 *   <li>Un descuento sobre el límite del rol o un precio bajo el mínimo exigen la autorización de un supervisor,
 *       que se consume aquí.</li>
 *   <li>Nunca se vende lo que no hay en la bodega de la caja (decisión D1): si un producto no alcanza, nada se
 *       guarda. El stock se bloquea en orden de id de producto para que dos cajas no se bloqueen entre sí.</li>
 *   <li>La misma venta reenviada con la misma {@code Idempotency-Key} devuelve la ya creada: no se cobra dos veces.</li>
 *   <li>El consecutivo es por caja ({@code C1-000123}); se reserva bloqueando la fila de la caja dentro de la misma
 *       transacción, así un rechazo no deja huecos.</li>
 * </ul>
 */
@Service
public class VentaService {

    /** Tipo de documento con que la venta referencia los movimientos de inventario y de caja. */
    static final String REFERENCIA = "VENTA";
    /** Largo máximo de la clave de idempotencia. */
    static final int MAX_CLAVE = 64;

    private final VentaRepository ventaRepository;
    private final PedidoVentaRepository pedidoRepository;
    private final TurnoCajaRepository turnoRepository;
    private final CajaRepository cajaRepository;
    private final MovimientoCajaRepository movimientoCajaRepository;
    private final ClienteRepository clienteRepository;
    private final ClienteService clienteService;
    private final ListaPrecioRepository listaRepository;
    private final PrecioProductoRepository precioRepository;
    private final ProductoRepository productoRepository;
    private final UnidadMedidaRepository unidadRepository;
    private final UnidadMedidaService unidadMedidaService;
    private final PreciosService preciosService;
    private final MovimientoInventarioService movimientoInventarioService;
    private final AutorizacionService autorizacionService;
    private final LimiteDescuentoService limiteService;
    private final UsuarioRepository usuarioRepository;
    private final AuditoriaService auditoriaService;
    private final JdbcTemplate jdbc;
    private final EmpresaRepository empresaRepository;
    private final DevolucionVentaRepository devolucionRepository;
    private final NotificacionService notificacionService;
    private final CarteraService carteraService;
    private final AlertaCajeroService alertaCajero;

    /**
     * Línea lista para cobrar: el producto, su precio del día y el resultado del cálculo.
     *
     * @param producto    producto
     * @param unidad      unidad vendida
     * @param cantidad    cantidad en la unidad vendida
     * @param cantidadProducto cantidad en la unidad del producto
     * @param precio      precio vigente usado
     * @param descuentoPct descuento pedido
     * @param resultado   montos de la línea
     * @param pasaLimite  {@code true} si el descuento pasa el límite del rol
     * @param bajoMinimo  {@code true} si el precio con descuento queda bajo el mínimo
     */
    record LineaPreparada(Producto producto, UnidadMedida unidad, BigDecimal cantidad, BigDecimal cantidadProducto,
            PrecioProducto precio, BigDecimal descuentoPct, CalculoVenta.ResultadoLinea resultado, boolean pasaLimite,
            boolean bajoMinimo) {
    }

    /**
     * Resultado de confirmar: la venta y si es nueva o un reenvío.
     *
     * @param venta venta
     * @param nueva {@code false} si la clave ya existía y se devolvió la venta anterior
     */
    public record Confirmacion(VentaResponse venta, boolean nueva) {
    }

    /**
     * @param ventaRepository             ventas
     * @param pedidoRepository            pedidos de venta
     * @param turnoRepository             turno abierto del cajero
     * @param cajaRepository              cajas (nombres)
     * @param movimientoCajaRepository    libro de caja
     * @param clienteRepository           clientes
     * @param clienteService              consumidor final
     * @param listaRepository             listas de precios
     * @param precioRepository            precios vigentes
     * @param productoRepository          productos
     * @param unidadRepository            unidades de medida
     * @param unidadMedidaService         conversión de unidades
     * @param preciosService              lista General y unidades válidas para un producto
     * @param movimientoInventarioService salida de inventario por venta
     * @param autorizacionService         autorizaciones del supervisor
     * @param limiteService               límite de descuento por rol
     * @param usuarioRepository           nombres de cajeros, vendedores y supervisores
     * @param auditoriaService            registro de la venta
     * @param jdbc                        consecutivo por caja (misma transacción)
     * @param empresaRepository           datos de la empresa para el tiquete
     * @param devolucionRepository        lo ya devuelto de cada línea
     * @param notificacionService         aviso de venta por debajo del costo (spec 008)
     * @param carteraService              crédito, cupo, mora y saldo a favor (spec 007)
     * @param alertaCajero                alerta de descuentos autorizados por cajero (spec 010)
     */
    public VentaService(VentaRepository ventaRepository, PedidoVentaRepository pedidoRepository,
            TurnoCajaRepository turnoRepository, CajaRepository cajaRepository,
            MovimientoCajaRepository movimientoCajaRepository, ClienteRepository clienteRepository,
            ClienteService clienteService, ListaPrecioRepository listaRepository,
            PrecioProductoRepository precioRepository, ProductoRepository productoRepository,
            UnidadMedidaRepository unidadRepository, UnidadMedidaService unidadMedidaService,
            PreciosService preciosService, MovimientoInventarioService movimientoInventarioService,
            AutorizacionService autorizacionService, LimiteDescuentoService limiteService,
            UsuarioRepository usuarioRepository, AuditoriaService auditoriaService, JdbcTemplate jdbc,
            EmpresaRepository empresaRepository, DevolucionVentaRepository devolucionRepository,
            NotificacionService notificacionService, CarteraService carteraService, AlertaCajeroService alertaCajero) {
        this.ventaRepository = ventaRepository;
        this.pedidoRepository = pedidoRepository;
        this.turnoRepository = turnoRepository;
        this.cajaRepository = cajaRepository;
        this.movimientoCajaRepository = movimientoCajaRepository;
        this.clienteRepository = clienteRepository;
        this.clienteService = clienteService;
        this.listaRepository = listaRepository;
        this.precioRepository = precioRepository;
        this.productoRepository = productoRepository;
        this.unidadRepository = unidadRepository;
        this.unidadMedidaService = unidadMedidaService;
        this.preciosService = preciosService;
        this.movimientoInventarioService = movimientoInventarioService;
        this.autorizacionService = autorizacionService;
        this.limiteService = limiteService;
        this.usuarioRepository = usuarioRepository;
        this.auditoriaService = auditoriaService;
        this.jdbc = jdbc;
        this.empresaRepository = empresaRepository;
        this.devolucionRepository = devolucionRepository;
        this.notificacionService = notificacionService;
        this.carteraService = carteraService;
        this.alertaCajero = alertaCajero;
    }

    /**
     * Confirma una venta del turno abierto de la sesión.
     *
     * @param req   cliente, líneas, pagos y autorizaciones
     * @param clave clave de idempotencia (cabecera {@code Idempotency-Key})
     * @return la venta y si es nueva
     * @throws ParametroInvalidoException si falta la clave o es muy larga
     * @throws NegocioException           sin turno abierto, cliente inactivo, producto sin precio o no vendible,
     *                                    descuento o precio sin autorización, pagos que no cuadran o stock insuficiente
     * @throws NoEncontradoException      si el cliente, el pedido o un producto no son de la empresa
     */
    @Transactional
    public Confirmacion confirmar(VentaRequest req, String clave) {
        String llave = validarClave(clave);
        Long cajero = SesionActual.usuario().id();
        // Primera lectura de la transacción y con bloqueo: ordena los reenvíos del mismo cajero, y como la foto de MySQL
        // se toma después de esperar, la búsqueda de la clave ya ve la venta que confirmó el primer envío. Si el turno se
        // cerró durante la espera no aparece, así que nunca se vende en un turno cerrado.
        TurnoCaja turno = turnoRepository.abiertoDeBloqueado(cajero)
                .orElseThrow(() -> new NegocioException("TURNO_NO_ABIERTO", "Abra su turno de caja antes de vender"));
        Venta anterior = ventaRepository.findByClaveIdempotencia(llave).orElse(null);
        if (anterior != null) {
            if (!anterior.getCajeroUsuarioId().equals(cajero)) {
                throw new NegocioException("CLAVE_REPETIDA", "Esa clave de venta ya la usó otra caja: abra un carrito nuevo");
            }
            if (!firma(req.lineas()).equals(firmaVenta(anterior))) {
                throw new NegocioException("CLAVE_REPETIDA",
                        "Esa clave ya cobró la venta " + anterior.getConsecutivo() + " con otro carrito: revise esa venta");
            }
            return new Confirmacion(respuesta(anterior), false);
        }

        PedidoVenta pedido = null;
        if (req.pedidoVentaId() != null) {
            pedido = pedidoRepository.bloquear(req.pedidoVentaId())
                    .orElseThrow(() -> new NoEncontradoException("Pedido de venta no encontrado"));
            if (pedido.getEstado() != EstadoPedidoVenta.PENDIENTE_COBRO) {
                throw new NegocioException("PEDIDO_NO_PENDIENTE", "El pedido " + pedido.getConsecutivo() + " ya se "
                        + (pedido.getEstado() == EstadoPedidoVenta.COBRADO ? "cobró" : "descartó"));
            }
            // El pedido se cobra con sus mismos productos y cantidades: así no se le acredita a un vendedor otra venta
            if (!firmaSinDescuento(req.lineas()).equals(firmaPedido(pedido))) {
                throw new NegocioException("PEDIDO_DISTINTO", "El carrito no coincide con el pedido " + pedido.getConsecutivo()
                        + ": cárguelo de nuevo o cobre sin el pedido");
            }
        }
        Cliente cliente = cliente(req.clienteId() != null ? req.clienteId() : pedido == null ? null : pedido.getClienteId());
        ListaPrecio lista = listaDe(cliente);
        BigDecimal limite = limiteService.limiteDe(SesionActual.usuario().roles());
        List<LineaPreparada> lineas = preparar(req.lineas(), lista, limite);

        // Descuentos y precios bajo el mínimo: un permiso del supervisor por cada tipo, que se gasta aquí
        Long autorizaDescuento = lineas.stream().anyMatch(LineaPreparada::pasaLimite)
                ? autorizacionService.consumir(req.autorizacionDescuento(), AccionAutorizada.DESCUENTO, turno.getId(), null)
                : null;
        if (autorizaDescuento != null) {
            // El permiso cubre hasta el límite de quien lo dio: un supervisor no autoriza más de lo que él mismo puede dar
            BigDecimal tope = usuarioRepository.findById(autorizaDescuento)
                    .map(u -> limiteService.limiteDe(u.codigosRoles())).orElse(BigDecimal.ZERO);
            lineas.stream().filter(l -> l.descuentoPct().compareTo(tope) > 0).findFirst().ifPresent(l -> {
                throw new NegocioException("DESCUENTO_SOBRE_AUTORIZACION", "El descuento de " + l.producto().getNombre()
                        + " (" + l.descuentoPct().toPlainString() + " %) pasa el máximo de quien autorizó ("
                        + tope.toPlainString() + " %)");
            });
        }
        Long autorizaPrecio = lineas.stream().anyMatch(LineaPreparada::bajoMinimo)
                ? autorizacionService.consumir(req.autorizacionPrecio(), AccionAutorizada.PRECIO_BAJO_MINIMO,
                        turno.getId(), null)
                : null;

        CalculoVenta.Totales totales = CalculoVenta.totales(lineas.stream().map(LineaPreparada::resultado).toList());
        List<CalculoVenta.Pago> pagos = req.pagos().stream().map(p -> new CalculoVenta.Pago(p.medio(), p.monto())).toList();
        boolean soloEfectivo = pagos.stream().allMatch(p -> p.medio() == MedioPagoVenta.EFECTIVO);
        BigDecimal redondeo = CalculoVenta.redondeo(totales.total(), soloEfectivo);
        validarTarjetas(req.pagos());
        CalculoVenta.ResultadoPagos cobro = CalculoVenta.pagos(totales.total().add(redondeo), pagos);
        // Spec 007: el crédito exige cliente identificado, cupo y no estar en mora; el saldo a favor, que alcance
        BigDecimal credito = cobro.porMedio().getOrDefault(MedioPagoVenta.CREDITO, BigDecimal.ZERO);
        BigDecimal saldoFavor = cobro.porMedio().getOrDefault(MedioPagoVenta.SALDO_FAVOR, BigDecimal.ZERO);
        if (credito.signum() > 0) {
            carteraService.validarCredito(cliente, credito);
        }
        if (saldoFavor.signum() > 0) {
            carteraService.validarSaldoFavor(cliente, saldoFavor);
        }

        Caja caja = turno.getCaja();
        Venta v = new Venta();
        v.setConsecutivo(siguienteConsecutivo(caja));
        v.setCajaId(caja.getId());
        v.setTurnoId(turno.getId());
        v.setBodegaId(caja.getBodega().getId());
        v.setClienteId(cliente.getId());
        v.setVendedorUsuarioId(pedido == null ? null : pedido.getVendedorUsuarioId());
        v.setCajeroUsuarioId(cajero);
        v.setListaPrecioId(lista.getId());
        v.setPedidoVentaId(pedido == null ? null : pedido.getId());
        v.setSubtotal(totales.subtotal());
        v.setDescuentoTotal(totales.descuento());
        v.setBaseGravable(totales.base());
        v.setImpuestos(totales.impuestos());
        v.setTotal(totales.total());
        v.setRedondeo(redondeo);
        v.setCambio(cobro.cambio());
        v.setEsCredito(credito.signum() > 0);
        v.setClaveIdempotencia(llave);
        v.setConfirmadaEn(LocalDateTime.now());
        v.setAutorizadaPorUsuarioId(autorizaDescuento != null ? autorizaDescuento : autorizaPrecio);
        for (LineaPreparada l : lineas) {
            v.getLineas().add(linea(v, l, l.pasaLimite() ? autorizaDescuento : l.bajoMinimo() ? autorizaPrecio : null));
        }
        for (PagoVentaRequest p : req.pagos()) {
            v.getPagos().add(pago(v, p, cobro));
        }
        ventaRepository.saveAndFlush(v);

        // Stock: una salida por producto, en orden de id (si alguno no alcanza, la excepción deshace todo)
        Map<Long, BigDecimal> porProducto = new TreeMap<>();
        lineas.forEach(l -> porProducto.merge(l.producto().getId(), l.cantidadProducto(), BigDecimal::add));
        porProducto.forEach((productoId, cantidad) -> movimientoInventarioService.registrarSalidaVenta(productoId,
                v.getBodegaId(), cantidad, REFERENCIA, v.getId()));

        if (credito.signum() > 0) {
            carteraService.crearCuenta(v, cliente, credito);
        }
        if (saldoFavor.signum() > 0) {
            carteraService.usarSaldoFavor(v, cliente, saldoFavor);
        }

        // Caja: un movimiento por medio de pago con lo que queda en ella (el crédito y el saldo a favor no entran)
        cobro.porMedio().forEach((medio, monto) -> {
            if (monto.signum() > 0 && medio.entraACaja()) {
                MovimientoCaja m = new MovimientoCaja();
                m.setTurnoId(turno.getId());
                m.setTipo(TipoMovimientoCaja.VENTA);
                m.setMedioPago(medio.enCaja());
                m.setMonto(monto);
                m.setReferenciaTipo(REFERENCIA);
                m.setReferenciaId(v.getId());
                m.setMotivo("Venta " + v.getConsecutivo());
                m.setUsuarioId(cajero);
                movimientoCajaRepository.save(m);
            }
        });

        if (pedido != null) {
            pedido.setEstado(EstadoPedidoVenta.COBRADO);
            pedido.setVentaId(v.getId());
            pedido.setActualizadoEn(LocalDateTime.now());
        }
        auditoriaService.exito(AccionAuditoria.VENTA_CONFIRMAR, EntidadAuditada.VENTA, v.getId(), v.getConsecutivo()
                + "; total=" + v.totalAPagar().toPlainString() + "; lineas=" + lineas.size() + "; turno=" + turno.getId()
                + (autorizaDescuento == null ? "" : "; descuento autorizado por " + autorizaDescuento)
                + (autorizaPrecio == null ? "" : "; precio bajo mínimo autorizado por " + autorizaPrecio)
                + (pedido == null ? "" : "; pedido=" + pedido.getConsecutivo()));
        avisarBajoCosto(v, lineas);
        if (v.getAutorizadaPorUsuarioId() != null) {
            alertaCajero.revisarDespuesDeConfirmar(cajero, AlertaCajeroService.Tipo.DESCUENTOS);
        }
        return new Confirmacion(respuesta(v), true);
    }

    /**
     * Huella de un carrito pedido: producto, cantidad y descuento de cada línea, en orden, para saber si un reenvío con la
     * misma clave es de verdad la misma venta.
     *
     * @param lineas líneas pedidas
     * @return huella ordenada
     */
    static List<String> firma(List<LineaVentaRequest> lineas) {
        return lineas.stream().map(l -> huella(l.productoId(), l.cantidad(),
                l.descuentoPct() == null ? BigDecimal.ZERO : l.descuentoPct())).sorted().toList();
    }

    /**
     * @param v venta ya guardada
     * @return su huella, comparable con {@link #firma(List)}
     */
    static List<String> firmaVenta(Venta v) {
        return v.getLineas().stream().map(l -> huella(l.getProductoId(), l.getCantidad(),
                l.getDescuentoPct() == null ? BigDecimal.ZERO : l.getDescuentoPct())).sorted().toList();
    }

    /**
     * @param lineas líneas pedidas
     * @return huella sin el descuento (el cajero puede ajustar el descuento de un pedido con su permiso)
     */
    static List<String> firmaSinDescuento(List<LineaVentaRequest> lineas) {
        return lineas.stream().map(l -> huella(l.productoId(), l.cantidad(), null)).sorted().toList();
    }

    /**
     * @param p pedido de venta
     * @return huella de sus líneas sin el descuento
     */
    static List<String> firmaPedido(PedidoVenta p) {
        return p.getLineas().stream().map(l -> huella(l.getProductoId(), l.getCantidad(), null)).sorted().toList();
    }

    /**
     * @param productoId producto
     * @param cantidad   cantidad
     * @param descuento  porcentaje ({@code null} = no cuenta)
     * @return texto que identifica la línea sin depender de la escala de los números
     */
    private static String huella(Long productoId, BigDecimal cantidad, BigDecimal descuento) {
        return productoId + "|" + cantidad.stripTrailingZeros().toPlainString() + "|"
                + (descuento == null ? "" : descuento.signum() == 0 ? "0" : descuento.stripTrailingZeros().toPlainString());
    }

    /**
     * Spec 008: vender por debajo del costo promedio no se impide, pero avisa a los jefes (dueño y supervisor de ventas)
     * con quién vendió, para que hablen con él. El precio neto se compara sin impuesto y en la unidad del producto.
     *
     * @param v      venta confirmada
     * @param lineas líneas calculadas
     */
    private void avisarBajoCosto(Venta v, List<LineaPreparada> lineas) {
        List<String> bajo = new ArrayList<>();
        for (LineaPreparada l : lineas) {
            BigDecimal costo = l.producto().costoVigente();
            if (costo == null || l.cantidadProducto().signum() <= 0) {
                continue;
            }
            BigDecimal neto = l.resultado().base().divide(l.cantidadProducto(), 4, java.math.RoundingMode.HALF_UP);
            if (neto.compareTo(costo) < 0) {
                bajo.add(l.producto().getCodigo() + " a " + neto.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString()
                        + " sin IVA (costo " + costo.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString() + ")");
            }
        }
        if (bajo.isEmpty()) {
            return;
        }
        String cajero = usuarioRepository.findById(v.getCajeroUsuarioId()).map(Usuario::getNombreCompleto).orElse("?");
        String mensaje = "La venta " + v.getConsecutivo() + " de " + cajero + " vendió por debajo del costo: "
                + String.join("; ", bajo) + ". Revísela con quien vendió.";
        notificacionService.notificarRol(Roles.ADMIN_SISTEMA, EventoNotificacion.PRECIO_BAJO_COSTO, mensaje);
        notificacionService.notificarRol(Roles.SUPERVISOR_VENTAS, EventoNotificacion.PRECIO_BAJO_COSTO, mensaje);
    }

    /**
     * Venta ya creada con una clave (para responder un reenvío que chocó con la primera mientras se guardaba).
     *
     * @param clave clave de idempotencia
     * @return la venta
     * @throws NoEncontradoException si no hay venta con esa clave
     */
    @Transactional(readOnly = true)
    public VentaResponse porClave(String clave) {
        Venta v = ventaRepository.findByClaveIdempotencia(validarClave(clave))
                .orElseThrow(() -> new NoEncontradoException("Venta no encontrada"));
        verificarAcceso(v);
        return respuesta(v);
    }

    /**
     * Historial de ventas según quién mira: supervisor, dueño y auditor ven todas; el vendedor, las suyas; el cajero
     * solo busca una venta por su consecutivo (para reimprimirla, anularla o devolverla), nunca la lista: el cierre es
     * ciego y no debe ver cuánto lleva vendido.
     *
     * @param cajaId      caja
     * @param turnoId     turno
     * @param clienteId   cliente
     * @param estado      estado
     * @param consecutivo consecutivo exacto
     * @param desde       desde (incluido)
     * @param hasta       hasta (excluido)
     * @param pageable    página y orden
     * @return ventas
     * @throws AccesoDenegadoException si un cajero busca sin consecutivo
     */
    @Transactional(readOnly = true)
    public PageResponse<VentaResumen> buscar(Long cajaId, Long turnoId, Long clienteId, EstadoVenta estado,
            String consecutivo, LocalDateTime desde, LocalDateTime hasta, Pageable pageable) {
        Set<String> roles = SesionActual.usuario().roles();
        Long yo = SesionActual.usuario().id();
        Long vendedor = null;
        String exacto = consecutivo == null || consecutivo.isBlank() ? null : consecutivo.trim().toUpperCase();
        if (!veTodas(roles)) {
            if (roles.contains(Roles.VENDEDOR)) {
                vendedor = yo;
            } else {
                if (exacto == null) {
                    throw new AccesoDenegadoException("El cajero busca sus ventas por consecutivo");
                }
            }
        }
        var pagina = ventaRepository.buscar(null, vendedor, cajaId, turnoId, clienteId, estado, exacto, desde, hasta,
                pageable);
        List<Venta> ventas = pagina.getContent();
        Map<Long, String> cajas = cajaRepository.findAllById(ventas.stream().map(Venta::getCajaId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(Caja::getId, Caja::getNombre));
        Map<Long, String> clientes = clienteRepository.findAllById(ventas.stream().map(Venta::getClienteId)
                .collect(Collectors.toSet())).stream().collect(Collectors.toMap(Cliente::getId, Cliente::getNombre));
        Set<Long> usuarios = new HashSet<>();
        ventas.forEach(v -> {
            usuarios.add(v.getCajeroUsuarioId());
            usuarios.add(v.getVendedorUsuarioId());
        });
        Map<Long, String> nombres = nombres(usuarios);
        return PageResponse.of(pagina, v -> new VentaResumen(v.getId(), v.getConsecutivo(), v.getEstado(),
                cajas.get(v.getCajaId()), v.getTurnoId(), clientes.get(v.getClienteId()), nombres.get(v.getCajeroUsuarioId()),
                nombres.get(v.getVendedorUsuarioId()), v.totalAPagar(), v.getConfirmadaEn()));
    }

    /**
     * @param id venta
     * @return detalle con líneas y pagos
     * @throws NoEncontradoException si no es de la empresa o la sesión no puede verla
     */
    @Transactional(readOnly = true)
    public VentaResponse obtener(Long id) {
        Venta v = ventaRepository.findById(id).orElseThrow(() -> new NoEncontradoException("Venta no encontrada"));
        verificarAcceso(v);
        return respuesta(v);
    }

    /**
     * Totales del carrito sin guardar nada (spec 005): el mismo cálculo que la venta, así lo que ve el cajero es lo
     * que se cobra. No consume autorizaciones ni toca stock o caja.
     *
     * @param req cliente, líneas y pagos escritos
     * @return líneas con su precio, totales, redondeo, cambio y qué autorizaciones hacen falta
     */
    @Transactional
    public CotizacionVenta cotizar(CotizarRequest req) {
        Cliente cliente = cliente(req.clienteId());
        BigDecimal limite = limiteService.limiteDe(SesionActual.usuario().roles());
        List<LineaPreparada> lineas = preparar(req.lineas(), listaDe(cliente), limite);
        CalculoVenta.Totales t = CalculoVenta.totales(lineas.stream().map(LineaPreparada::resultado).toList());
        List<PagoVentaRequest> escritos = req.pagos() == null ? List.of() : req.pagos();
        boolean soloEfectivo = escritos.stream().allMatch(p -> p.medio() == MedioPagoVenta.EFECTIVO);
        BigDecimal redondeo = CalculoVenta.redondeo(t.total(), soloEfectivo);
        BigDecimal aPagar = t.total().add(redondeo);
        BigDecimal pagado = escritos.stream().map(p -> CalculoVenta.dinero(p.monto())).reduce(BigDecimal.ZERO.setScale(2),
                BigDecimal::add);
        BigDecimal cambio = null;
        String mensaje = null;
        if (!escritos.isEmpty()) {
            try {
                validarTarjetas(escritos);
                cambio = CalculoVenta.pagos(aPagar, escritos.stream()
                        .map(p -> new CalculoVenta.Pago(p.medio(), p.monto())).toList()).cambio();
            } catch (NegocioException e) {
                mensaje = e.getMessage();
            }
        }
        List<LineaCotizada> salida = lineas.stream().map(l -> new LineaCotizada(l.producto().getId(),
                l.producto().getCodigo(), l.producto().getNombre(), l.unidad().getId(), l.unidad().getCodigo(), l.cantidad(),
                l.precio().getPrecio(), l.resultado().precioUnitario(), l.descuentoPct(), l.resultado().descuento(),
                l.producto().getTarifaImpuesto() == null ? BigDecimal.ZERO : l.producto().getTarifaImpuesto().getPorcentaje(),
                l.resultado().total(), l.pasaLimite(), l.bajoMinimo())).toList();
        var credito = cliente.isEsConsumidorFinal() ? null : carteraService.estado(cliente);
        return new CotizacionVenta(salida, t.subtotal(), t.descuento(), t.base(), t.impuestos(), t.total(), redondeo, aPagar,
                pagado, cambio, mensaje, limite, lineas.stream().anyMatch(LineaPreparada::pasaLimite),
                lineas.stream().anyMatch(LineaPreparada::bajoMinimo), credito == null ? null : credito.disponible(),
                credito == null ? BigDecimal.ZERO : credito.saldoFavor(), credito != null && credito.bloqueado());
    }

    /**
     * Datos del tiquete. La primera impresión es el original; las siguientes dicen COPIA. Cada impresión queda en la
     * auditoría ({@code VENTA_IMPRIMIR} o {@code VENTA_REIMPRIMIR}).
     *
     * @param id venta
     * @return tiquete
     * @throws NoEncontradoException si no es de la empresa o la sesión no puede verla
     */
    @Transactional
    public TiqueteResponse tiquete(Long id) {
        Venta v = ventaRepository.findById(id).orElseThrow(() -> new NoEncontradoException("Venta no encontrada"));
        verificarAcceso(v);
        Long empresaId = EmpresaContexto.requerida();
        Integer previas = jdbc.queryForObject("SELECT COUNT(*) FROM auditoria WHERE empresa_id = ? AND entidad = ? "
                + "AND entidad_id = ? AND accion IN (?, ?)", Integer.class, empresaId, EntidadAuditada.VENTA.name(), v.getId(),
                AccionAuditoria.VENTA_IMPRIMIR.name(), AccionAuditoria.VENTA_REIMPRIMIR.name());
        int impresion = (previas == null ? 0 : previas) + 1;
        boolean copia = impresion > 1;
        auditoriaService.exito(copia ? AccionAuditoria.VENTA_REIMPRIMIR : AccionAuditoria.VENTA_IMPRIMIR,
                EntidadAuditada.VENTA, v.getId(), v.getConsecutivo() + (copia ? "; copia " + (impresion - 1) : "; original"));
        Empresa empresa = empresaRepository.findById(empresaId).orElse(null);
        String sede = cajaRepository.conDetalle(v.getCajaId()).map(c -> c.getSede().getNombre()).orElse(null);
        Map<BigDecimal, BigDecimal[]> porTarifa = new TreeMap<>();
        v.getLineas().forEach(l -> {
            BigDecimal[] suma = porTarifa.computeIfAbsent(l.getTarifaImpuestoPct().stripTrailingZeros(),
                    k -> new BigDecimal[] { BigDecimal.ZERO.setScale(2), BigDecimal.ZERO.setScale(2) });
            suma[0] = suma[0].add(l.getBaseGravable());
            suma[1] = suma[1].add(l.getImpuesto());
        });
        List<ImpuestoTiquete> impuestos = porTarifa.entrySet().stream()
                .map(e -> new ImpuestoTiquete(e.getKey(), e.getValue()[0], e.getValue()[1])).toList();
        return new TiqueteResponse(copia, impresion, LocalDateTime.now(), empresa == null ? null : empresa.getRazonSocial(),
                empresa == null ? null : empresa.getNit(), empresa == null ? null : empresa.getDireccionPrincipal(), sede,
                respuesta(v), impuestos);
    }

    // ------------------------------------------------------------------ cálculo

    /**
     * Pone el precio del día a cada línea y la calcula. No guarda nada (la spec 005 lo usa para cotizar).
     *
     * @param pedidas líneas que mandó la pantalla
     * @param lista   lista de precios del cliente
     * @param limite  descuento máximo sin autorización de la sesión
     * @return líneas calculadas
     * @throws NoEncontradoException si un producto o una unidad no son de la empresa
     * @throws NegocioException      si un producto no se vende o no tiene precio en esa unidad
     */
    List<LineaPreparada> preparar(List<LineaVentaRequest> pedidas, ListaPrecio lista, BigDecimal limite) {
        Set<Long> ids = pedidas.stream().map(LineaVentaRequest::productoId).collect(Collectors.toSet());
        Map<Long, Producto> productos = productoRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Producto::getId, Function.identity()));
        Map<String, PrecioProducto> precios = new HashMap<>();
        precioRepository.vigentesDe(lista.getId(), ids).forEach(p -> precios.put(llave(p), p));
        ListaPrecio general = general();
        if (!general.getId().equals(lista.getId())) {
            precioRepository.vigentesDe(general.getId(), ids).forEach(p -> precios.putIfAbsent(llave(p), p));
        }
        List<LineaPreparada> lineas = new ArrayList<>();
        for (LineaVentaRequest l : pedidas) {
            Producto producto = productos.get(l.productoId());
            if (producto == null) {
                throw new NoEncontradoException("Producto no encontrado");
            }
            if (!producto.isActivo() || !producto.isVendible()) {
                throw new NegocioException("PRODUCTO_NO_VENDIBLE", "El producto " + producto.getCodigo() + " no se vende");
            }
            UnidadMedida unidad = preciosService.unidadDelPrecio(producto, l.unidadMedidaId());
            PrecioProducto precio = precios.get(producto.getId() + ":" + unidad.getId());
            if (precio == null) {
                throw new NegocioException("SIN_PRECIO", "El producto " + producto.getCodigo() + " no tiene precio de venta en "
                        + unidad.getCodigo());
            }
            BigDecimal cantidadProducto = unidad.getId().equals(producto.getUnidadMedida().getId()) ? l.cantidad()
                    : unidadMedidaService.convertir(l.cantidad(), unidad.getId(), producto.getUnidadMedida().getId());
            BigDecimal descuento = l.descuentoPct() == null ? BigDecimal.ZERO.setScale(2) : l.descuentoPct().setScale(2);
            CalculoVenta.ResultadoLinea r = CalculoVenta.linea(new CalculoVenta.Linea(precio.getPrecio(), l.cantidad(),
                    descuento, producto.getTarifaImpuesto() == null ? BigDecimal.ZERO : producto.getTarifaImpuesto().getPorcentaje(),
                    producto.isPrecioIncluyeImpuesto()));
            boolean bajoMinimo = precio.getPrecioMinimo() != null && r.precioUnitario().compareTo(precio.getPrecioMinimo()) < 0;
            lineas.add(new LineaPreparada(producto, unidad, l.cantidad(), cantidadProducto, precio, descuento, r,
                    descuento.compareTo(limite) > 0, bajoMinimo));
        }
        return lineas;
    }

    /**
     * @param clienteId cliente ({@code null} = consumidor final)
     * @return cliente activo de la empresa
     * @throws NoEncontradoException si no es de la empresa
     * @throws NegocioException      si está inactivo
     */
    Cliente cliente(Long clienteId) {
        Long id = clienteId != null ? clienteId : clienteService.consumidorFinal().id();
        Cliente c = clienteRepository.findById(id).orElseThrow(() -> new NoEncontradoException("Cliente no encontrado"));
        if (!c.isActivo()) {
            throw new NegocioException("CLIENTE_INACTIVO", "El cliente " + c.getNombre() + " está inactivo");
        }
        return c;
    }

    /**
     * @param cliente cliente
     * @return su lista de precios, o la General si no tiene o está inactiva
     */
    ListaPrecio listaDe(Cliente cliente) {
        ListaPrecio propia = cliente.getListaPrecio();
        return propia != null && propia.isActivo() ? propia : general();
    }

    /**
     * @return lista General de la empresa (se crea si aún no existe)
     */
    private ListaPrecio general() {
        preciosService.asegurarBase();
        return listaRepository.findAllByOrderByEsGeneralDescNombreAsc().stream().filter(ListaPrecio::isEsGeneral)
                .findFirst().orElseThrow(() -> new IllegalStateException("La empresa no tiene lista General"));
    }

    /**
     * @param p precio
     * @return llave producto:unidad
     */
    private static String llave(PrecioProducto p) {
        return p.getProducto().getId() + ":" + p.getUnidadMedidaId();
    }

    // ------------------------------------------------------------------ apoyo

    /**
     * @param clave clave recibida
     * @return la clave sin espacios
     * @throws ParametroInvalidoException si falta o pasa de {@value #MAX_CLAVE} caracteres
     */
    private static String validarClave(String clave) {
        if (clave == null || clave.isBlank()) {
            throw new ParametroInvalidoException("Falta la cabecera Idempotency-Key con la clave de la venta");
        }
        String c = clave.trim();
        if (c.length() > MAX_CLAVE) {
            throw new ParametroInvalidoException("La clave Idempotency-Key admite hasta " + MAX_CLAVE + " caracteres");
        }
        return c;
    }

    /**
     * @param pagos pagos recibidos
     * @throws NegocioException si una tarjeta llega sin sus últimos 4 dígitos o un medio que no es tarjeta los trae
     */
    private static void validarTarjetas(List<PagoVentaRequest> pagos) {
        if (pagos.stream().filter(p -> p.medio() == MedioPagoVenta.EFECTIVO).count() > 1) {
            throw new NegocioException("PAGO_REPETIDO", "Registre el efectivo en un solo pago");
        }
        for (PagoVentaRequest p : pagos) {
            if (p.medio().esTarjeta() && (p.ultimos4() == null || p.referencia() == null || p.referencia().isBlank())) {
                throw new NegocioException("TARJETA_SIN_DATOS",
                        "De la tarjeta escriba los últimos 4 dígitos y el número de autorización del datáfono");
            }
            if (!p.medio().esTarjeta() && p.ultimos4() != null) {
                throw new NegocioException("TARJETA_SIN_DATOS", "Solo las tarjetas llevan los últimos 4 dígitos");
            }
        }
    }

    /**
     * Reserva el siguiente número de la caja. El UPDATE bloquea la fila hasta que termina la transacción: si la venta
     * falla, el número vuelve y no queda hueco.
     *
     * @param caja caja del turno
     * @return consecutivo con el prefijo de la caja
     */
    private String siguienteConsecutivo(Caja caja) {
        Long empresa = EmpresaContexto.requerida();
        jdbc.update("UPDATE caja SET ultimo_consecutivo = ultimo_consecutivo + 1 WHERE id = ? AND empresa_id = ?",
                caja.getId(), empresa);
        Long numero = jdbc.queryForObject("SELECT ultimo_consecutivo FROM caja WHERE id = ? AND empresa_id = ?",
                Long.class, caja.getId(), empresa);
        return formatear(caja.getPrefijoConsecutivo(), numero);
    }

    /**
     * @param prefijo prefijo de la caja
     * @param numero  número
     * @return {@code PREFIJO-000123}
     */
    static String formatear(String prefijo, long numero) {
        return prefijo + "-" + String.format("%06d", numero);
    }

    /**
     * @param v             venta
     * @param l             línea calculada
     * @param autorizadoPor supervisor que autorizó esta línea
     * @return línea con su foto fija
     */
    private static VentaLinea linea(Venta v, LineaPreparada l, Long autorizadoPor) {
        VentaLinea x = new VentaLinea();
        x.setVenta(v);
        x.setProductoId(l.producto().getId());
        x.setUnidadMedidaId(l.unidad().getId());
        x.setCantidad(l.cantidad());
        x.setCantidadUnidadProducto(l.cantidadProducto());
        x.setPrecioLista(l.precio().getPrecio());
        x.setPrecioUnitario(l.resultado().precioUnitario());
        x.setDescuentoPct(l.descuentoPct());
        x.setDescuentoValor(l.resultado().descuento());
        x.setTarifaImpuestoPct(l.producto().getTarifaImpuesto() == null ? BigDecimal.ZERO
                : l.producto().getTarifaImpuesto().getPorcentaje());
        x.setPrecioIncluyeImpuesto(l.producto().isPrecioIncluyeImpuesto());
        x.setBaseGravable(l.resultado().base());
        x.setImpuesto(l.resultado().impuesto());
        x.setTotalLinea(l.resultado().total());
        x.setCostoUnitario(l.producto().costoVigente());
        x.setAutorizadoPorUsuarioId(autorizadoPor);
        return x;
    }

    /**
     * @param v     venta
     * @param p     pago recibido
     * @param cobro cálculo de los pagos
     * @return pago; en efectivo, {@code monto} es lo que queda en la caja y {@code recibido} lo que entregó el cliente
     */
    private static VentaPago pago(Venta v, PagoVentaRequest p, CalculoVenta.ResultadoPagos cobro) {
        VentaPago x = new VentaPago();
        x.setVenta(v);
        x.setMedio(p.medio());
        BigDecimal monto = CalculoVenta.dinero(p.monto());
        if (p.medio() == MedioPagoVenta.EFECTIVO) {
            x.setRecibido(monto);
            x.setMonto(cobro.porMedio().get(MedioPagoVenta.EFECTIVO));
        } else {
            x.setMonto(monto);
        }
        x.setReferencia(p.referencia() == null || p.referencia().isBlank() ? null : p.referencia().trim());
        x.setUltimos4(p.ultimos4());
        return x;
    }

    /**
     * @param roles roles de la sesión
     * @return {@code true} si ve todas las ventas
     */
    private static boolean veTodas(Set<String> roles) {
        return roles.contains(Roles.SUPERVISOR_VENTAS) || roles.contains(Roles.ADMIN_SISTEMA) || roles.contains(Roles.AUDITOR);
    }

    /**
     * @param v venta
     * @throws NoEncontradoException si la sesión no puede verla (no se dice que existe): el vendedor solo ve las suyas
     */
    private static void verificarAcceso(Venta v) {
        Set<String> roles = SesionActual.usuario().roles();
        Long yo = SesionActual.usuario().id();
        if (veTodas(roles) || roles.contains(Roles.CAJERO) || yo.equals(v.getCajeroUsuarioId())
                || (roles.contains(Roles.VENDEDOR) && yo.equals(v.getVendedorUsuarioId()))) {
            return;
        }
        throw new NoEncontradoException("Venta no encontrada");
    }

    /**
     * @param ids usuarios ({@code null} se ignora)
     * @return nombre por id
     */
    private Map<Long, String> nombres(Set<Long> ids) {
        ids.remove(null);
        return usuarioRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Usuario::getId, Usuario::getNombreCompleto));
    }

    /**
     * @param v venta
     * @return detalle con nombres, líneas y pagos
     */
    VentaResponse respuesta(Venta v) {
        Set<Long> ids = new HashSet<>();
        ids.add(v.getCajeroUsuarioId());
        ids.add(v.getVendedorUsuarioId());
        ids.add(v.getAutorizadaPorUsuarioId());
        v.getLineas().forEach(l -> ids.add(l.getAutorizadoPorUsuarioId()));
        Map<Long, String> nombres = nombres(ids);
        Map<Long, Producto> productos = productoRepository.findAllById(v.getLineas().stream().map(VentaLinea::getProductoId)
                .collect(Collectors.toSet())).stream().collect(Collectors.toMap(Producto::getId, Function.identity()));
        Map<Long, String> unidades = unidadRepository.findAllById(v.getLineas().stream().map(VentaLinea::getUnidadMedidaId)
                .collect(Collectors.toSet())).stream().collect(Collectors.toMap(UnidadMedida::getId, UnidadMedida::getCodigo));
        Cliente cliente = clienteRepository.findById(v.getClienteId()).orElse(null);
        String caja = cajaRepository.findById(v.getCajaId()).map(Caja::getNombre).orElse(null);
        String lista = listaRepository.findById(v.getListaPrecioId()).map(ListaPrecio::getNombre).orElse(null);
        Map<Long, DevolucionVentaRepository.Devuelto> devuelto = devolucionRepository.devueltoPorLinea(v.getId()).stream()
                .collect(Collectors.toMap(DevolucionVentaRepository.Devuelto::ventaLineaId, Function.identity()));
        BigDecimal totalDevuelto = devuelto.values().stream().map(DevolucionVentaRepository.Devuelto::valor)
                .reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
        boolean turnoAbierto = turnoRepository.findById(v.getTurnoId()).map(t -> t.getEstado() == EstadoTurno.ABIERTO)
                .orElse(false);
        List<VentaLineaResponse> lineas = v.getLineas().stream().map(l -> {
            Producto p = productos.get(l.getProductoId());
            return new VentaLineaResponse(l.getProductoId(), p == null ? null : p.getCodigo(), p == null ? null : p.getNombre(),
                    unidades.get(l.getUnidadMedidaId()), l.getCantidad(), l.getPrecioLista(), l.getPrecioUnitario(),
                    l.getDescuentoPct(), l.getDescuentoValor(), l.getTarifaImpuestoPct(), l.getBaseGravable(),
                    l.getImpuesto(), l.getTotalLinea(), nombres.get(l.getAutorizadoPorUsuarioId()), l.getId(),
                    devuelto.containsKey(l.getId()) ? devuelto.get(l.getId()).cantidad() : BigDecimal.ZERO);
        }).toList();
        List<VentaPagoResponse> pagos = v.getPagos().stream().map(p -> new VentaPagoResponse(p.getMedio(), p.getMonto(),
                p.getRecibido(), p.getReferencia(), p.getUltimos4())).toList();
        return new VentaResponse(v.getId(), v.getConsecutivo(), v.getEstado(), v.getCajaId(), caja, v.getTurnoId(),
                v.getClienteId(), cliente == null ? null : cliente.getNombre(),
                cliente == null ? null : cliente.getTipoDocumento() + " " + cliente.getNumeroDocumento(),
                nombres.get(v.getCajeroUsuarioId()), nombres.get(v.getVendedorUsuarioId()), lista, v.getPedidoVentaId(),
                v.getSubtotal(), v.getDescuentoTotal(), v.getBaseGravable(), v.getImpuestos(), v.getTotal(), v.getRedondeo(),
                v.totalAPagar(), v.getCambio(), v.getConfirmadaEn(), nombres.get(v.getAutorizadaPorUsuarioId()),
                v.getAnuladaEn(), v.getMotivoAnulacion(), lineas, pagos, turnoAbierto, totalDevuelto);
    }
}
