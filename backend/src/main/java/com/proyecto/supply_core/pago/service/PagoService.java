package com.proyecto.supply_core.pago.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.proyecto.supply_core.auditoria.enums.AccionAuditoria;
import com.proyecto.supply_core.auditoria.enums.EntidadAuditada;
import com.proyecto.supply_core.auditoria.service.AuditoriaService;
import com.proyecto.supply_core.common.dto.PageResponse;
import com.proyecto.supply_core.common.exception.AccesoDenegadoException;
import com.proyecto.supply_core.common.exception.NegocioException;
import com.proyecto.supply_core.common.exception.NoEncontradoException;
import com.proyecto.supply_core.common.exception.ParametroInvalidoException;
import com.proyecto.supply_core.common.tenant.EmpresaContexto;
import com.proyecto.supply_core.common.util.Ordenamiento;
import com.proyecto.supply_core.compra.adjudicacion.entity.Adjudicacion;
import com.proyecto.supply_core.compra.adjudicacion.repository.AdjudicacionRepository;
import com.proyecto.supply_core.compra.orden.entity.OrdenCompra;
import com.proyecto.supply_core.compra.orden.enums.EstadoOrden;
import com.proyecto.supply_core.compra.orden.repository.OrdenCompraRepository;
import com.proyecto.supply_core.compra.solicitud.entity.AprobacionSolicitud;
import com.proyecto.supply_core.compra.solicitud.entity.SolicitudCompra;
import com.proyecto.supply_core.compra.solicitud.repository.AprobacionSolicitudRepository;
import com.proyecto.supply_core.compra.solicitud.repository.SolicitudCompraRepository;
import com.proyecto.supply_core.documento.dto.DocumentoResponse;
import com.proyecto.supply_core.documento.enums.EntidadDocumento;
import com.proyecto.supply_core.documento.service.DocumentoService;
import com.proyecto.supply_core.empresa.entity.Empresa;
import com.proyecto.supply_core.empresa.repository.EmpresaRepository;
import com.proyecto.supply_core.factura.entity.FacturaProveedor;
import com.proyecto.supply_core.factura.service.FacturaService;
import com.proyecto.supply_core.notificacion.enums.EventoNotificacion;
import com.proyecto.supply_core.notificacion.service.NotificacionService;
import com.proyecto.supply_core.pago.dto.PagoResponse;
import com.proyecto.supply_core.pago.dto.PagosOrdenResponse;
import com.proyecto.supply_core.pago.dto.RegistrarPagoRequest;
import com.proyecto.supply_core.pago.entity.Pago;
import com.proyecto.supply_core.pago.enums.EstadoPago;
import com.proyecto.supply_core.pago.enums.TipoPago;
import com.proyecto.supply_core.pago.repository.PagoRepository;
import com.proyecto.supply_core.security.context.SesionActual;
import com.proyecto.supply_core.security.context.UsuarioAutenticado;
import com.proyecto.supply_core.usuario.repository.UsuarioRepository;

/**
 * UC-11 Registrar pago. Solo registra lo que el área administrativa ya pagó por fuera (no hay pasarela ni
 * contabilidad).
 * <ul>
 *   <li>Se paga la versión vigente de la orden desde que se emite (anticipos y pagos parciales). La suma de
 *       los pagos de todas las versiones nunca supera el total vigente.</li>
 *   <li>Sin comprobante el pago queda con evidencia pendiente; el comprobante se adjunta una sola vez.</li>
 *   <li>Segregación de funciones: no registra el pago quien pidió la compra, quien la aprobó (solicitud o
 *       adjudicación), quien adjudicó ni quien emitió alguna versión de la orden.</li>
 *   <li>Un pago no se borra ni se edita: si fue un error se anula con motivo y deja de sumar.</li>
 *   <li>Hay dos tipos. El ANTICIPO se paga antes de recibir y la suma de anticipos no supera el tope de la empresa
 *       (% del total de la orden). El PAGO_FINAL solo se registra con la orden ya recibida y una factura CUADRADA
 *       o APROBADA; no puede superar lo que falta por pagar de esa factura (descuenta lo ya pagado).</li>
 * </ul>
 */
@Service
public class PagoService {

