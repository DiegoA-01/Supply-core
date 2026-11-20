package com.proyecto.supply_core.cartera.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.proyecto.supply_core.auditoria.enums.AccionAuditoria;
import com.proyecto.supply_core.auditoria.enums.EntidadAuditada;
import com.proyecto.supply_core.auditoria.service.AuditoriaService;
import com.proyecto.supply_core.cartera.dto.AbonoResponse;
import com.proyecto.supply_core.cartera.dto.ClienteCartera;
import com.proyecto.supply_core.cartera.dto.CuentaResponse;
import com.proyecto.supply_core.cartera.dto.DetalleCartera;
import com.proyecto.supply_core.cartera.dto.ExcepcionCreditoRequest;
import com.proyecto.supply_core.cartera.dto.ResumenCartera;
import com.proyecto.supply_core.cartera.entity.CuentaPorCobrar;
import com.proyecto.supply_core.cartera.enums.EstadoCuenta;
import com.proyecto.supply_core.cartera.repository.AbonoClienteRepository;
import com.proyecto.supply_core.cartera.repository.CuentaPorCobrarRepository;
import com.proyecto.supply_core.cliente.entity.Cliente;
import com.proyecto.supply_core.cliente.repository.ClienteRepository;
import com.proyecto.supply_core.common.exception.NegocioException;
import com.proyecto.supply_core.common.exception.NoEncontradoException;
import com.proyecto.supply_core.common.tenant.EmpresaContexto;
import com.proyecto.supply_core.plataforma.service.AccesoEmpresaService;
import com.proyecto.supply_core.security.context.Modulos;
import com.proyecto.supply_core.security.context.SesionActual;
import com.proyecto.supply_core.usuario.entity.Usuario;
import com.proyecto.supply_core.usuario.repository.UsuarioRepository;
import com.proyecto.supply_core.venta.entity.SaldoFavorCliente;
import com.proyecto.supply_core.venta.entity.Venta;
import com.proyecto.supply_core.venta.repository.SaldoFavorClienteRepository;
import com.proyecto.supply_core.venta.repository.VentaRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;

/**
 * Crédito y cartera (spec 007).
 * <ul>
 *   <li>Solo se vende a crédito a un cliente identificado (nunca al consumidor final) y sin pasar su cupo
 *       disponible (cupo − saldo). El cliente se bloquea mientras se revisa, así dos cajas no pasan juntas su cupo.</li>
 *   <li>Un cliente con alguna cuenta vencida no compra a crédito: se avisa a los encargados. Ellos pueden darle una
 *       excepción hasta una fecha y con motivo (decisión de la usuaria); la excepción no salta el cupo.</li>
 *   <li>Cada venta a crédito crea su cuenta por cobrar con vencimiento según el plazo del cliente.</li>
 *   <li>El saldo a favor (de devoluciones, spec 006) se puede gastar como medio de pago.</li>
 * </ul>
 */
@Service
public class CarteraService {

    @PersistenceContext
    private EntityManager em;

    private final CuentaPorCobrarRepository cuentaRepository;
    private final AbonoClienteRepository abonoRepository;
    private final ClienteRepository clienteRepository;
    private final SaldoFavorClienteRepository saldoRepository;
    private final VentaRepository ventaRepository;
    private final UsuarioRepository usuarioRepository;
    private final AccesoEmpresaService accesoEmpresa;
    private final AvisoCreditoService avisoCredito;
    private final AuditoriaService auditoriaService;
    private final AbonoService abonoService;

    /**
     * @param cuentaRepository  cuentas por cobrar
     * @param abonoRepository   abonos
     * @param clienteRepository clientes
     * @param saldoRepository   saldo a favor
     * @param ventaRepository   ventas (consecutivos)
     * @param usuarioRepository nombres
     * @param accesoEmpresa     módulo de cartera
     * @param avisoCredito      aviso de crédito bloqueado (en su propia transacción)
     * @param auditoriaService  registro de excepciones
     * @param abonoService      recibos de abonos
     */
    public CarteraService(CuentaPorCobrarRepository cuentaRepository, AbonoClienteRepository abonoRepository,
            ClienteRepository clienteRepository, SaldoFavorClienteRepository saldoRepository,
            VentaRepository ventaRepository, UsuarioRepository usuarioRepository, AccesoEmpresaService accesoEmpresa,
            AvisoCreditoService avisoCredito, AuditoriaService auditoriaService, AbonoService abonoService) {
        this.cuentaRepository = cuentaRepository;
        this.abonoRepository = abonoRepository;
        this.clienteRepository = clienteRepository;
        this.saldoRepository = saldoRepository;
        this.ventaRepository = ventaRepository;
        this.usuarioRepository = usuarioRepository;
        this.accesoEmpresa = accesoEmpresa;
        this.avisoCredito = avisoCredito;
        this.auditoriaService = auditoriaService;
        this.abonoService = abonoService;
    }

