package com.proyecto.supply_core.factura.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
import com.proyecto.supply_core.common.tenant.EmpresaContexto;
import com.proyecto.supply_core.common.util.Ordenamiento;
import com.proyecto.supply_core.compra.orden.entity.OrdenCompra;
import com.proyecto.supply_core.compra.orden.enums.EstadoOrden;
import com.proyecto.supply_core.compra.orden.repository.OrdenCompraRepository;
import com.proyecto.supply_core.compra.orden.service.OrdenCompraService;
import com.proyecto.supply_core.documento.dto.DocumentoResponse;
import com.proyecto.supply_core.documento.enums.EntidadDocumento;
import com.proyecto.supply_core.documento.service.DocumentoService;
import com.proyecto.supply_core.empresa.entity.Empresa;
import com.proyecto.supply_core.empresa.repository.EmpresaRepository;
import com.proyecto.supply_core.factura.dto.FacturaResponse;
import com.proyecto.supply_core.factura.dto.RegistrarFacturaRequest;
import com.proyecto.supply_core.factura.entity.FacturaProveedor;
import com.proyecto.supply_core.factura.enums.EstadoFactura;
import com.proyecto.supply_core.factura.repository.FacturaProveedorRepository;
import com.proyecto.supply_core.notificacion.enums.EventoNotificacion;
import com.proyecto.supply_core.notificacion.service.NotificacionService;
import com.proyecto.supply_core.pago.repository.PagoRepository;
import com.proyecto.supply_core.recepcion.repository.RecepcionLineaRepository;
import com.proyecto.supply_core.recepcion.repository.RecepcionLineaRepository.AgregadoLinea;
import com.proyecto.supply_core.security.context.Roles;
import com.proyecto.supply_core.security.context.SesionActual;
import com.proyecto.supply_core.security.context.UsuarioAutenticado;

/**
 * Factura del proveedor y cruce de tres vías (orden, recepción y factura).
 * <ul>
 *   <li>Se registra sobre una orden que ya tiene recepciones (RECIBIDA_PARCIAL, RECIBIDA_TOTAL o CERRADA). Hay
 *       una sola factura vigente por orden; el número no se repite por proveedor.</li>
 *   <li>El sistema calcula lo que debería facturar el proveedor (cantidad aceptada por el precio de la orden más
 *       impuesto) y la compara con el total facturado. Dentro de la tolerancia de la empresa queda CUADRADA; si
 *       no, EN_REVISION y avisa a compras.</li>
 *   <li>Compras aprueba (con motivo) una factura EN_REVISION; quien la registró no puede aprobarla. Una factura
 *       equivocada se anula con motivo y se registra otra.</li>
 *   <li>El pago final solo se registra contra una factura CUADRADA o APROBADA ({@link #exigirPagable}).</li>
 * </ul>
 */
@Service
public class FacturaService {

    private static final Logger log = LoggerFactory.getLogger(FacturaService.class);
    /** Estados de la orden en los que se puede facturar (ya hay recepciones). */
    private static final Set<EstadoOrden> FACTURABLES = EnumSet.of(EstadoOrden.RECIBIDA_PARCIAL,
            EstadoOrden.RECIBIDA_TOTAL, EstadoOrden.CERRADA);
    /** Campos por los que se puede ordenar el listado. */
    private static final Set<String> CAMPOS_ORDENABLES = Set.of("creadoEn", "fechaFactura", "total", "estado");

    private final FacturaProveedorRepository facturaRepository;
    private final OrdenCompraService ordenService;
    private final OrdenCompraRepository ordenRepository;
    private final RecepcionLineaRepository recepcionLineaRepository;
    private final EmpresaRepository empresaRepository;
    private final PagoRepository pagoRepository;
    private final DocumentoService documentoService;
    private final AuditoriaService auditoriaService;
    private final NotificacionService notificacionService;

