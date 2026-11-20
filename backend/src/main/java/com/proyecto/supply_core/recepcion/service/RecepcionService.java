package com.proyecto.supply_core.recepcion.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.proyecto.supply_core.auditoria.enums.AccionAuditoria;
import com.proyecto.supply_core.auditoria.enums.EntidadAuditada;
import com.proyecto.supply_core.auditoria.service.AuditoriaService;
import com.proyecto.supply_core.common.exception.NegocioException;
import com.proyecto.supply_core.common.exception.NoEncontradoException;
import com.proyecto.supply_core.common.exception.ParametroInvalidoException;
import com.proyecto.supply_core.compra.orden.entity.OrdenCompra;
import com.proyecto.supply_core.compra.orden.entity.OrdenCompraLinea;
import com.proyecto.supply_core.compra.orden.enums.EstadoOrden;
import com.proyecto.supply_core.compra.orden.repository.OrdenCompraRepository;
import com.proyecto.supply_core.compra.orden.service.OrdenCompraService;
import com.proyecto.supply_core.inventario.entity.Bodega;
import com.proyecto.supply_core.inventario.repository.BodegaRepository;
import com.proyecto.supply_core.inventario.service.CostoPromedioService;
import com.proyecto.supply_core.inventario.service.MovimientoInventarioService;
import com.proyecto.supply_core.notificacion.enums.EventoNotificacion;
import com.proyecto.supply_core.notificacion.service.NotificacionService;
import com.proyecto.supply_core.proveedor.dto.IndicadoresEvaluacion;
import com.proyecto.supply_core.proveedor.service.EvaluacionProveedorService;
import com.proyecto.supply_core.recepcion.dto.LineaRecepcionRequest;
import com.proyecto.supply_core.recepcion.dto.LineaRecepcionResponse;
import com.proyecto.supply_core.recepcion.dto.RecepcionResponse;
import com.proyecto.supply_core.recepcion.dto.RegistrarRecepcionRequest;
import com.proyecto.supply_core.recepcion.dto.ResumenRecepcionResponse;
import com.proyecto.supply_core.recepcion.entity.Recepcion;
import com.proyecto.supply_core.recepcion.entity.RecepcionLinea;
import com.proyecto.supply_core.recepcion.enums.DecisionExceso;
import com.proyecto.supply_core.recepcion.enums.EstadoRecepcionLinea;
import com.proyecto.supply_core.recepcion.repository.RecepcionLineaRepository;
import com.proyecto.supply_core.recepcion.repository.RecepcionLineaRepository.AgregadoLinea;
import com.proyecto.supply_core.recepcion.repository.RecepcionRepository;
import com.proyecto.supply_core.security.context.Roles;
import com.proyecto.supply_core.security.context.SesionActual;
import com.proyecto.supply_core.security.context.UsuarioAutenticado;
import com.proyecto.supply_core.usuario.repository.UsuarioRepository;

/**
 * UC-13 Recepción de mercancía.
 * <ul>
 *   <li>Se recibe una orden DESPACHADA o ya recibida parcialmente; puede haber varias recepciones (entregas
 *       parciales). Por línea se informa lo recibido y lo rechazado (con motivo); Java reparte lo demás.</li>
 *   <li>Solo lo aceptado (lo bueno que cabe en lo pedido) sube el stock, por
 *       {@code MovimientoInventarioService.registrarEntradaCompra}. Lo rechazado nunca entra: queda como
 *       devolución pendiente al proveedor, con su motivo. El excedente queda EN_REVISION hasta que compras
 *       decida aceptarlo o devolverlo.</li>
 *   <li>La recepción actualiza el costo de referencia del producto con el último precio de la orden.</li>
 *   <li>La orden pasa a RECIBIDA_PARCIAL o RECIBIDA_TOTAL. Al cerrar (CERRADA) se evalúa al proveedor (UC-14).</li>
 * </ul>
 */
@Service
public class RecepcionService {