    // ------------------------------------------------------------------ venta

    /**
     * Revisa que el cliente pueda comprar a crédito por ese valor. Bloquea al cliente hasta el fin de la venta.
     *
     * @param cliente cliente de la venta
     * @param monto   valor a crédito
     * @throws NegocioException {@code CREDITO_SIN_CLIENTE}, {@code CUPO_INSUFICIENTE} o {@code CLIENTE_EN_MORA}
     */
    @Transactional
    public void validarCredito(Cliente cliente, BigDecimal monto) {
        accesoEmpresa.exigirModulo(EmpresaContexto.requerida(), Modulos.CARTERA);
        if (cliente.isEsConsumidorFinal()) {
            throw new NegocioException("CREDITO_SIN_CLIENTE", "Para vender a crédito registre o elija al cliente");
        }
        bloquear(cliente);
        Long empresa = EmpresaContexto.requerida();
        LocalDate hoy = LocalDate.now();
        if (cuentaRepository.vencidasBloqueado(empresa, cliente.getId(), hoy) > 0 && !excepcionVigente(cliente, hoy)) {
            avisoCredito.avisarBloqueo("Se intentó vender a crédito a " + cliente.getNombre() + " (" + cliente.getNumeroDocumento()
                    + ") y tiene cuentas vencidas. Si debe seguir comprando a crédito, habilítelo en Cartera.");
            throw new NegocioException("CLIENTE_EN_MORA", cliente.getNombre()
                    + " tiene cuentas vencidas: cobre de contado o pida que lo habiliten en Cartera");
        }
        BigDecimal disponible = cliente.getCupoCredito().subtract(cuentaRepository.saldoBloqueado(empresa, cliente.getId()));
        if (monto.compareTo(disponible) > 0) {
            throw new NegocioException("CUPO_INSUFICIENTE", cliente.getNombre() + " tiene disponible "
                    + disponible.max(BigDecimal.ZERO).toPlainString() + " de cupo y la venta a crédito es de " + monto.toPlainString());
        }
    }

    /**
     * Revisa que el cliente tenga saldo a favor para pagar ese valor. Bloquea al cliente hasta el fin de la venta.
     *
     * @param cliente cliente
     * @param monto   valor a pagar con saldo a favor
     * @throws NegocioException {@code SALDO_FAVOR_INSUFICIENTE}
     */
    @Transactional
    public void validarSaldoFavor(Cliente cliente, BigDecimal monto) {
        bloquear(cliente);
        BigDecimal saldo = saldoRepository.saldoBloqueado(EmpresaContexto.requerida(), cliente.getId());
        if (cliente.isEsConsumidorFinal() || monto.compareTo(saldo) > 0) {
            throw new NegocioException("SALDO_FAVOR_INSUFICIENTE", "El cliente tiene " + saldo.toPlainString()
                    + " de saldo a favor");
        }
    }

    /**
     * Crea la cuenta por cobrar de una venta a crédito.
     *
     * @param v       venta ya guardada
     * @param cliente cliente
     * @param monto   valor a crédito
     */
    @Transactional
    public void crearCuenta(Venta v, Cliente cliente, BigDecimal monto) {
        CuentaPorCobrar c = new CuentaPorCobrar();
        c.setVentaId(v.getId());
        c.setClienteId(cliente.getId());
        c.setValor(monto);
        c.setSaldo(monto);
        c.setVenceEn(LocalDate.now().plusDays(Math.max(0, cliente.getPlazoDias())));
        cuentaRepository.save(c);
    }