    private static final Logger log = LoggerFactory.getLogger(PagoService.class);
    /** Estados de la orden que admiten pagos. */
    private static final Set<EstadoOrden> PAGABLES = EnumSet.of(EstadoOrden.EMITIDA, EstadoOrden.ACEPTADA_PROVEEDOR,
            EstadoOrden.EN_PREPARACION, EstadoOrden.DESPACHADA, EstadoOrden.RECIBIDA_PARCIAL,
            EstadoOrden.RECIBIDA_TOTAL, EstadoOrden.CERRADA);
    /** Estados de la orden en los que se puede registrar el pago final (ya hay recepciones). */
    private static final Set<EstadoOrden> FINALES = EnumSet.of(EstadoOrden.RECIBIDA_PARCIAL, EstadoOrden.RECIBIDA_TOTAL,
            EstadoOrden.CERRADA);
    private static final BigDecimal CIEN = BigDecimal.valueOf(100);
    /** Campos por los que se puede ordenar. */
    private static final Set<String> CAMPOS_ORDENABLES = Set.of("fechaPago", "creadoEn", "valor", "estado");

    private final PagoRepository pagoRepository;
    private final OrdenCompraRepository ordenRepository;
    private final AdjudicacionRepository adjudicacionRepository;
    private final AprobacionSolicitudRepository aprobacionRepository;
    private final UsuarioRepository usuarioRepository;
    private final DocumentoService documentoService;
    private final AuditoriaService auditoriaService;
    private final NotificacionService notificacionService;
    private final EmpresaRepository empresaRepository;
    private final FacturaService facturaService;
    private final SolicitudCompraRepository solicitudRepository;

    /**
     * @param pagoRepository         pagos
     * @param ordenRepository        órdenes
     * @param adjudicacionRepository adjudicación de origen (segregación)
     * @param aprobacionRepository   aprobaciones de la solicitud (segregación)
     * @param usuarioRepository      usuarios del proveedor (avisos)
     * @param documentoService       comprobantes
     * @param auditoriaService       registro de acciones
     * @param notificacionService    avisos
     * @param empresaRepository      tope de anticipos de la empresa
     * @param facturaService         factura que respalda el pago final
     * @param solicitudRepository    solicitud de una orden directa de contrato (segregación)
     */
    public PagoService(PagoRepository pagoRepository, OrdenCompraRepository ordenRepository,
            AdjudicacionRepository adjudicacionRepository, AprobacionSolicitudRepository aprobacionRepository,
            UsuarioRepository usuarioRepository, DocumentoService documentoService, AuditoriaService auditoriaService,
            NotificacionService notificacionService, EmpresaRepository empresaRepository,
            FacturaService facturaService, SolicitudCompraRepository solicitudRepository) {
        this.solicitudRepository = solicitudRepository;
        this.empresaRepository = empresaRepository;
        this.facturaService = facturaService;
        this.pagoRepository = pagoRepository;
        this.ordenRepository = ordenRepository;
        this.adjudicacionRepository = adjudicacionRepository;
        this.aprobacionRepository = aprobacionRepository;
        this.usuarioRepository = usuarioRepository;
        this.documentoService = documentoService;
        this.auditoriaService = auditoriaService;
        this.notificacionService = notificacionService;
    }