    /**
     * @param facturaRepository        facturas
     * @param ordenService             apoyo compartido de la orden (bloqueo)
     * @param ordenRepository          órdenes
     * @param recepcionLineaRepository lo aceptado en las recepciones
     * @param empresaRepository        tolerancia configurada de la empresa
     * @param pagoRepository           pagos (para no anular una factura ya pagada)
     * @param documentoService         PDF de la factura
     * @param auditoriaService         registro de acciones
     * @param notificacionService      avisos
     */
    public FacturaService(FacturaProveedorRepository facturaRepository, OrdenCompraService ordenService,
            OrdenCompraRepository ordenRepository, RecepcionLineaRepository recepcionLineaRepository,
            EmpresaRepository empresaRepository, PagoRepository pagoRepository, DocumentoService documentoService,
            AuditoriaService auditoriaService, NotificacionService notificacionService) {
        this.facturaRepository = facturaRepository;
        this.ordenService = ordenService;
        this.ordenRepository = ordenRepository;
        this.recepcionLineaRepository = recepcionLineaRepository;
        this.empresaRepository = empresaRepository;
        this.pagoRepository = pagoRepository;
        this.documentoService = documentoService;
        this.auditoriaService = auditoriaService;
        this.notificacionService = notificacionService;
    }

    /**
     * Registra la factura del proveedor, la cruza con lo recibido y deja el resultado.
     *
     * @param ordenId orden recibida
     * @param req     número, fecha y montos de la factura
     * @return factura CUADRADA o EN_REVISION
     * @throws NoEncontradoException si la orden no existe en la empresa
     * @throws NegocioException      si la orden aún no se recibe, ya tiene factura vigente o el número está repetido
     */
    @Transactional
    public FacturaResponse registrar(Long ordenId, RegistrarFacturaRequest req) {
        OrdenCompra o = ordenService.bloquear(ordenId);
        if (!FACTURABLES.contains(o.getEstado())) {
            throw new NegocioException("ORDEN_NO_FACTURABLE", "La orden " + o.getConsecutivo() + " está " + o.getEstado()
                    + "; solo se factura una orden que ya tiene recepciones");
        }
        if (facturaRepository.findFirstByOrden_ConsecutivoAndEstadoNot(o.getConsecutivo(), EstadoFactura.ANULADA).isPresent()) {
            throw new NegocioException("FACTURA_EXISTENTE", "La orden " + o.getConsecutivo()
                    + " ya tiene una factura vigente; anúlela si se registró por error");
        }
        String numero = req.numero().trim();
        if (facturaRepository.existsByProveedor_IdAndNumeroIgnoreCaseAndEstadoNot(o.getProveedor().getId(), numero,
                EstadoFactura.ANULADA)) {
            throw new NegocioException("FACTURA_NUMERO_DUPLICADO",
                    "El proveedor ya tiene registrada la factura " + numero);
        }

        FacturaProveedor f = new FacturaProveedor();
        f.setOrden(o);
        f.setProveedor(o.getProveedor());
        f.setUsuarioId(SesionActual.usuario().id());
        f.setNumero(numero);
        f.setFechaFactura(req.fechaFactura());
        f.setSubtotal(req.subtotal());
        f.setImpuestos(req.impuestos());
        f.setTotal(req.total());
        cruzar(f, o);
        FacturaProveedor guardada = facturaRepository.save(f);

        if (guardada.getEstado() == EstadoFactura.EN_REVISION) {
            avisarRevision(guardada);
        }
        auditoriaService.exito(AccionAuditoria.FACTURA_REGISTRAR, EntidadAuditada.FACTURA, guardada.getId(),
                detalle(guardada));
        log.info("Factura {} de la orden {} registrada: {}", numero, o.getConsecutivo(), guardada.getEstado());
        return FacturaResponse.of(guardada);
    }

    /**
     * Vuelve a cruzar la factura con lo recibido hoy (útil si llegó más mercancía después de registrarla).
     *
     * @param id factura CUADRADA o EN_REVISION
     * @return factura con el cruce actualizado
     * @throws NegocioException si la factura ya fue aprobada o anulada
     */
    @Transactional
    public FacturaResponse recruzar(Long id) {
        FacturaProveedor f = bloquear(id);
        if (f.getEstado() != EstadoFactura.CUADRADA && f.getEstado() != EstadoFactura.EN_REVISION) {
            throw new NegocioException("FACTURA_NO_CRUZABLE", "La factura " + f.getNumero() + " está " + f.getEstado()
                    + " y no se vuelve a cruzar");
        }
        OrdenCompra o = ordenService.bloquear(f.getOrden().getId());
        EstadoFactura antes = f.getEstado();
        cruzar(f, o);
        if (antes != EstadoFactura.EN_REVISION && f.getEstado() == EstadoFactura.EN_REVISION) {
            avisarRevision(f);
        }
        auditoriaService.exito(AccionAuditoria.FACTURA_RECRUZAR, EntidadAuditada.FACTURA, id,
                antes + " → " + f.getEstado() + "; " + detalle(f));
        return FacturaResponse.of(f);
    }

