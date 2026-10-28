package com.proyecto.supply_core.compra.orden.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.proyecto.supply_core.auditoria.enums.AccionAuditoria;
import com.proyecto.supply_core.auditoria.enums.EntidadAuditada;
import com.proyecto.supply_core.auditoria.service.AuditoriaService;
import com.proyecto.supply_core.catalogo.service.UnidadMedidaService;
import com.proyecto.supply_core.common.dto.PageResponse;
import com.proyecto.supply_core.common.exception.NegocioException;
import com.proyecto.supply_core.common.exception.NoEncontradoException;
import com.proyecto.supply_core.common.exception.ParametroInvalidoException;
import com.proyecto.supply_core.common.util.ConsecutivoGenerator;
import com.proyecto.supply_core.common.util.Ordenamiento;
import com.proyecto.supply_core.compra.adjudicacion.entity.Adjudicacion;
import com.proyecto.supply_core.compra.adjudicacion.enums.EstadoAdjudicacion;
import com.proyecto.supply_core.compra.adjudicacion.repository.AdjudicacionRepository;
import com.proyecto.supply_core.compra.cotizacion.entity.CotizacionLinea;
import com.proyecto.supply_core.compra.orden.dto.EmitirOrdenRequest;
import com.proyecto.supply_core.compra.orden.dto.NuevaVersionRequest;
import com.proyecto.supply_core.compra.orden.dto.OrdenResponse;
import com.proyecto.supply_core.compra.orden.dto.OrdenResumenResponse;
import com.proyecto.supply_core.compra.orden.entity.OrdenCompra;
import com.proyecto.supply_core.compra.orden.entity.OrdenCompraLinea;
import com.proyecto.supply_core.compra.orden.entity.OrdenEstadoHistorial;
import com.proyecto.supply_core.compra.orden.enums.EstadoOrden;
import com.proyecto.supply_core.compra.orden.repository.OrdenCompraRepository;
import com.proyecto.supply_core.compra.orden.repository.OrdenEstadoHistorialRepository;
import com.proyecto.supply_core.compra.solicitud.enums.EstadoSolicitud;
import com.proyecto.supply_core.compra.solicitud.repository.SolicitudCompraRepository;
import com.proyecto.supply_core.inventario.entity.Bodega;
import com.proyecto.supply_core.inventario.repository.BodegaRepository;
import com.proyecto.supply_core.notificacion.enums.EventoNotificacion;
import com.proyecto.supply_core.notificacion.service.NotificacionService;
import com.proyecto.supply_core.pago.repository.PagoRepository;
import com.proyecto.supply_core.security.context.SesionActual;
import com.proyecto.supply_core.usuario.repository.UsuarioRepository;

/**
 * UC-10 Emitir orden de compra (lado de compras): emitir desde una adjudicación APROBADA, crear versiones
 * nuevas y cancelar. Las acciones del proveedor están en {@link OrdenProveedorService}.
 * <ul>
 *   <li>Las líneas salen de la cotización ganadora y se convierten a la unidad del producto (la del
 *       inventario). El neto de cada línea es exactamente el cotizado.</li>
 *   <li>La orden no se edita: un cambio crea la versión siguiente (mismo consecutivo) y la anterior queda
 *       REEMPLAZADA. Los precios nunca cambian; solo cantidades y datos de entrega.</li>
 *   <li>Cada cambio de estado deja historial, auditoría y aviso.</li>
 * </ul>
 */
@Service
public class OrdenCompraService {

    private static final Logger log = LoggerFactory.getLogger(OrdenCompraService.class);
    /** Estados que no cuentan como orden vigente de una adjudicación. */
    private static final Set<EstadoOrden> NO_VIGENTES = EnumSet.of(EstadoOrden.CANCELADA, EstadoOrden.REEMPLAZADA);
    /** Campos por los que se puede ordenar. */
    private static final Set<String> CAMPOS_ORDENABLES = Set.of("creadoEn", "consecutivo", "estado",
            "fechaEntregaEsperada", "total");
    /** Cien. */
    private static final BigDecimal CIEN = BigDecimal.valueOf(100);

    private final OrdenCompraRepository ordenRepository;
    private final OrdenEstadoHistorialRepository historialRepository;
    private final AdjudicacionRepository adjudicacionRepository;
    private final BodegaRepository bodegaRepository;
    private final UnidadMedidaService unidadMedidaService;
    private final UsuarioRepository usuarioRepository;
    private final ConsecutivoGenerator consecutivos;
    private final AuditoriaService auditoriaService;
    private final NotificacionService notificacionService;
    private final PagoRepository pagoRepository;
    private final SolicitudCompraRepository solicitudRepository;

