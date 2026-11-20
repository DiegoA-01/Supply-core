package com.proyecto.supply_core.cartera.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.proyecto.supply_core.auditoria.enums.AccionAuditoria;
import com.proyecto.supply_core.auditoria.enums.EntidadAuditada;
import com.proyecto.supply_core.auditoria.service.AuditoriaService;
import com.proyecto.supply_core.caja.entity.MovimientoCaja;
import com.proyecto.supply_core.caja.entity.TurnoCaja;
import com.proyecto.supply_core.caja.enums.MedioPago;
import com.proyecto.supply_core.caja.enums.TipoMovimientoCaja;
import com.proyecto.supply_core.caja.repository.MovimientoCajaRepository;
import com.proyecto.supply_core.caja.repository.TurnoCajaRepository;
import com.proyecto.supply_core.cartera.dto.AbonoRequest;
import com.proyecto.supply_core.cartera.dto.AbonoResponse;
import com.proyecto.supply_core.cartera.entity.AbonoAplicacion;
import com.proyecto.supply_core.cartera.entity.AbonoCliente;
import com.proyecto.supply_core.cartera.entity.CuentaPorCobrar;
import com.proyecto.supply_core.cartera.enums.EstadoCuenta;
import com.proyecto.supply_core.cartera.repository.AbonoClienteRepository;
import com.proyecto.supply_core.cartera.repository.CuentaPorCobrarRepository;
import com.proyecto.supply_core.cliente.entity.Cliente;
import com.proyecto.supply_core.cliente.repository.ClienteRepository;
import com.proyecto.supply_core.common.exception.NegocioException;
import com.proyecto.supply_core.common.exception.NoEncontradoException;
import com.proyecto.supply_core.common.util.ConsecutivoGenerator;
import com.proyecto.supply_core.security.context.SesionActual;
import com.proyecto.supply_core.usuario.entity.Usuario;
import com.proyecto.supply_core.usuario.repository.UsuarioRepository;
import com.proyecto.supply_core.venta.entity.Venta;
import com.proyecto.supply_core.venta.repository.VentaRepository;

/**
 * Abonos de cartera (spec 007): entran a la caja del turno ({@code ABONO_CARTERA}), se aplican a las cuentas más
 * antiguas primero y dejan recibo. El esperado del cierre de caja ya los suma.
 */
@Service
public class AbonoService {

    /** Tipo de documento con que el abono referencia el libro de caja. */
    static final String REFERENCIA = "ABONO_CLIENTE";

    private final AbonoClienteRepository abonoRepository;
    private final CuentaPorCobrarRepository cuentaRepository;
    private final ClienteRepository clienteRepository;
    private final TurnoCajaRepository turnoRepository;
    private final MovimientoCajaRepository movimientoCajaRepository;
    private final VentaRepository ventaRepository;
    private final UsuarioRepository usuarioRepository;
    private final ConsecutivoGenerator consecutivos;
    private final AuditoriaService auditoriaService;

    /**
     * @param abonoRepository          abonos
     * @param cuentaRepository         cuentas por cobrar
     * @param clienteRepository        clientes
     * @param turnoRepository          turno de la sesión
     * @param movimientoCajaRepository libro de caja
     * @param ventaRepository          consecutivos de las ventas
     * @param usuarioRepository        nombres
     * @param consecutivos             consecutivo {@code AB}
     * @param auditoriaService         registro del abono
     */
    public AbonoService(AbonoClienteRepository abonoRepository, CuentaPorCobrarRepository cuentaRepository,
            ClienteRepository clienteRepository, TurnoCajaRepository turnoRepository,
            MovimientoCajaRepository movimientoCajaRepository, VentaRepository ventaRepository,
            UsuarioRepository usuarioRepository, ConsecutivoGenerator consecutivos, AuditoriaService auditoriaService) {
        this.abonoRepository = abonoRepository;
        this.cuentaRepository = cuentaRepository;
        this.clienteRepository = clienteRepository;
        this.turnoRepository = turnoRepository;
        this.movimientoCajaRepository = movimientoCajaRepository;
        this.ventaRepository = ventaRepository;
        this.usuarioRepository = usuarioRepository;
        this.consecutivos = consecutivos;
        this.auditoriaService = auditoriaService;
    }