    /**
     * Aprueba una factura EN_REVISION con un motivo (compras acepta la diferencia). Quien la registró no la aprueba.
     *
     * @param id     factura EN_REVISION
     * @param motivo por qué se acepta la diferencia
     * @return factura APROBADA
     * @throws AccesoDenegadoException si quien aprueba es quien la registró
     * @throws NegocioException        si la factura no está EN_REVISION
     */
    @Transactional
    public FacturaResponse aprobar(Long id, String motivo) {
        FacturaProveedor f = bloquear(id);
        if (f.getEstado() != EstadoFactura.EN_REVISION) {
            throw new NegocioException("FACTURA_NO_EN_REVISION", "La factura " + f.getNumero() + " está " + f.getEstado()
                    + "; solo se aprueba una factura EN_REVISION");
        }
        Long usuario = SesionActual.usuario().id();
        if (f.getUsuarioId().equals(usuario)) {
            throw new AccesoDenegadoException(
                    "No puede aprobar una factura que usted mismo registró (segregación de funciones)");
        }
        f.setEstado(EstadoFactura.APROBADA);
        f.setMotivoAprobacion(motivo.trim());
        f.setAprobadoPorUsuarioId(usuario);
        f.setAprobadoEn(LocalDateTime.now());
        notificacionService.notificar(f.getUsuarioId(), EventoNotificacion.FACTURA_DECIDIDA, "Se aprobó la factura "
                + f.getNumero() + " de la orden " + f.getOrden().getConsecutivo() + " pese a una diferencia de $ "
                + f.getDiferencia().toPlainString() + ": " + f.getMotivoAprobacion());
        auditoriaService.exito(AccionAuditoria.FACTURA_APROBAR, EntidadAuditada.FACTURA, id,
                detalle(f) + "; motivo=" + f.getMotivoAprobacion());
        return FacturaResponse.of(f);
    }

    /**
     * Anula una factura registrada por error. No se anula si la orden ya tiene un pago final registrado.
     *
     * @param id     factura
     * @param motivo por qué se anula
     * @return factura ANULADA
     * @throws NegocioException si ya estaba anulada o la orden tiene un pago final
     */
    @Transactional
    public FacturaResponse anular(Long id, String motivo) {
        FacturaProveedor f = bloquear(id);
        if (f.getEstado() == EstadoFactura.ANULADA) {
            throw new NegocioException("FACTURA_ANULADA", "La factura " + f.getNumero() + " ya está anulada");
        }
        if (pagoRepository.hayPagoFinalRegistrado(f.getOrden().getConsecutivo())) {
            throw new NegocioException("FACTURA_CON_PAGO", "La orden " + f.getOrden().getConsecutivo()
                    + " ya tiene un pago final registrado; anule primero ese pago");
        }
        f.setEstado(EstadoFactura.ANULADA);
        f.setMotivoAnulacion(motivo.trim());
        f.setAnuladoPorUsuarioId(SesionActual.usuario().id());
        f.setAnuladoEn(LocalDateTime.now());
        if (!f.getUsuarioId().equals(f.getAnuladoPorUsuarioId())) {
            notificacionService.notificar(f.getUsuarioId(), EventoNotificacion.FACTURA_DECIDIDA, "Se anuló la factura "
                    + f.getNumero() + " de la orden " + f.getOrden().getConsecutivo() + ": " + f.getMotivoAnulacion());
        }
        auditoriaService.exito(AccionAuditoria.FACTURA_ANULAR, EntidadAuditada.FACTURA, id,
                detalle(f) + "; motivo=" + f.getMotivoAnulacion());
        return FacturaResponse.of(f);
    }