    /**
     * @param ordenRepository        órdenes
     * @param historialRepository    historial de estados
     * @param adjudicacionRepository adjudicación de origen
     * @param bodegaRepository       bodega destino
     * @param unidadMedidaService    conversión a la unidad del producto
     * @param usuarioRepository      usuarios del proveedor
     * @param consecutivos           números OC-000001
     * @param auditoriaService       registro de acciones
     * @param notificacionService    avisos
     * @param pagoRepository         pagos (una versión no puede quedar por debajo de lo pagado)
     * @param solicitudRepository    solicitud de una orden directa (vuelve a APROBADA si se cancela)
     */
    public OrdenCompraService(OrdenCompraRepository ordenRepository, OrdenEstadoHistorialRepository historialRepository,
            AdjudicacionRepository adjudicacionRepository, BodegaRepository bodegaRepository,
            UnidadMedidaService unidadMedidaService, UsuarioRepository usuarioRepository,
            ConsecutivoGenerator consecutivos, AuditoriaService auditoriaService,
            NotificacionService notificacionService, PagoRepository pagoRepository,
            SolicitudCompraRepository solicitudRepository) {
        this.ordenRepository = ordenRepository;
        this.historialRepository = historialRepository;
        this.adjudicacionRepository = adjudicacionRepository;
        this.bodegaRepository = bodegaRepository;
        this.unidadMedidaService = unidadMedidaService;
        this.usuarioRepository = usuarioRepository;
        this.consecutivos = consecutivos;
        this.auditoriaService = auditoriaService;
        this.notificacionService = notificacionService;
        this.pagoRepository = pagoRepository;
        this.solicitudRepository = solicitudRepository;
    }

    /**
     * Emite la orden (versión 1) desde una adjudicación APROBADA.
     *
     * @param req adjudicación, bodega y entrega
     * @return orden EMITIDA
     * @throws NegocioException si la adjudicación no está aprobada, ya tiene orden vigente o la bodega está inactiva
     */
    @Transactional
    public OrdenResponse emitir(EmitirOrdenRequest req) {
        adjudicacionRepository.bloquear(req.adjudicacionId())
                .orElseThrow(() -> new NoEncontradoException("Adjudicación", req.adjudicacionId()));
        Adjudicacion adj = adjudicacionRepository.findDetalleById(req.adjudicacionId())
                .orElseThrow(() -> new NoEncontradoException("Adjudicación", req.adjudicacionId()));
        if (adj.getEstado() != EstadoAdjudicacion.APROBADA) {
            throw new NegocioException("ADJUDICACION_NO_APROBADA", "La adjudicación está " + adj.getEstado()
                    + "; solo se emite orden de una adjudicación APROBADA");
        }
        if (ordenRepository.existsByAdjudicacionIdAndEstadoNotIn(adj.getId(), NO_VIGENTES)) {
            throw new NegocioException("ORDEN_EXISTENTE", "La adjudicación ya tiene una orden vigente");
        }
        OrdenCompra o = new OrdenCompra();
        o.setAdjudicacionId(adj.getId());
        o.setProveedor(adj.getCotizacion().getProveedor());
        o.setBodegaDestino(bodegaActiva(req.bodegaDestinoId()));
        o.setUsuarioId(SesionActual.usuario().id());
        o.setConsecutivo(consecutivos.siguiente(ConsecutivoGenerator.ORDEN_COMPRA));
        o.setLugarEntrega(req.lugarEntrega().trim());
        o.setFechaEntregaEsperada(req.fechaEntregaEsperada());
        for (CotizacionLinea cl : adj.getCotizacion().getLineas()) {
            BigDecimal enUnidadRfq = cl.getCantidadOfertada().multiply(cl.getFactorConversion());
            BigDecimal cantidad = unidadMedidaService.convertir(enUnidadRfq, cl.getRfqLinea().getUnidadMedida().getId(),
                    cl.getProducto().getUnidadMedida().getId());
            OrdenCompraLinea l = new OrdenCompraLinea();
            l.setCotizacionLineaId(cl.getId());
            l.setProducto(cl.getProducto());
            l.setCantidad(cantidad);
            l.setSubtotal(cl.getSubtotal());
            l.setPrecioUnitario(cl.getSubtotal().divide(cantidad, 4, RoundingMode.HALF_UP));
            l.setImpuestoPct(cl.getImpuestoPct());
            o.agregarLinea(l);
        }
        if (o.getLineas().isEmpty()) {
            throw new NegocioException("ORDEN_SIN_LINEAS", "La cotización adjudicada no tiene precios");
        }
        totalizar(o);
        OrdenCompra guardada = ordenRepository.save(o);
        historial(guardada, EstadoOrden.EMITIDA, null);
        avisarProveedor(guardada, EventoNotificacion.ORDEN_EMITIDA, "Recibió la orden de compra " + guardada.getConsecutivo()
                + " por $ " + guardada.getTotal().toPlainString() + " con entrega el " + guardada.getFechaEntregaEsperada());
        auditoriaService.exito(AccionAuditoria.ORDEN_EMITIR, EntidadAuditada.ORDEN_COMPRA, guardada.getId(),
                guardada.getConsecutivo() + " v1; adjudicacion=" + adj.getId() + "; total=" + guardada.getTotal());
        log.info("Orden {} emitida a {} por {}", guardada.getConsecutivo(), guardada.getProveedor().getRazonSocial(),
                guardada.getTotal());
        return respuesta(guardada);
    }

