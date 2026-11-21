package com.proyecto.supply_core.venta.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

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
import com.proyecto.supply_core.common.exception.AccesoDenegadoException;
import com.proyecto.supply_core.common.exception.NegocioException;
import com.proyecto.supply_core.common.exception.NoEncontradoException;
import com.proyecto.supply_core.inventario.service.MovimientoInventarioService;
import com.proyecto.supply_core.security.context.SesionActual;
import com.proyecto.supply_core.venta.dto.AnularVentaRequest;
import com.proyecto.supply_core.venta.dto.VentaResponse;
import com.proyecto.supply_core.venta.entity.Venta;
import com.proyecto.supply_core.venta.enums.EstadoVenta;
import com.proyecto.supply_core.venta.repository.DevolucionVentaRepository;
import com.proyecto.supply_core.venta.repository.VentaRepository;

/**
 * Anular una venta (spec 006): solo del turno abierto, por su cajero y con la autorización de otra persona. Devuelve el
 * stock ({@code ANULACION_VENTA}) y registra en la caja la salida del dinero por cada medio ({@code ANULACION}, en
 * negativo). La venta no se borra: queda {@code ANULADA} con motivo y autorizador, y su consecutivo no se reutiliza.
 * Una venta de un turno cerrado no se anula: se hace una devolución.
 */
@Service
public class AnulacionVentaService {

    private final VentaRepository ventaRepository;
    private final TurnoCajaRepository turnoRepository;
    private final MovimientoCajaRepository movimientoCajaRepository;
    private final DevolucionVentaRepository devolucionRepository;
    private final MovimientoInventarioService movimientoInventarioService;
    private final AutorizacionService autorizacionService;
    private final AuditoriaService auditoriaService;
    private final VentaService ventaService;
    private final CarteraService carteraService;
    private final AlertaCajeroService alertaCajero;

    /**
     * @param ventaRepository             ventas
     * @param turnoRepository             turno de la venta
     * @param movimientoCajaRepository    libro de caja
     * @param devolucionRepository        devoluciones (una venta con devoluciones no se anula)
     * @param movimientoInventarioService entrada por anulación
     * @param autorizacionService         permiso del supervisor
     * @param auditoriaService            registro de la anulación
     * @param ventaService                detalle de la venta
     * @param carteraService              anula la cuenta y devuelve el saldo a favor (spec 007)
     * @param alertaCajero                alerta de anulaciones por cajero (spec 010)
     */
    public AnulacionVentaService(VentaRepository ventaRepository, TurnoCajaRepository turnoRepository,
            MovimientoCajaRepository movimientoCajaRepository, DevolucionVentaRepository devolucionRepository,
            MovimientoInventarioService movimientoInventarioService, AutorizacionService autorizacionService,
            AuditoriaService auditoriaService, VentaService ventaService, CarteraService carteraService,
            AlertaCajeroService alertaCajero) {
        this.ventaRepository = ventaRepository;
        this.turnoRepository = turnoRepository;
        this.movimientoCajaRepository = movimientoCajaRepository;
        this.devolucionRepository = devolucionRepository;
        this.movimientoInventarioService = movimientoInventarioService;
        this.autorizacionService = autorizacionService;
        this.auditoriaService = auditoriaService;
        this.ventaService = ventaService;
        this.carteraService = carteraService;
        this.alertaCajero = alertaCajero;
    }