    /**
     * Adjunta el PDF de la factura (una sola vez).
     *
     * @param id      factura no anulada
     * @param archivo PDF o imagen
     * @return factura con su documento
     * @throws NegocioException si está anulada o ya tiene archivo
     */
    @Transactional
    public FacturaResponse adjuntarArchivo(Long id, MultipartFile archivo) {
        FacturaProveedor f = bloquear(id);
        if (f.getEstado() == EstadoFactura.ANULADA) {
            throw new NegocioException("FACTURA_ANULADA", "La factura " + f.getNumero() + " está anulada");
        }
        if (f.getDocumentoId() != null) {
            throw new NegocioException("ARCHIVO_EXISTENTE", "La factura ya tiene archivo; no se reemplaza");
        }
        DocumentoResponse doc = documentoService.subir(EntidadDocumento.FACTURA, id, archivo);
        f.setDocumentoId(doc.id());
        auditoriaService.exito(AccionAuditoria.FACTURA_ARCHIVO, EntidadAuditada.FACTURA, id,
                f.getNumero() + "; documento=" + doc.id());
        return FacturaResponse.of(f);
    }

    /**
     * Verifica que la orden tenga una factura con la que se pueda registrar el pago final y la devuelve. Si la
     * factura estaba CUADRADA pero la recepción cambió y ya no cuadra, falla (hay que volver a cruzarla).
     * Se une a la transacción del pago.
     *
     * @param o orden que se va a pagar
     * @return factura vigente que respalda el pago
     * @throws NegocioException si no hay factura, está EN_REVISION o ya no cuadra con lo recibido
     */
    @Transactional(readOnly = true)
    public FacturaProveedor exigirPagable(OrdenCompra o) {
        FacturaProveedor f = facturaRepository.findFirstByOrden_ConsecutivoAndEstadoNot(o.getConsecutivo(), EstadoFactura.ANULADA)
                .orElseThrow(() -> new NegocioException("FACTURA_REQUERIDA", "La orden " + o.getConsecutivo()
                        + " no tiene factura registrada; el pago final se hace contra la factura"));
        if (!f.getEstado().permitePagoFinal()) {
            throw new NegocioException("FACTURA_EN_REVISION", "La factura " + f.getNumero()
                    + " no cuadra con lo recibido y espera la decisión de compras");
        }
        if (f.getEstado() == EstadoFactura.CUADRADA) {
            CalculadoraFactura.Esperado esperado = esperado(o);
            Empresa e = empresa();
            CalculadoraFactura.Cruce hoy = CalculadoraFactura.evaluar(f.getTotal(), esperado.total(),
                    e.getToleranciaFacturaPct(), e.getToleranciaFacturaMonto());
            if (hoy.estado() != EstadoFactura.CUADRADA) {
                throw new NegocioException("FACTURA_NO_CUADRA", "Desde que se registró la factura " + f.getNumero()
                        + " la recepción cambió y ya no cuadra (esperado $ " + esperado.total().toPlainString()
                        + "); vuelva a cruzarla");
            }
        }
        return f;
    }

    /**
     * Detalle de una factura.
     *
     * @param id factura
     * @return factura
     * @throws NoEncontradoException si no existe en la empresa
     */
    @Transactional(readOnly = true)
    public FacturaResponse obtener(Long id) {
        return FacturaResponse.of(facturaRepository.findDetalleById(id).orElseThrow(() -> new NoEncontradoException("Factura", id)));
    }

    /**
     * Facturas de una orden (todas sus versiones, incluidas las anuladas).
     *
     * @param ordenId cualquier versión de la orden
     * @return facturas, de la más antigua a la más reciente
     * @throws NoEncontradoException si la orden no existe en la empresa
     */
    @Transactional(readOnly = true)
    public List<FacturaResponse> deOrden(Long ordenId) {
        OrdenCompra o = ordenRepository.findById(ordenId).orElseThrow(() -> new NoEncontradoException("Orden", ordenId));
        return facturaRepository.findByOrden_ConsecutivoOrderByCreadoEnAscIdAsc(o.getConsecutivo()).stream()
                .map(FacturaResponse::of).toList();
    }

    /**
     * Facturas de una orden del proveedor de la sesión.
     *
     * @param ordenId cualquier versión de la orden
     * @return facturas de su orden
     * @throws NoEncontradoException si la orden no existe o es de otro proveedor
     */
    @Transactional(readOnly = true)
    public List<FacturaResponse> deOrdenDelProveedor(Long ordenId) {
        UsuarioAutenticado u = SesionActual.usuario();
        OrdenCompra o = ordenRepository.findDetalleById(ordenId)
                .filter(x -> u.esProveedor() && x.getProveedor().getId().equals(u.proveedorId()))
                .orElseThrow(() -> new NoEncontradoException("Orden", ordenId));
        return facturaRepository.findByOrden_ConsecutivoOrderByCreadoEnAscIdAsc(o.getConsecutivo()).stream()
                .map(FacturaResponse::of).toList();
    }