    /**
     * Registra el uso del saldo a favor en una venta (movimiento negativo).
     *
     * @param v       venta ya guardada
     * @param cliente cliente
     * @param monto   valor usado
     */
    @Transactional
    public void usarSaldoFavor(Venta v, Cliente cliente, BigDecimal monto) {
        SaldoFavorCliente s = new SaldoFavorCliente();
        s.setClienteId(cliente.getId());
        s.setVentaId(v.getId());
        s.setMonto(monto.negate());
        s.setMotivo("Pago de la venta " + v.getConsecutivo());
        saldoRepository.save(s);
    }

    /**
     * Al anular una venta (spec 006): anula su cuenta si no tiene abonos y devuelve el saldo a favor que usó.
     *
     * @param v venta que se anula
     * @throws NegocioException {@code CUENTA_CON_ABONOS} si su cuenta ya recibió abonos
     */
    @Transactional
    public void revertirVenta(Venta v) {
        cuentaRepository.deVentaBloqueada(v.getId()).ifPresent(c -> {
            if (c.getSaldo().compareTo(c.getValor()) != 0) {
                throw new NegocioException("CUENTA_CON_ABONOS",
                        "La venta a crédito ya recibió abonos: registre una devolución en vez de anularla");
            }
            c.setEstado(EstadoCuenta.ANULADA);
            c.setSaldo(BigDecimal.ZERO.setScale(2));
        });
        BigDecimal usado = saldoUsadoEn(v);
        if (usado.signum() > 0) {
            SaldoFavorCliente s = new SaldoFavorCliente();
            s.setClienteId(v.getClienteId());
            s.setVentaId(v.getId());
            s.setMonto(usado);
            s.setMotivo("Anulación de la venta " + v.getConsecutivo());
            saldoRepository.save(s);
        }
    }

    /**
     * Devolución de una venta que se pagó (todo o en parte) a crédito: el valor devuelto rebaja primero la deuda abierta
     * de esa venta. Solo lo que sobre se reembolsa por la forma que se pidió; así nunca sale dinero de la caja ni queda
     * saldo a favor mientras el cliente siga debiendo esa misma venta.
     *
     * @param v     venta original
     * @param total valor devuelto
     * @return lo que se aplicó a la deuda (0 si la venta no tiene cuenta abierta)
     */
    @Transactional
    public BigDecimal aplicarDevolucionADeuda(Venta v, BigDecimal total) {
        CuentaPorCobrar c = cuentaRepository.deVentaBloqueada(v.getId()).orElse(null);
        if (c == null || c.getEstado() != EstadoCuenta.ABIERTA || total.signum() <= 0) {
            return BigDecimal.ZERO.setScale(2);
        }
        BigDecimal aplica = total.min(c.getSaldo());
        c.setSaldo(c.getSaldo().subtract(aplica));
        if (c.getSaldo().signum() == 0) {
            c.setEstado(EstadoCuenta.PAGADA);
        }
        return aplica;
    }

    /**
     * Estado de crédito de un cliente para mostrarlo en el POS (sin bloquear nada).
     *
     * @param cliente cliente
     * @return cartera del cliente
     */
    @Transactional(readOnly = true)
    public ClienteCartera estado(Cliente cliente) {
        List<CuentaPorCobrar> abiertas = cuentaRepository.findByClienteIdOrderByIdDesc(cliente.getId()).stream()
                .filter(c -> c.getEstado() == EstadoCuenta.ABIERTA).toList();
        return fila(cliente, abiertas, saldoRepository.saldoDe(cliente.getId()), Map.of(), LocalDate.now());
    }

    // ------------------------------------------------------------------ consultas