    private static final Logger log = LoggerFactory.getLogger(RecepcionService.class);
    /** Prefijo del documento de origen de los movimientos de stock. */
    static final String REFERENCIA = "RECEPCION";
    /** Estados de la orden en los que se puede registrar una recepción. */
    private static final Set<EstadoOrden> RECIBIBLES = EnumSet.of(EstadoOrden.DESPACHADA, EstadoOrden.RECIBIDA_PARCIAL);
    /** Estados de la orden desde los que se puede cerrar. */
    private static final Set<EstadoOrden> CERRABLES = EnumSet.of(EstadoOrden.RECIBIDA_PARCIAL, EstadoOrden.RECIBIDA_TOTAL);

    private final RecepcionRepository recepcionRepository;
    private final RecepcionLineaRepository lineaRepository;
    private final OrdenCompraRepository ordenRepository;
    private final OrdenCompraService ordenService;
    private final BodegaRepository bodegaRepository;
    private final MovimientoInventarioService movimientoService;
    private final EvaluacionProveedorService evaluacionService;
    private final UsuarioRepository usuarioRepository;
    private final AuditoriaService auditoriaService;
    private final NotificacionService notificacionService;
    private final CostoPromedioService costoPromedioService;

    /**
     * @param recepcionRepository recepciones
     * @param lineaRepository     líneas de recepción
     * @param ordenRepository     órdenes
     * @param ordenService        apoyo compartido de la orden (bloqueo, historial, transiciones)
     * @param bodegaRepository    bodegas
     * @param movimientoService   único que modifica el stock
     * @param evaluacionService   evaluación del proveedor (UC-14)
     * @param usuarioRepository   usuarios del proveedor (avisos)
     * @param auditoriaService    registro de acciones
     * @param notificacionService avisos
     * @param costoPromedioService costo promedio ponderado (spec 008)
     */
    public RecepcionService(RecepcionRepository recepcionRepository, RecepcionLineaRepository lineaRepository,
            OrdenCompraRepository ordenRepository, OrdenCompraService ordenService, BodegaRepository bodegaRepository,
            MovimientoInventarioService movimientoService, EvaluacionProveedorService evaluacionService,
            UsuarioRepository usuarioRepository, AuditoriaService auditoriaService,
            NotificacionService notificacionService, CostoPromedioService costoPromedioService) {
        this.recepcionRepository = recepcionRepository;
        this.lineaRepository = lineaRepository;
        this.ordenRepository = ordenRepository;
        this.ordenService = ordenService;
        this.bodegaRepository = bodegaRepository;
        this.movimientoService = movimientoService;
        this.evaluacionService = evaluacionService;
        this.usuarioRepository = usuarioRepository;
        this.auditoriaService = auditoriaService;
        this.notificacionService = notificacionService;
        this.costoPromedioService = costoPromedioService;
    }