    /**
     * Registra un pago de la orden (queda REGISTRADO con evidencia pendiente hasta adjuntar el comprobante).
     *
     * @param ordenId versión vigente de la orden
     * @param req     método, valor, fecha y referencia
     * @return pago registrado
     * @throws NegocioException           si la orden no admite pagos o el valor supera el saldo
     * @throws ParametroInvalidoException si la fecha es anterior a la emisión de la orden
     * @throws AccesoDenegadoException    si el usuario participó en la compra (segregación de funciones)
     */
    @Transactional
    public PagoResponse registrar(Long ordenId, RegistrarPagoRequest req) {
        ordenRepository.bloquear(ordenId).orElseThrow(() -> new NoEncontradoException("Orden", ordenId));
        OrdenCompra o = ordenRepository.findDetalleById(ordenId).orElseThrow(() -> new NoEncontradoException("Orden", ordenId));
        if (!PAGABLES.contains(o.getEstado())) {
            throw new NegocioException("ORDEN_NO_PAGABLE", o.getEstado() == EstadoOrden.REEMPLAZADA
                    ? "La versión " + o.getVersion() + " de " + o.getConsecutivo() + " fue reemplazada; registre el pago en la versión vigente"
                    : "La orden " + o.getConsecutivo() + " está " + o.getEstado() + " y no admite pagos");
        }
        List<OrdenCompra> versiones = ordenRepository.findByConsecutivoOrderByVersionAsc(o.getConsecutivo());
        LocalDate emision = versiones.isEmpty() ? o.getCreadoEn().toLocalDate() : versiones.get(0).getCreadoEn().toLocalDate();
        if (req.fechaPago().isBefore(emision)) {
            throw new ParametroInvalidoException("fechaPago: no puede ser anterior a la emisión de la orden (" + emision + ")");
        }
        UsuarioAutenticado usuario = SesionActual.usuario();
        if (involucrados(o, versiones).contains(usuario.id())) {
            throw new AccesoDenegadoException(
                    "No puede registrar el pago de una compra en la que participó (segregación de funciones)");
        }
        BigDecimal saldo = o.getTotal().subtract(pagoRepository.pagadoPorConsecutivo(o.getConsecutivo()));
        if (req.valor().compareTo(saldo) > 0) {
            throw new NegocioException("PAGO_EXCEDE_SALDO", "El pago de $ " + req.valor().toPlainString()
                    + " supera el saldo de la orden " + o.getConsecutivo() + " ($ " + saldo.toPlainString() + ")");
        }

        exigirReglasDelTipo(o, req);

        Pago p = new Pago();
        p.setOrden(o);
        p.setTipo(req.tipo());
        p.setUsuarioId(usuario.id());
        p.setMetodo(req.metodo());
        p.setValor(req.valor());
        p.setFechaPago(req.fechaPago());
        p.setReferencia(req.referencia() == null || req.referencia().isBlank() ? null : req.referencia().trim());
        p.setEstado(EstadoPago.REGISTRADO);
        p.setEvidenciaPendiente(true);
        Pago guardado = pagoRepository.save(p);
        avisar(o, EventoNotificacion.PAGO_REGISTRADO, "Se registró un pago de $ " + guardado.getValor().toPlainString()
                + " (" + guardado.getMetodo() + ") a la orden " + o.getConsecutivo() + "; saldo $ "
                + saldo.subtract(guardado.getValor()).toPlainString());
        auditoriaService.exito(AccionAuditoria.PAGO_REGISTRAR, EntidadAuditada.PAGO, guardado.getId(),
                o.getConsecutivo() + " v" + o.getVersion() + "; tipo=" + guardado.getTipo() + "; valor=" + guardado.getValor() + "; metodo="
                        + guardado.getMetodo() + "; referencia=" + guardado.getReferencia());
        log.info("Pago {} de {} registrado a {}", guardado.getId(), guardado.getValor(), o.getConsecutivo());
        return PagoResponse.of(guardado);
    }

    /**
     * Adjunta el comprobante (una sola vez) y cierra la evidencia pendiente.
     *
     * @param id      pago
     * @param archivo comprobante (PDF o imagen)
     * @return pago con comprobante
     * @throws NegocioException si el pago está anulado o ya tiene comprobante
     */
    @Transactional
    public PagoResponse adjuntarComprobante(Long id, MultipartFile archivo) {
        Pago p = bloquear(id);
        exigirRegistrado(p);
        if (p.getComprobanteDocumentoId() != null) {
            throw new NegocioException("COMPROBANTE_EXISTENTE", "El pago ya tiene comprobante; no se reemplaza");
        }
        DocumentoResponse doc = documentoService.subir(EntidadDocumento.PAGO, id, archivo);
        p.setComprobanteDocumentoId(doc.id());
        p.setEvidenciaPendiente(false);
        auditoriaService.exito(AccionAuditoria.PAGO_COMPROBANTE, EntidadAuditada.PAGO, id,
                p.getOrden().getConsecutivo() + "; documento=" + doc.id());
        return PagoResponse.of(p);
    }

    /**
     * Anula un pago registrado por error (deja de sumar al pagado).
     *
     * @param id     pago
     * @param motivo motivo obligatorio
     * @return pago ANULADO
     * @throws NegocioException si ya estaba anulado
     */
    @Transactional
    public PagoResponse anular(Long id, String motivo) {
        if (motivo == null || motivo.isBlank()) {
            throw new ParametroInvalidoException("motivo: es obligatorio");
        }
        Pago p = bloquear(id);
        exigirRegistrado(p);
        p.setEstado(EstadoPago.ANULADO);
        p.setMotivoAnulacion(motivo.trim());
        p.setAnuladoPorUsuarioId(SesionActual.usuario().id());
        p.setAnuladoEn(LocalDateTime.now());
        avisar(p.getOrden(), EventoNotificacion.PAGO_ANULADO, "Se anuló el pago de $ " + p.getValor().toPlainString()
                + " de la orden " + p.getOrden().getConsecutivo() + ": " + p.getMotivoAnulacion());
        auditoriaService.exito(AccionAuditoria.PAGO_ANULAR, EntidadAuditada.PAGO, id,
                p.getOrden().getConsecutivo() + "; valor=" + p.getValor() + "; motivo=" + p.getMotivoAnulacion());
        return PagoResponse.of(p);
    }