    /**
     * @return cartera de la empresa: clientes con cupo o con saldo, primero los que más deben
     */
    @Transactional(readOnly = true)
    public ResumenCartera resumen() {
        LocalDate hoy = LocalDate.now();
        Map<Long, List<CuentaPorCobrar>> abiertas = cuentaRepository.findByEstado(EstadoCuenta.ABIERTA).stream()
                .collect(Collectors.groupingBy(CuentaPorCobrar::getClienteId));
        List<Cliente> clientes = clienteRepository.findAll().stream()
                .filter(c -> !c.isEsConsumidorFinal()
                        && (c.getCupoCredito().signum() > 0 || abiertas.containsKey(c.getId()) || c.getCreditoExcepcionHasta() != null))
                .toList();
        Map<Long, String> nombres = nombres(clientes.stream().map(Cliente::getCreditoExcepcionPorUsuarioId).collect(Collectors.toSet()));
        List<ClienteCartera> filas = clientes.stream()
                .map(c -> fila(c, abiertas.getOrDefault(c.getId(), List.of()), saldoRepository.saldoDe(c.getId()), nombres, hoy))
                .sorted(Comparator.comparing(ClienteCartera::saldo).reversed().thenComparing(ClienteCartera::nombre))
                .toList();
        CalculoCartera.Edades total = CalculoCartera.edades(abiertas.values().stream().flatMap(List::stream)
                .map(c -> new CalculoCartera.Cuenta(c.getSaldo(), c.getVenceEn())).toList(), hoy);
        return new ResumenCartera(filas, total.total(), total.alDia(), total.d1a30(), total.d31a60(), total.d61a90(),
                total.mas90(), (int) filas.stream().filter(ClienteCartera::enMora).count());
    }