    /**
     * Registra una recepción de la orden: guarda lo recibido y rechazado, sube el stock con lo aceptado,
     * actualiza el costo de referencia y mueve la orden a RECIBIDA_PARCIAL o RECIBIDA_TOTAL.
     *
     * @param ordenId orden (DESPACHADA o RECIBIDA_PARCIAL)
     * @param req     bodega, observaciones y lo que llegó por línea
     * @return recepción registrada
     * @throws NoEncontradoException si la orden, la bodega o una línea no existen en la empresa
     * @throws NegocioException      si la orden no se puede recibir o la bodega está inactiva
     */
    @Transactional
    public RecepcionResponse registrar(Long ordenId, RegistrarRecepcionRequest req) {
        OrdenCompra o = ordenService.bloquear(ordenId);
        if (!RECIBIBLES.contains(o.getEstado())) {
            throw new NegocioException("ORDEN_NO_RECIBIBLE", "La orden " + o.getConsecutivo() + " está " + o.getEstado()
                    + "; solo se recibe una orden DESPACHADA o recibida parcialmente");
        }
        Bodega bodega = req.bodegaId() == null ? o.getBodegaDestino()
                : bodegaRepository.findById(req.bodegaId()).orElseThrow(() -> new NoEncontradoException("Bodega", req.bodegaId()));
        if (!bodega.isActivo()) {
            throw new NegocioException("BODEGA_INACTIVA", "La bodega " + bodega.getNombre() + " está inactiva");
        }

        Map<Long, OrdenCompraLinea> lineasOrden = o.getLineas().stream()
                .collect(Collectors.toMap(OrdenCompraLinea::getId, l -> l));
        Map<Long, BigDecimal> aceptadoPrevio = new HashMap<>();
        lineaRepository.agregadoPorLinea(ordenId).forEach(a -> aceptadoPrevio.put(a.getOrdenLineaId(), a.getAceptada()));

        Recepcion r = new Recepcion();
        r.setOrden(o);
        r.setBodega(bodega);
        r.setUsuarioId(SesionActual.usuario().id());
        r.setObservaciones(textoOpcional(req.observaciones()));
        for (LineaRecepcionRequest lr : req.lineas()) {
            OrdenCompraLinea ol = lineasOrden.get(lr.ordenLineaId());
            if (ol == null) {
                throw new NoEncontradoException("Línea de la orden", lr.ordenLineaId());
            }
            var reparto = CalculadoraRecepcion.repartir(ol.getCantidad(),
                    aceptadoPrevio.getOrDefault(ol.getId(), BigDecimal.ZERO), lr.cantidadRecibida(), lr.rechazada());
            RecepcionLinea l = new RecepcionLinea();
            l.setOrdenLinea(ol);
            l.setCantidadRecibida(lr.cantidadRecibida());
            l.setCantidadRechazada(lr.rechazada());
            l.setMotivoRechazo(lr.rechazada().signum() > 0 ? lr.motivoRechazo() : null);
            l.setCantidadAceptada(reparto.aceptada());
            l.setCantidadExcedente(reparto.excedente());
            l.setCantidadFaltante(reparto.faltante());
            l.setObservacionCalidad(textoOpcional(lr.observacionCalidad()));
            l.setEstado(reparto.excedente().signum() > 0 ? EstadoRecepcionLinea.EN_REVISION : EstadoRecepcionLinea.ACEPTADA);
            r.agregarLinea(l);
        }
        Recepcion guardada = recepcionRepository.saveAndFlush(r);

        // Solo lo aceptado sube el stock; el costo promedio se recalcula antes de la entrada (spec 008) y el costo de
        // referencia pasa al último precio de la orden
        for (RecepcionLinea l : guardada.getLineas()) {
            if (l.getCantidadAceptada().signum() > 0) {
                var producto = l.getOrdenLinea().getProducto();
                BigDecimal costo = l.getOrdenLinea().getPrecioUnitario();
                costoPromedioService.registrarCompra(producto, l.getCantidadAceptada(), costo);
                movimientoService.registrarEntradaCompra(producto.getId(), bodega.getId(), l.getCantidadAceptada(),
                        costo, REFERENCIA, guardada.getId());
                producto.setCostoReferencia(l.getOrdenLinea().getPrecioUnitario());
            }
        }

        // Estado de la orden según lo aceptado en total
        Map<Long, BigDecimal> total = new HashMap<>(aceptadoPrevio);
        guardada.getLineas().forEach(l -> total.merge(l.getOrdenLinea().getId(), l.getCantidadAceptada(), BigDecimal::add));
        boolean completa = o.getLineas().stream()
                .allMatch(ol -> total.getOrDefault(ol.getId(), BigDecimal.ZERO).compareTo(ol.getCantidad()) >= 0);
        EstadoOrden destino = completa ? EstadoOrden.RECIBIDA_TOTAL : EstadoOrden.RECIBIDA_PARCIAL;
        OrdenCompraService.exigirTransicion(o, destino);
        o.setEstado(destino);
        ordenService.historial(o, destino, "Recepción " + guardada.getId() + " en " + bodega.getNombre());

        String resumen = resumen(guardada);
        avisar(o, EventoNotificacion.RECEPCION_REGISTRADA, "Se recibió la orden " + o.getConsecutivo() + " en "
                + bodega.getNombre() + " (" + destino + "). " + resumen);
        if (guardada.getLineas().stream().anyMatch(l -> l.getEstado() == EstadoRecepcionLinea.EN_REVISION)) {
            notificacionService.notificarRol(Roles.COMPRADOR, EventoNotificacion.RECEPCION_EXCEDENTE, "En la orden "
                    + o.getConsecutivo() + " llegó mercancía de más que espera su decisión (aceptar o devolver)");
        }
        auditoriaService.exito(AccionAuditoria.RECEPCION_REGISTRAR, EntidadAuditada.RECEPCION, guardada.getId(),
                o.getConsecutivo() + " v" + o.getVersion() + " → " + destino + "; " + resumen);
        log.info("Recepción {} de {} registrada: {}", guardada.getId(), o.getConsecutivo(), destino);
        return RecepcionResponse.of(guardada);
    }