    /**
     * Crea la versión siguiente con los cambios (la actual queda REEMPLAZADA). El proveedor debe aceptarla de nuevo.
     *
     * @param id  versión actual
     * @param req motivo y cambios
     * @return versión nueva (EMITIDA)
     * @throws NegocioException si el estado ya no permite cambios, la orden quedaría sin líneas o su total
     *                          quedaría por debajo de lo ya pagado
     */
    @Transactional
    public OrdenResponse nuevaVersion(Long id, NuevaVersionRequest req) {
        OrdenCompra actual = bloquear(id);
        exigirTransicion(actual, EstadoOrden.REEMPLAZADA);
        Map<Long, BigDecimal> cambios = req.cantidades() == null ? Map.of()
                : req.cantidades().stream().collect(Collectors.toMap(NuevaVersionRequest.CambioCantidad::lineaId,
                        NuevaVersionRequest.CambioCantidad::cantidad));
        Set<Long> lineasActuales = actual.getLineas().stream().map(OrdenCompraLinea::getId).collect(Collectors.toSet());
        Set<Long> desconocidas = new HashSet<>(cambios.keySet());
        desconocidas.removeAll(lineasActuales);
        if (!desconocidas.isEmpty()) {
            throw new ParametroInvalidoException("cantidades: las líneas " + desconocidas + " no son de esta orden");
        }

        OrdenCompra nueva = new OrdenCompra();
        nueva.setAdjudicacionId(actual.getAdjudicacionId());
        nueva.setContratoId(actual.getContratoId());
        nueva.setSolicitudId(actual.getSolicitudId());
        nueva.setProveedor(actual.getProveedor());
        nueva.setBodegaDestino(req.bodegaDestinoId() == null ? actual.getBodegaDestino() : bodegaActiva(req.bodegaDestinoId()));
        nueva.setUsuarioId(SesionActual.usuario().id());
        nueva.setConsecutivo(actual.getConsecutivo());
        nueva.setVersion(actual.getVersion() + 1);
        nueva.setOrdenPadreId(actual.getId());
        nueva.setMotivoVersion(req.motivo().trim());
        nueva.setLugarEntrega(req.lugarEntrega() == null || req.lugarEntrega().isBlank()
                ? actual.getLugarEntrega() : req.lugarEntrega().trim());
        nueva.setFechaEntregaEsperada(req.fechaEntregaEsperada() == null
                ? actual.getFechaEntregaEsperada() : req.fechaEntregaEsperada());
        for (OrdenCompraLinea l : actual.getLineas()) {
            BigDecimal cantidad = cambios.getOrDefault(l.getId(), l.getCantidad());
            if (cantidad.signum() == 0) {
                continue; // línea quitada
            }
            if (l.getContratoPrecioId() != null && cantidad.compareTo(l.getCantidad()) > 0) {
                throw new NegocioException("CONTRATO_TOPE_EXCEDIDO", "La línea de " + l.getProducto().getNombre()
                        + " viene de un contrato: su cantidad solo puede bajar de " + l.getCantidad().toPlainString()
                        + "; para pedir más, emita una orden nueva y se revisa el tope");
            }
            OrdenCompraLinea copia = new OrdenCompraLinea();
            copia.setContratoPrecioId(l.getContratoPrecioId());
            copia.setCotizacionLineaId(l.getCotizacionLineaId());
            copia.setProducto(l.getProducto());
            copia.setCantidad(cantidad);
            copia.setPrecioUnitario(l.getPrecioUnitario());
            copia.setImpuestoPct(l.getImpuestoPct());
            copia.setSubtotal(cantidad.compareTo(l.getCantidad()) == 0 ? l.getSubtotal()
                    : cantidad.multiply(l.getPrecioUnitario()).setScale(2, RoundingMode.HALF_UP));
            nueva.agregarLinea(copia);
        }
        if (nueva.getLineas().isEmpty()) {
            throw new NegocioException("ORDEN_SIN_LINEAS", "La nueva versión debe tener al menos una línea; para anular use cancelar");
        }
        totalizar(nueva);
        BigDecimal pagado = pagoRepository.pagadoPorConsecutivo(actual.getConsecutivo());
        if (nueva.getTotal().compareTo(pagado) < 0) {
            throw new NegocioException("ORDEN_TOTAL_MENOR_PAGADO", "La nueva versión quedaría en $ "
                    + nueva.getTotal().toPlainString() + " y ya se pagaron $ " + pagado.toPlainString()
                    + "; anule los pagos que sobran antes de reducirla");
        }
        actual.setEstado(EstadoOrden.REEMPLAZADA);
        historial(actual, EstadoOrden.REEMPLAZADA, "Reemplazada por la versión " + nueva.getVersion() + ": " + nueva.getMotivoVersion());
        OrdenCompra guardada = ordenRepository.save(nueva);
        historial(guardada, EstadoOrden.EMITIDA, nueva.getMotivoVersion());
        avisarProveedor(guardada, EventoNotificacion.ORDEN_ACTUALIZADA, "La orden " + guardada.getConsecutivo()
                + " cambió a la versión " + guardada.getVersion() + " (" + guardada.getMotivoVersion() + "); revísela y acéptela");
        auditoriaService.exito(AccionAuditoria.ORDEN_VERSIONAR, EntidadAuditada.ORDEN_COMPRA, guardada.getId(),
                guardada.getConsecutivo() + " v" + guardada.getVersion() + "; anterior=" + actual.getId() + "; motivo="
                        + guardada.getMotivoVersion() + "; total=" + guardada.getTotal());
        return respuesta(guardada);
    }