    /**
     * @param clienteId cliente
     * @return su cartera con cuentas y abonos
     * @throws NoEncontradoException si no es de la empresa
     */
    @Transactional(readOnly = true)
    public DetalleCartera detalle(Long clienteId) {
        Cliente c = clienteRepository.findById(clienteId).orElseThrow(() -> new NoEncontradoException("Cliente no encontrado"));
        LocalDate hoy = LocalDate.now();
        List<CuentaPorCobrar> cuentas = cuentaRepository.findByClienteIdOrderByIdDesc(clienteId);
        Map<Long, String> ventas = ventaRepository.findAllById(cuentas.stream().map(CuentaPorCobrar::getVentaId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(Venta::getId, Venta::getConsecutivo));
        List<CuentaResponse> salida = cuentas.stream().map(x -> new CuentaResponse(x.getId(), x.getVentaId(), ventas.get(x.getVentaId()),
                x.getValor(), x.getSaldo(), x.getVenceEn(), x.getEstado(),
                x.getEstado() == EstadoCuenta.ABIERTA ? CalculoCartera.diasVencida(x.getVenceEn(), hoy) : 0)).toList();
        List<AbonoResponse> abonos = abonoRepository.findByClienteIdOrderByIdDesc(clienteId).stream().map(abonoService::respuesta).toList();
        Map<Long, String> nombres = nombres(new java.util.HashSet<>(java.util.Collections.singleton(c.getCreditoExcepcionPorUsuarioId())));
        return new DetalleCartera(fila(c, cuentas.stream().filter(x -> x.getEstado() == EstadoCuenta.ABIERTA).toList(),
                saldoRepository.saldoDe(clienteId), nombres, hoy), salida, abonos);
    }

    /**
     * Habilita a un cliente en mora para comprar a crédito hasta una fecha (decisión de la usuaria).
     *
     * @param clienteId cliente
     * @param req       fecha y motivo
     * @return cartera del cliente
     * @throws NegocioException si es el consumidor final
     */
    @Transactional
    public ClienteCartera fijarExcepcion(Long clienteId, ExcepcionCreditoRequest req) {
        Cliente c = clienteRepository.findById(clienteId).orElseThrow(() -> new NoEncontradoException("Cliente no encontrado"));
        if (c.isEsConsumidorFinal()) {
            throw new NegocioException("CREDITO_SIN_CLIENTE", "El consumidor final no compra a crédito");
        }
        c.setCreditoExcepcionHasta(req.hasta());
        c.setCreditoExcepcionMotivo(req.motivo().trim());
        c.setCreditoExcepcionPorUsuarioId(SesionActual.usuario().id());
        auditoriaService.exito(AccionAuditoria.CREDITO_EXCEPCION, EntidadAuditada.CLIENTE, c.getId(),
                "habilitado a crédito hasta " + req.hasta() + "; motivo=" + c.getCreditoExcepcionMotivo());
        return estadoConNombres(c);
    }

    /**
     * Quita la excepción de crédito de un cliente.
     *
     * @param clienteId cliente
     * @return cartera del cliente
     */
    @Transactional
    public ClienteCartera quitarExcepcion(Long clienteId) {
        Cliente c = clienteRepository.findById(clienteId).orElseThrow(() -> new NoEncontradoException("Cliente no encontrado"));
        c.setCreditoExcepcionHasta(null);
        c.setCreditoExcepcionMotivo(null);
        c.setCreditoExcepcionPorUsuarioId(null);
        auditoriaService.exito(AccionAuditoria.CREDITO_EXCEPCION, EntidadAuditada.CLIENTE, c.getId(), "excepción de crédito retirada");
        return estadoConNombres(c);
    }

    // ------------------------------------------------------------------ apoyo

    /**
     * @param c cliente
     * @return su cartera con el nombre de quien dio la excepción
     */
    private ClienteCartera estadoConNombres(Cliente c) {
        List<CuentaPorCobrar> abiertas = cuentaRepository.findByClienteIdOrderByIdDesc(c.getId()).stream()
                .filter(x -> x.getEstado() == EstadoCuenta.ABIERTA).toList();
        Map<Long, String> nombres = nombres(new java.util.HashSet<>(java.util.Collections.singleton(c.getCreditoExcepcionPorUsuarioId())));
        return fila(c, abiertas, saldoRepository.saldoDe(c.getId()), nombres, LocalDate.now());
    }

    /**
     * Relee al cliente con bloqueo (guarda antes lo pendiente): dos ventas del mismo cliente van en fila.
     *
     * @param cliente cliente
     */
    private void bloquear(Cliente cliente) {
        em.flush();
        em.refresh(cliente, LockModeType.PESSIMISTIC_WRITE);
    }

    /**
     * @param c   cliente
     * @param hoy fecha de hoy
     * @return {@code true} si tiene una excepción de crédito que cubre hoy
     */
    static boolean excepcionVigente(Cliente c, LocalDate hoy) {
        return c.getCreditoExcepcionHasta() != null && !c.getCreditoExcepcionHasta().isBefore(hoy);
    }

    /**
     * @param v venta
     * @return saldo a favor que usó la venta (positivo)
     */
    private BigDecimal saldoUsadoEn(Venta v) {
        return v.getPagos().stream().filter(p -> p.getMedio() == com.proyecto.supply_core.venta.enums.MedioPagoVenta.SALDO_FAVOR)
                .map(p -> p.getMonto()).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * @param c          cliente
     * @param abiertas   sus cuentas abiertas
     * @param saldoFavor su saldo a favor
     * @param nombres    nombres de usuarios por id
     * @param hoy        fecha de hoy
     * @return renglón de cartera
     */
    private static ClienteCartera fila(Cliente c, List<CuentaPorCobrar> abiertas, BigDecimal saldoFavor,
            Map<Long, String> nombres, LocalDate hoy) {
        CalculoCartera.Edades e = CalculoCartera.edades(abiertas.stream()
                .map(x -> new CalculoCartera.Cuenta(x.getSaldo(), x.getVenceEn())).toList(), hoy);
        boolean enMora = e.vencido().signum() > 0;
        return new ClienteCartera(c.getId(), c.getNombre(), c.getTipoDocumento() + " " + c.getNumeroDocumento(),
                c.getCupoCredito(), c.getPlazoDias(), e.total(), c.getCupoCredito().subtract(e.total()).max(BigDecimal.ZERO),
                saldoFavor, e.alDia(), e.vencido(), e.d1a30(), e.d31a60(), e.d61a90(), e.mas90(), enMora,
                enMora && !excepcionVigente(c, hoy), c.getCreditoExcepcionHasta(), c.getCreditoExcepcionMotivo(),
                c.getCreditoExcepcionPorUsuarioId() == null ? null : nombres.get(c.getCreditoExcepcionPorUsuarioId()));
    }

    /**
     * @param ids usuarios ({@code null} se ignora)
     * @return nombre por id
     */
    private Map<Long, String> nombres(Set<Long> ids) {
        Set<Long> limpios = new java.util.HashSet<>(ids);
        limpios.remove(null);
        if (limpios.isEmpty()) {
            return new HashMap<>();
        }
        return usuarioRepository.findAllById(limpios).stream().collect(Collectors.toMap(Usuario::getId, Usuario::getNombreCompleto));
    }
}