    /**
     * Listado con filtros.
     *
     * @param estado      estado
     * @param proveedorId proveedor
     * @param pageable    página y orden
     * @return página de facturas
     */
    @Transactional(readOnly = true)
    public PageResponse<FacturaResponse> buscar(EstadoFactura estado, Long proveedorId, Pageable pageable) {
        return PageResponse.of(facturaRepository.buscar(estado, proveedorId,
                Ordenamiento.validar(pageable, CAMPOS_ORDENABLES)), FacturaResponse::of);
    }

    // ----------------------------------------------------------------- apoyo

    /**
     * Calcula lo esperado, la diferencia y el estado de la factura con la tolerancia de la empresa.
     *
     * @param f factura (con total fijado)
     * @param o orden con líneas
     */
    private void cruzar(FacturaProveedor f, OrdenCompra o) {
        CalculadoraFactura.Esperado esperado = esperado(o);
        Empresa e = empresa();
        CalculadoraFactura.Cruce cruce = CalculadoraFactura.evaluar(f.getTotal(), esperado.total(),
                e.getToleranciaFacturaPct(), e.getToleranciaFacturaMonto());
        f.setEsperadoSubtotal(esperado.subtotal());
        f.setEsperadoImpuestos(esperado.impuestos());
        f.setEsperadoTotal(esperado.total());
        f.setDiferencia(cruce.diferencia());
        f.setEstado(cruce.estado());
    }

    /**
     * Lo que debería facturar el proveedor según lo aceptado en las recepciones de la orden.
     *
     * @param o orden con líneas
     * @return neto, impuestos y total esperados
     */
    private CalculadoraFactura.Esperado esperado(OrdenCompra o) {
        Map<Long, BigDecimal> aceptado = new HashMap<>();
        for (AgregadoLinea a : recepcionLineaRepository.agregadoPorLinea(o.getId())) {
            aceptado.put(a.getOrdenLineaId(), a.getAceptada());
        }
        return CalculadoraFactura.esperado(o.getLineas().stream().map(l -> new CalculadoraFactura.LineaCruce(
                aceptado.getOrDefault(l.getId(), BigDecimal.ZERO), l.getPrecioUnitario(), l.getImpuestoPct())).toList());
    }

    /**
     * Empresa del contexto (para su tolerancia).
     *
     * @return empresa
     */
    private Empresa empresa() {
        return empresaRepository.findById(EmpresaContexto.requerida())
                .orElseThrow(() -> new NoEncontradoException("Empresa", EmpresaContexto.requerida()));
    }

    /**
     * Bloquea la factura y la carga con su orden y proveedor.
     *
     * @param id factura
     * @return factura bloqueada
     */
    private FacturaProveedor bloquear(Long id) {
        facturaRepository.bloquear(id).orElseThrow(() -> new NoEncontradoException("Factura", id));
        return facturaRepository.findDetalleById(id).orElseThrow(() -> new NoEncontradoException("Factura", id));
    }

    /**
     * Avisa a compras que una factura no cuadra con lo recibido.
     *
     * @param f factura EN_REVISION
     */
    private void avisarRevision(FacturaProveedor f) {
        notificacionService.notificarRol(Roles.ADMIN_COMPRAS, EventoNotificacion.FACTURA_EN_REVISION, "La factura "
                + f.getNumero() + " de " + f.getProveedor().getRazonSocial() + " (orden " + f.getOrden().getConsecutivo()
                + ") no cuadra con lo recibido: facturó $ " + f.getTotal().toPlainString() + " y se esperaba $ "
                + f.getEsperadoTotal().toPlainString());
    }

    /**
     * Resumen para la auditoría.
     *
     * @param f factura
     * @return texto con número, orden, montos y estado
     */
    private static String detalle(FacturaProveedor f) {
        return "factura=" + f.getNumero() + "; orden=" + f.getOrden().getConsecutivo() + " v" + f.getOrden().getVersion()
                + "; total=" + f.getTotal() + "; esperado=" + f.getEsperadoTotal() + "; diferencia=" + f.getDiferencia()
                + "; estado=" + f.getEstado();
    }
}