    /**
     * Cancela la orden (antes del despacho).
     *
     * @param id     orden
     * @param motivo motivo obligatorio
     * @return orden CANCELADA
     * @throws ParametroInvalidoException si falta el motivo
     * @throws NegocioException           si ya se despachó o terminó
     */
    @Transactional
    public OrdenResponse cancelar(Long id, String motivo) {
        String texto = obligatorio(motivo);
        OrdenCompra o = bloquear(id);
        exigirTransicion(o, EstadoOrden.CANCELADA);
        o.setEstado(EstadoOrden.CANCELADA);
        if (o.getAdjudicacionId() == null && o.getSolicitudId() != null) {
            // orden directa de contrato: la solicitud vuelve a estar lista para otra compra
            solicitudRepository.findById(o.getSolicitudId()).filter(s -> s.getEstado() == EstadoSolicitud.ADJUDICADA)
                    .ifPresent(s -> s.setEstado(EstadoSolicitud.APROBADA));
        }
        historial(o, EstadoOrden.CANCELADA, texto);
        avisarProveedor(o, EventoNotificacion.ORDEN_ACTUALIZADA, "La orden " + o.getConsecutivo() + " fue CANCELADA: " + texto);
        auditoriaService.exito(AccionAuditoria.ORDEN_CANCELAR, EntidadAuditada.ORDEN_COMPRA, id,
                o.getConsecutivo() + " v" + o.getVersion() + "; motivo=" + texto);
        return respuesta(o);
    }

    /**
     * Detalle con líneas e historial.
     *
     * @param id orden
     * @return orden
     */
    @Transactional(readOnly = true)
    public OrdenResponse obtener(Long id) {
        return respuesta(ordenRepository.findDetalleById(id).orElseThrow(() -> new NoEncontradoException("Orden", id)));
    }