    /**
     * @param id  venta
     * @param req motivo y autorización
     * @return la venta anulada
     * @throws NoEncontradoException  si la venta no es de la empresa
     * @throws AccesoDenegadoException si quien anula no es el cajero del turno
     * @throws NegocioException        si ya está anulada, su turno está cerrado, tiene devoluciones, la autorización no
     *                                 sirve o no hay efectivo para devolver
     */
    @Transactional
    public VentaResponse anular(Long id, AnularVentaRequest req) {
        Venta v = ventaRepository.bloquear(id).orElseThrow(() -> new NoEncontradoException("Venta no encontrada"));
        if (v.getEstado() != EstadoVenta.CONFIRMADA) {
            throw new NegocioException("VENTA_ANULADA", "La venta " + v.getConsecutivo() + " ya está anulada");
        }
        TurnoCaja turno = turnoRepository.bloquear(v.getTurnoId())
                .orElseThrow(() -> new NoEncontradoException("Turno no encontrado"));
        if (turno.getEstado() != EstadoTurno.ABIERTO) {
            throw new NegocioException("TURNO_CERRADO",
                    "La venta es de un turno ya cerrado: no se anula, registre una devolución");
        }
        Long yo = SesionActual.usuario().id();
        if (!turno.getCajeroUsuarioId().equals(yo)) {
            throw new AccesoDenegadoException("Solo el cajero del turno anula sus ventas");
        }
        if (devolucionRepository.existsByVentaId(v.getId())) {
            throw new NegocioException("TIENE_DEVOLUCIONES",
                    "La venta ya tiene devoluciones: devuelva el resto en vez de anularla");
        }
        String motivo = req.motivo().trim();

        // Caja: lo que entró por cada medio sale en negativo; el efectivo debe estar en la caja
        List<MovimientoCaja> entradas = movimientoCajaRepository.findByReferenciaTipoAndReferenciaId(VentaService.REFERENCIA,
                v.getId()).stream().filter(m -> m.getTipo() == TipoMovimientoCaja.VENTA).toList();
        BigDecimal efectivo = entradas.stream().filter(m -> m.getMedioPago() == MedioPago.EFECTIVO)
                .map(MovimientoCaja::getMonto).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (efectivo.signum() > 0 && movimientoCajaRepository.saldo(turno.getId(), MedioPago.EFECTIVO).compareTo(efectivo) < 0) {
            throw new NegocioException("EFECTIVO_INSUFICIENTE",
                    "No hay efectivo suficiente en la caja para devolver " + efectivo.toPlainString());
        }
        Long supervisor = autorizacionService.consumir(req.autorizacion(), AccionAutorizada.ANULAR_VENTA, turno.getId(),
                v.getId());
        carteraService.revertirVenta(v);

        // Stock: vuelve a la bodega de la caja, por producto en orden de id
        Map<Long, BigDecimal> porProducto = new TreeMap<>();
        v.getLineas().forEach(l -> porProducto.merge(l.getProductoId(), l.getCantidadUnidadProducto(), BigDecimal::add));
        porProducto.forEach((productoId, cantidad) -> movimientoInventarioService.registrarAnulacionVenta(productoId,
                v.getBodegaId(), cantidad, "Anulación de " + v.getConsecutivo() + ": " + motivo, v.getId()));

        for (MovimientoCaja e : entradas) {
            MovimientoCaja m = new MovimientoCaja();
            m.setTurnoId(turno.getId());
            m.setTipo(TipoMovimientoCaja.ANULACION);
            m.setMedioPago(e.getMedioPago());
            m.setMonto(e.getMonto().negate());
            m.setReferenciaTipo(VentaService.REFERENCIA);
            m.setReferenciaId(v.getId());
            m.setMotivo("Anulación de " + v.getConsecutivo());
            m.setUsuarioId(yo);
            m.setAutorizadoPorUsuarioId(supervisor);
            movimientoCajaRepository.save(m);
        }

        v.setEstado(EstadoVenta.ANULADA);
        v.setAnuladaEn(LocalDateTime.now());
        v.setAnuladaPorUsuarioId(yo);
        v.setMotivoAnulacion(motivo);
        v.setAnulacionAutorizadaPorUsuarioId(supervisor);
        auditoriaService.exito(AccionAuditoria.VENTA_ANULAR, EntidadAuditada.VENTA, v.getId(), v.getConsecutivo()
                + "; total=" + v.totalAPagar().toPlainString() + "; autorizó=" + supervisor + "; motivo=" + motivo);
        alertaCajero.revisarDespuesDeConfirmar(v.getCajeroUsuarioId(), AlertaCajeroService.Tipo.ANULACIONES);
        return ventaService.respuesta(v);
    }
}