    /**
     * Detalle de un pago.
     *
     * @param id pago
     * @return pago
     */
    @Transactional(readOnly = true)
    public PagoResponse obtener(Long id) {
        return PagoResponse.of(pagoRepository.findDetalleById(id).orElseThrow(() -> new NoEncontradoException("Pago", id)));
    }

    /**
     * Estado de pago de una orden (todas sus versiones): total vigente, pagado, saldo y pagos.
     *
     * @param ordenId cualquier versión de la orden
     * @return resumen de pagos
     */
    @Transactional(readOnly = true)
    public PagosOrdenResponse deOrden(Long ordenId) {
        OrdenCompra o = ordenRepository.findById(ordenId).orElseThrow(() -> new NoEncontradoException("Orden", ordenId));
        return resumen(o);
    }

    /**
     * Estado de pago de una orden del proveedor de la sesión.
     *
     * @param ordenId cualquier versión de la orden
     * @return resumen de pagos
     * @throws NoEncontradoException si la orden no existe o es de otro proveedor
     */
    @Transactional(readOnly = true)
    public PagosOrdenResponse deOrdenDelProveedor(Long ordenId) {
        OrdenCompra o = ordenRepository.findDetalleById(ordenId)
                .filter(x -> x.getProveedor().getId().equals(SesionActual.usuario().proveedorId()))
                .orElseThrow(() -> new NoEncontradoException("Orden", ordenId));
        return resumen(o);
    }

    /**
     * Listado con filtros.
     *
     * @param estado             estado
     * @param evidenciaPendiente con o sin comprobante pendiente
     * @param proveedorId        proveedor
     * @param desde              fecha de pago desde
     * @param hasta              fecha de pago hasta
     * @param pageable           página y orden
     * @return página de pagos
     * @throws ParametroInvalidoException si {@code desde} es posterior a {@code hasta}
     */
    @Transactional(readOnly = true)
    public PageResponse<PagoResponse> buscar(EstadoPago estado, Boolean evidenciaPendiente, Long proveedorId,
            LocalDate desde, LocalDate hasta, Pageable pageable) {
        if (desde != null && hasta != null && desde.isAfter(hasta)) {
            throw new ParametroInvalidoException("desde: no puede ser posterior a hasta");
        }
        return PageResponse.of(pagoRepository.buscar(estado, evidenciaPendiente, proveedorId, desde, hasta,
                Ordenamiento.validar(pageable, CAMPOS_ORDENABLES)), PagoResponse::of);
    }

    // ----------------------------------------------------------------- apoyo

    /**
     * Aplica las reglas propias del tipo de pago. ANTICIPO: la suma de anticipos no supera el tope de la empresa.
     * PAGO_FINAL: la orden ya tiene recepciones, hay una factura que cuadra y el pago no supera lo que falta por
     * pagar de esa factura.
     *
     * @param o   orden que se paga
     * @param req datos del pago
     * @throws NegocioException si no se cumple la regla del tipo
     */
    private void exigirReglasDelTipo(OrdenCompra o, RegistrarPagoRequest req) {
        if (req.tipo() == TipoPago.PAGO_FINAL) {
            if (!FINALES.contains(o.getEstado())) {
                throw new NegocioException("PAGO_FINAL_SIN_RECEPCION", "La orden " + o.getConsecutivo() + " está "
                        + o.getEstado() + "; el pago final solo se registra después de recibir");
            }
            FacturaProveedor factura = facturaService.exigirPagable(o);
            BigDecimal saldoFactura = factura.getTotal().subtract(pagoRepository.pagadoPorConsecutivo(o.getConsecutivo()));
            if (req.valor().compareTo(saldoFactura) > 0) {
                throw new NegocioException("PAGO_EXCEDE_FACTURA", "El pago de $ " + req.valor().toPlainString()
                        + " supera lo que falta por pagar de la factura " + factura.getNumero() + " ($ "
                        + saldoFactura.max(BigDecimal.ZERO).toPlainString() + ")");
            }
            return;
        }
        Empresa empresa = empresaRepository.findById(EmpresaContexto.requerida())
                .orElseThrow(() -> new NoEncontradoException("Empresa", EmpresaContexto.requerida()));
        BigDecimal tope = o.getTotal().multiply(empresa.getTopeAnticipoPct()).divide(CIEN, 2, RoundingMode.HALF_UP);
        BigDecimal anticipado = pagoRepository.anticipadoPorConsecutivo(o.getConsecutivo());
        if (anticipado.add(req.valor()).compareTo(tope) > 0) {
            throw new NegocioException("ANTICIPO_EXCEDE_TOPE", "Los anticipos de la orden " + o.getConsecutivo()
                    + " no pueden superar $ " + tope.toPlainString() + " (" + empresa.getTopeAnticipoPct().stripTrailingZeros().toPlainString()
                    + " % del total); ya van $ " + anticipado.toPlainString() + ". El resto se paga contra factura");
        }
    }