    /**
     * Listado con filtros (sin versiones reemplazadas, salvo que se pidan).
     *
     * @param texto       coincidencia en el consecutivo
     * @param estado      estado
     * @param proveedorId proveedor
     * @param pageable    página y orden
     * @return página de órdenes
     */
    @Transactional(readOnly = true)
    public PageResponse<OrdenResumenResponse> buscar(String texto, EstadoOrden estado, Long proveedorId,
            Pageable pageable) {
        String filtro = texto == null || texto.isBlank() ? null : texto.trim().toUpperCase();
        return PageResponse.of(ordenRepository.buscar(filtro, estado, proveedorId,
                Ordenamiento.validar(pageable, CAMPOS_ORDENABLES)), OrdenResumenResponse::of);
    }

    // ----------------------------------------------------------------- apoyo compartido con el proveedor

    /**
     * Bloquea la orden y la carga con su detalle.
     *
     * @param id orden
     * @return orden bloqueada
     */
    public OrdenCompra bloquear(Long id) {
        ordenRepository.bloquear(id).orElseThrow(() -> new NoEncontradoException("Orden", id));
        return ordenRepository.findDetalleById(id).orElseThrow(() -> new NoEncontradoException("Orden", id));
    }

    /**
     * Verifica que la transición esté permitida.
     *
     * @param o       orden
     * @param destino estado destino
     * @throws NegocioException si no está permitida
     */
    public static void exigirTransicion(OrdenCompra o, EstadoOrden destino) {
        if (!o.getEstado().puedePasarA(destino)) {
            throw new NegocioException("TRANSICION_INVALIDA", "La orden " + o.getConsecutivo() + " v" + o.getVersion()
                    + " está " + o.getEstado() + " y no puede pasar a " + destino);
        }
    }

    /**
     * Guarda un cambio de estado en el historial (usuario de la sesión).
     *
     * @param o           orden
     * @param estado      estado nuevo
     * @param observacion comentario
     */
    public void historial(OrdenCompra o, EstadoOrden estado, String observacion) {
        historialRepository.save(new OrdenEstadoHistorial(o.getId(), estado, SesionActual.usuario().id(), observacion));
    }

    /**
     * Respuesta con historial.
     *
     * @param o orden con detalle
     * @return DTO
     */
    OrdenResponse respuesta(OrdenCompra o) {
        return OrdenResponse.of(o, historialRepository.findByOrdenIdOrderByCreadoEnAscIdAsc(o.getId()));
    }

    /**
     * Avisa a los usuarios del proveedor de la orden.
     *
     * @param o       orden
     * @param evento  evento
     * @param mensaje texto
     */
    void avisarProveedor(OrdenCompra o, EventoNotificacion evento, String mensaje) {
        usuarioRepository.findByProveedorId(o.getProveedor().getId())
                .forEach(u -> notificacionService.notificar(u.getId(), evento, mensaje));
    }

    /**
     * Texto obligatorio.
     *
     * @param texto texto recibido
     * @return texto sin espacios extremos
     * @throws ParametroInvalidoException si está vacío
     */
    static String obligatorio(String texto) {
        if (texto == null || texto.isBlank()) {
            throw new ParametroInvalidoException("observacion: es obligatoria para esta acción");
        }
        return texto.trim();
    }

    // ----------------------------------------------------------------- apoyo

    /**
     * Subtotal, impuestos (por línea, 2 decimales) y total.
     *
     * @param o orden con líneas
     */
    static void totalizar(OrdenCompra o) {
        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal impuestos = BigDecimal.ZERO;
        for (OrdenCompraLinea l : o.getLineas()) {
            subtotal = subtotal.add(l.getSubtotal());
            impuestos = impuestos.add(l.getSubtotal().multiply(l.getImpuestoPct()).divide(CIEN, 2, RoundingMode.HALF_UP));
        }
        o.setSubtotal(subtotal);
        o.setImpuestos(impuestos);
        o.setTotal(subtotal.add(impuestos));
    }

    /**
     * Bodega existente y activa.
     *
     * @param id bodega
     * @return bodega
     */
    private Bodega bodegaActiva(Long id) {
        Bodega b = bodegaRepository.findById(id).orElseThrow(() -> new NoEncontradoException("Bodega", id));
        if (!b.isActivo()) {
            throw new NegocioException("BODEGA_INACTIVA", "La bodega " + b.getNombre() + " está inactiva");
        }
        return b;
    }
}