    /**
     * Cierra la recepción de la orden (CERRADA) y evalúa al proveedor. Si faltó mercancía exige un motivo.
     *
     * @param ordenId orden (RECIBIDA_PARCIAL o RECIBIDA_TOTAL)
     * @param motivo  obligatorio si hay faltante
     * @return estado final de la recepción de la orden
     * @throws NegocioException           si la orden no se puede cerrar o hay excedente sin resolver
     * @throws ParametroInvalidoException si cierra con faltante sin motivo
     */
    @Transactional
    public ResumenRecepcionResponse cerrar(Long ordenId, String motivo) {
        OrdenCompra o = ordenService.bloquear(ordenId);
        if (!CERRABLES.contains(o.getEstado())) {
            throw new NegocioException("ORDEN_NO_CERRABLE", "La orden " + o.getConsecutivo() + " está " + o.getEstado()
                    + "; solo se cierra una orden con recepciones");
        }
        if (lineaRepository.hayEnRevision(ordenId)) {
            throw new NegocioException("EXCEDENTE_SIN_RESOLVER",
                    "Hay mercancía de más sin resolver; compras debe aceptarla o devolverla antes de cerrar");
        }
        boolean conFaltante = o.getEstado() == EstadoOrden.RECIBIDA_PARCIAL;
        if (conFaltante && (motivo == null || motivo.isBlank())) {
            throw new ParametroInvalidoException("motivo: es obligatorio para cerrar con faltante");
        }
        OrdenCompraService.exigirTransicion(o, EstadoOrden.CERRADA);
        o.setEstado(EstadoOrden.CERRADA);
        String observacion = conFaltante ? "Cierre con faltante: " + motivo.trim() : "Recepción completa";
        ordenService.historial(o, EstadoOrden.CERRADA, observacion);

        List<Recepcion> recepciones = recepcionRepository.findByOrden_IdOrderByRecibidoEnAscIdAsc(ordenId);
        IndicadoresEvaluacion indicadores = CalculadoraRecepcion.indicadores(totales(o, ordenId), o.getFechaEntregaEsperada(),
                recepciones.stream().map(rc -> rc.getRecibidoEn().toLocalDate()).max(LocalDate::compareTo).orElse(null));
        evaluacionService.registrar(o.getProveedor().getId(), o.getId(), indicadores);

        avisar(o, EventoNotificacion.RECEPCION_CERRADA, "Se cerró la recepción de la orden " + o.getConsecutivo()
                + (conFaltante ? " con faltante: " + motivo.trim() : "") + "; el proveedor fue evaluado");
        auditoriaService.exito(AccionAuditoria.RECEPCION_CERRAR, EntidadAuditada.ORDEN_COMPRA, o.getId(),
                o.getConsecutivo() + " v" + o.getVersion() + "; " + observacion + "; score=" + indicadores.score());
        return resumenDe(o, recepciones);
    }