    /**
     * Resumen de pagos del consecutivo de la orden.
     *
     * @param o cualquier versión
     * @return total vigente, pagado, saldo y pagos
     */
    private PagosOrdenResponse resumen(OrdenCompra o) {
        List<OrdenCompra> versiones = ordenRepository.findByConsecutivoOrderByVersionAsc(o.getConsecutivo());
        OrdenCompra vigente = versiones.isEmpty() ? o : versiones.get(versiones.size() - 1);
        BigDecimal pagado = pagoRepository.pagadoPorConsecutivo(o.getConsecutivo());
        List<PagoResponse> pagos = pagoRepository.findByOrden_ConsecutivoOrderByFechaPagoAscIdAsc(o.getConsecutivo())
                .stream().map(PagoResponse::of).toList();
        return new PagosOrdenResponse(o.getConsecutivo(), vigente.getId(), vigente.getTotal(), pagado,
                vigente.getTotal().subtract(pagado), pagos);
    }

    /**
     * Usuarios que participaron en la compra: solicitante, quienes decidieron la solicitud, quien adjudicó,
     * quien aprobó la adjudicación y quienes emitieron cada versión de la orden.
     *
     * @param o         orden
     * @param versiones todas sus versiones
     * @return ids de usuario
     */
    private Set<Long> involucrados(OrdenCompra o, List<OrdenCompra> versiones) {
        Set<Long> ids = new HashSet<>();
        ids.add(o.getUsuarioId());
        versiones.forEach(v -> ids.add(v.getUsuarioId()));
        if (o.getAdjudicacionId() != null) {
            adjudicacionRepository.findDetalleById(o.getAdjudicacionId()).ifPresent((Adjudicacion adj) -> {
                ids.add(adj.getUsuarioId());
                ids.add(adj.getAprobadoPorUsuarioId());
                agregarDeSolicitud(adj.getRfq().getSolicitud(), ids);
            });
        } else if (o.getSolicitudId() != null) {
            // orden directa de contrato: no hay adjudicación, el origen es la solicitud aprobada
            solicitudRepository.findById(o.getSolicitudId()).ifPresent(s -> agregarDeSolicitud(s, ids));
        }
        ids.removeIf(Objects::isNull);
        return ids;
    }

    /**
     * Agrega al conjunto de involucrados al solicitante y a quienes decidieron la solicitud.
     *
     * @param s   solicitud de origen de la compra
     * @param ids conjunto que se completa
     */
    private void agregarDeSolicitud(SolicitudCompra s, Set<Long> ids) {
        ids.add(s.getUsuarioSolicitanteId());
        aprobacionRepository.findBySolicitudCompraIdOrderByDecididoEnAscIdAsc(s.getId()).stream()
                .map(AprobacionSolicitud::getUsuarioAprobadorId).forEach(ids::add);
    }

    /**
     * Bloquea el pago y lo carga con su orden.
     *
     * @param id pago
     * @return pago bloqueado
     */
    private Pago bloquear(Long id) {
        pagoRepository.bloquear(id).orElseThrow(() -> new NoEncontradoException("Pago", id));
        return pagoRepository.findDetalleById(id).orElseThrow(() -> new NoEncontradoException("Pago", id));
    }

    /**
     * Exige que el pago no esté anulado.
     *
     * @param p pago
     * @throws NegocioException si está anulado
     */
    private static void exigirRegistrado(Pago p) {
        if (p.getEstado() != EstadoPago.REGISTRADO) {
            throw new NegocioException("PAGO_ANULADO", "El pago " + p.getId() + " está " + p.getEstado());
        }
    }

    /**
     * Avisa a los usuarios del proveedor y al comprador que emitió la orden.
     *
     * @param o       orden
     * @param evento  evento
     * @param mensaje texto
     */
    private void avisar(OrdenCompra o, EventoNotificacion evento, String mensaje) {
        usuarioRepository.findByProveedorId(o.getProveedor().getId())
                .forEach(u -> notificacionService.notificar(u.getId(), evento, mensaje));
        if (o.getUsuarioId() != null) {
            notificacionService.notificar(o.getUsuarioId(), evento, mensaje);
        }
    }
}