    /**
     * @param req cliente, valor, medio y referencia
     * @return el abono con su recibo
     * @throws NegocioException sin turno abierto, sin deuda o si el abono pasa lo que debe
     */
    @Transactional
    public AbonoResponse registrar(AbonoRequest req) {
        Long yo = SesionActual.usuario().id();
        boolean sinReferencia = req.referencia() == null || req.referencia().isBlank();
        if (sinReferencia && req.medioPago() != MedioPago.EFECTIVO) {
            throw new NegocioException("REFERENCIA_REQUERIDA",
                    "Escriba la referencia o el número de aprobación del pago con " + req.medioPago());
        }
        // Mismo orden de bloqueo que la venta y la anulación (primero el turno, después las cuentas del cliente), para
        // que un abono y una venta a crédito del mismo cajero no se bloqueen entre sí. Las dos son lecturas con bloqueo,
        // así que ven lo último confirmado aunque otra petición haya terminado mientras se esperaba.
        TurnoCaja turno = turnoRepository.abiertoDeBloqueado(yo)
                .orElseThrow(() -> new NegocioException("TURNO_NO_ABIERTO", "Abra su turno de caja para recibir el abono"));
        List<CuentaPorCobrar> cuentas = cuentaRepository.abiertasBloqueadas(req.clienteId());
        Cliente cliente = clienteRepository.findById(req.clienteId()).orElseThrow(() -> new NoEncontradoException("Cliente no encontrado"));
        BigDecimal valor = req.valor().setScale(2, java.math.RoundingMode.HALF_UP);
        BigDecimal debe = cuentas.stream().map(CuentaPorCobrar::getSaldo).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (debe.signum() == 0) {
            throw new NegocioException("SIN_DEUDA", cliente.getNombre() + " no tiene saldo por pagar");
        }
        if (valor.compareTo(debe) > 0) {
            throw new NegocioException("ABONO_EXCEDE", cliente.getNombre() + " debe " + debe.toPlainString()
                    + ": el abono no puede ser mayor");
        }
        AbonoCliente a = new AbonoCliente();
        a.setConsecutivo(consecutivos.siguiente(ConsecutivoGenerator.ABONO_CLIENTE));
        a.setClienteId(cliente.getId());
        a.setTurnoId(turno.getId());
        a.setValor(valor);
        a.setMedioPago(req.medioPago());
        a.setReferencia(req.referencia() == null || req.referencia().isBlank() ? null : req.referencia().trim());
        a.setUsuarioId(yo);
        a.setCreadoEn(LocalDateTime.now());
        BigDecimal resto = valor;
        for (CuentaPorCobrar c : cuentas) {
            if (resto.signum() == 0) {
                break;
            }
            BigDecimal aplica = resto.min(c.getSaldo());
            c.setSaldo(c.getSaldo().subtract(aplica));
            if (c.getSaldo().signum() == 0) {
                c.setEstado(EstadoCuenta.PAGADA);
            }
            AbonoAplicacion x = new AbonoAplicacion();
            x.setAbono(a);
            x.setCuentaId(c.getId());
            x.setValor(aplica);
            a.getAplicaciones().add(x);
            resto = resto.subtract(aplica);
        }
        abonoRepository.saveAndFlush(a);

        MovimientoCaja m = new MovimientoCaja();
        m.setTurnoId(turno.getId());
        m.setTipo(TipoMovimientoCaja.ABONO_CARTERA);
        m.setMedioPago(req.medioPago());
        m.setMonto(valor);
        m.setReferenciaTipo(REFERENCIA);
        m.setReferenciaId(a.getId());
        m.setMotivo("Abono " + a.getConsecutivo() + " de " + cliente.getNombre());
        m.setUsuarioId(yo);
        movimientoCajaRepository.save(m);
        auditoriaService.exito(AccionAuditoria.ABONO_REGISTRAR, EntidadAuditada.ABONO_CLIENTE, a.getId(), a.getConsecutivo()
                + "; cliente=" + cliente.getId() + "; valor=" + valor.toPlainString() + "; medio=" + req.medioPago());
        return respuesta(a);
    }

    /**
     * @param id abono
     * @return el abono con su recibo
     * @throws NoEncontradoException si no es de la empresa
     */
    @Transactional(readOnly = true)
    public AbonoResponse obtener(Long id) {
        return respuesta(abonoRepository.findById(id).orElseThrow(() -> new NoEncontradoException("Abono no encontrado")));
    }

    /**
     * @param a abono
     * @return respuesta con nombres, a qué ventas se aplicó y el saldo que le queda al cliente
     */
    AbonoResponse respuesta(AbonoCliente a) {
        Cliente c = clienteRepository.findById(a.getClienteId()).orElse(null);
        Map<Long, CuentaPorCobrar> cuentas = cuentaRepository.findAllById(a.getAplicaciones().stream()
                .map(AbonoAplicacion::getCuentaId).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(CuentaPorCobrar::getId, x -> x));
        Map<Long, String> ventas = ventaRepository.findAllById(cuentas.values().stream().map(CuentaPorCobrar::getVentaId)
                .collect(Collectors.toSet())).stream().collect(Collectors.toMap(Venta::getId, Venta::getConsecutivo));
        List<AbonoResponse.Aplicacion> aplicaciones = a.getAplicaciones().stream().map(x -> new AbonoResponse.Aplicacion(
                x.getCuentaId(), cuentas.containsKey(x.getCuentaId()) ? ventas.get(cuentas.get(x.getCuentaId()).getVentaId()) : null,
                x.getValor())).toList();
        BigDecimal pendiente = cuentaRepository.findByClienteIdOrderByIdDesc(a.getClienteId()).stream()
                .filter(x -> x.getEstado() == EstadoCuenta.ABIERTA).map(CuentaPorCobrar::getSaldo)
                .reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
        String usuario = usuarioRepository.findById(a.getUsuarioId()).map(Usuario::getNombreCompleto).orElse(null);
        return new AbonoResponse(a.getId(), a.getConsecutivo(), a.getClienteId(), c == null ? null : c.getNombre(),
                c == null ? null : c.getTipoDocumento() + " " + c.getNumeroDocumento(), a.getValor(), a.getMedioPago(),
                a.getReferencia(), usuario, a.getCreadoEn(), aplicaciones, pendiente);
    }
}