    /**
     * Resuelve el excedente de una línea: aceptarlo (entra al stock) o devolverlo al proveedor.
     *
     * @param lineaId  línea de recepción EN_REVISION
     * @param decision ACEPTAR o DEVOLVER
     * @param motivo   por qué se decidió así
     * @return línea resuelta
     * @throws NoEncontradoException si la línea no existe en la empresa
     * @throws NegocioException      si la línea no está EN_REVISION o la orden ya se cerró
     */
    @Transactional
    public LineaRecepcionResponse resolverExceso(Long lineaId, DecisionExceso decision, String motivo) {
        RecepcionLinea previa = lineaRepository.findDetalleById(lineaId)
                .orElseThrow(() -> new NoEncontradoException("Línea de recepción", lineaId));
        OrdenCompra o = ordenService.bloquear(previa.getRecepcion().getOrden().getId());
        RecepcionLinea l = lineaRepository.findDetalleById(lineaId)
                .orElseThrow(() -> new NoEncontradoException("Línea de recepción", lineaId));
        if (o.getEstado() == EstadoOrden.CERRADA) {
            throw new NegocioException("ORDEN_CERRADA", "La orden " + o.getConsecutivo() + " ya está cerrada");
        }
        if (l.getEstado() != EstadoRecepcionLinea.EN_REVISION) {
            throw new NegocioException("LINEA_NO_EN_REVISION", "La línea no tiene excedente por resolver");
        }
        if (decision == DecisionExceso.ACEPTAR) {
            var producto = l.getOrdenLinea().getProducto();
            BigDecimal costo = l.getOrdenLinea().getPrecioUnitario();
            costoPromedioService.registrarCompra(producto, l.getCantidadExcedente(), costo);
            movimientoService.registrarEntradaCompra(producto.getId(), l.getRecepcion().getBodega().getId(),
                    l.getCantidadExcedente(), costo, REFERENCIA, l.getRecepcion().getId());
            l.setCantidadAceptada(l.getCantidadAceptada().add(l.getCantidadExcedente()));
            l.setEstado(EstadoRecepcionLinea.ACEPTADA);
        } else {
            l.setEstado(EstadoRecepcionLinea.EXCEDENTE_DEVUELTO);
        }
        l.setResueltoPorUsuarioId(SesionActual.usuario().id());
        l.setResueltoEn(LocalDateTime.now());
        l.setMotivoResolucion(motivo == null ? null : motivo.trim());
        auditoriaService.exito(AccionAuditoria.RECEPCION_RESOLVER_EXCESO, EntidadAuditada.RECEPCION,
                l.getRecepcion().getId(), o.getConsecutivo() + "; línea=" + lineaId + "; excedente="
                        + l.getCantidadExcedente() + "; decisión=" + decision + "; motivo=" + l.getMotivoResolucion());
        return LineaRecepcionResponse.of(l);
    }

    /**
     * Estado de la recepción de una orden (uso interno).
     *
     * @param ordenId orden
     * @return avance por línea y recepciones
     * @throws NoEncontradoException si la orden no existe en la empresa
     */
    @Transactional(readOnly = true)
    public ResumenRecepcionResponse deOrden(Long ordenId) {
        OrdenCompra o = ordenRepository.findDetalleById(ordenId).orElseThrow(() -> new NoEncontradoException("Orden", ordenId));
        return resumenDe(o, recepcionRepository.findByOrden_IdOrderByRecibidoEnAscIdAsc(ordenId));
    }

    /**
     * Estado de la recepción de una orden del proveedor de la sesión.
     *
     * @param ordenId orden
     * @return avance por línea y recepciones
     * @throws NoEncontradoException si la orden no existe o es de otro proveedor
     */
    @Transactional(readOnly = true)
    public ResumenRecepcionResponse deOrdenDelProveedor(Long ordenId) {
        UsuarioAutenticado u = SesionActual.usuario();
        OrdenCompra o = ordenRepository.findDetalleById(ordenId)
                .filter(x -> u.esProveedor() && x.getProveedor().getId().equals(u.proveedorId()))
                .orElseThrow(() -> new NoEncontradoException("Orden", ordenId));
        return resumenDe(o, recepcionRepository.findByOrden_IdOrderByRecibidoEnAscIdAsc(ordenId));
    }

    /**
     * Detalle de una recepción.
     *
     * @param id recepción
     * @return recepción con sus líneas
     * @throws NoEncontradoException si no existe en la empresa
     */
    @Transactional(readOnly = true)
    public RecepcionResponse obtener(Long id) {
        return RecepcionResponse.of(recepcionRepository.findDetalleById(id)
                .orElseThrow(() -> new NoEncontradoException("Recepción", id)));
    }

    // ----------------------------------------------------------------- apoyo

    /**
     * Arma el avance por línea y la lista de recepciones de una orden.
     *
     * @param o           orden con líneas
     * @param recepciones recepciones de la orden
     * @return resumen
     */
    private ResumenRecepcionResponse resumenDe(OrdenCompra o, List<Recepcion> recepciones) {
        Map<Long, AgregadoLinea> agregado = lineaRepository.agregadoPorLinea(o.getId()).stream()
                .collect(Collectors.toMap(AgregadoLinea::getOrdenLineaId, a -> a));
        Map<Long, BigDecimal> enRevision = new HashMap<>();
        recepciones.forEach(rc -> rc.getLineas().stream()
                .filter(l -> l.getEstado() == EstadoRecepcionLinea.EN_REVISION)
                .forEach(l -> enRevision.merge(l.getOrdenLinea().getId(), l.getCantidadExcedente(), BigDecimal::add)));
        List<ResumenRecepcionResponse.Avance> avance = new ArrayList<>();
        for (OrdenCompraLinea ol : o.getLineas()) {
            AgregadoLinea a = agregado.get(ol.getId());
            BigDecimal aceptado = a == null ? BigDecimal.ZERO : a.getAceptada();
            BigDecimal rechazado = a == null ? BigDecimal.ZERO : a.getRechazada();
            avance.add(new ResumenRecepcionResponse.Avance(ol.getId(), ol.getProducto().getCodigo(),
                    ol.getProducto().getNombre(), ol.getCantidad(), aceptado, rechazado,
                    ol.getCantidad().subtract(aceptado).max(BigDecimal.ZERO),
                    enRevision.getOrDefault(ol.getId(), BigDecimal.ZERO)));
        }
        return new ResumenRecepcionResponse(o.getId(), o.getConsecutivo(), o.getEstado(), avance,
                recepciones.stream().map(RecepcionResponse::of).toList());
    }

    /**
     * Totales por línea de la orden para evaluar al proveedor.
     *
     * @param o       orden con líneas
     * @param ordenId id de la orden
     * @return totales (las líneas sin recepciones cuentan en cero)
     */
    private List<CalculadoraRecepcion.Totales> totales(OrdenCompra o, Long ordenId) {
        Map<Long, AgregadoLinea> agregado = lineaRepository.agregadoPorLinea(ordenId).stream()
                .collect(Collectors.toMap(AgregadoLinea::getOrdenLineaId, a -> a));
        return o.getLineas().stream().map(ol -> {
            AgregadoLinea a = agregado.get(ol.getId());
            return a == null
                    ? new CalculadoraRecepcion.Totales(ol.getCantidad(), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                            BigDecimal.ZERO)
                    : new CalculadoraRecepcion.Totales(ol.getCantidad(), a.getAceptada(), a.getRecibida(),
                            a.getRechazada(), a.getExcedente());
        }).toList();
    }

    /**
     * Texto corto de lo ocurrido en una recepción (para avisos y auditoría).
     *
     * @param r recepción guardada
     * @return resumen con rechazos, excedentes y faltantes
     */
    private static String resumen(Recepcion r) {
        long conRechazo = r.getLineas().stream().filter(l -> l.getCantidadRechazada().signum() > 0).count();
        long conExceso = r.getLineas().stream().filter(l -> l.getCantidadExcedente().signum() > 0).count();
        long conFaltante = r.getLineas().stream().filter(l -> l.getCantidadFaltante().signum() > 0).count();
        String motivos = r.getLineas().stream().filter(l -> l.getMotivoRechazo() != null)
                .map(l -> l.getOrdenLinea().getProducto().getNombre() + " (" + l.getMotivoRechazo().descripcion() + ")")
                .collect(Collectors.joining(", "));
        return r.getLineas().size() + " línea(s); rechazo en " + conRechazo + (motivos.isEmpty() ? "" : ": " + motivos)
                + "; excedente en " + conExceso + "; aún falta en " + conFaltante;
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

    /**
     * Texto sin espacios extremos, o {@code null} si viene vacío.
     *
     * @param texto texto recibido
     * @return texto limpio o {@code null}
     */
    private static String textoOpcional(String texto) {
        return texto == null || texto.isBlank() ? null : texto.trim();
    }
}
