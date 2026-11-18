package com.proyecto.supply_core.compra.adjudicacion.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.proyecto.supply_core.auditoria.enums.AccionAuditoria;
import com.proyecto.supply_core.auditoria.enums.EntidadAuditada;
import com.proyecto.supply_core.auditoria.service.AuditoriaService;
import com.proyecto.supply_core.common.dto.PageResponse;
import com.proyecto.supply_core.common.exception.AccesoDenegadoException;
import com.proyecto.supply_core.common.exception.NegocioException;
import com.proyecto.supply_core.common.exception.NoEncontradoException;
import com.proyecto.supply_core.common.exception.ParametroInvalidoException;
import com.proyecto.supply_core.common.util.Ordenamiento;
import com.proyecto.supply_core.compra.adjudicacion.dto.AdjudicacionResponse;
import com.proyecto.supply_core.compra.adjudicacion.dto.AdjudicarRequest;
import com.proyecto.supply_core.compra.adjudicacion.dto.DecisionAdjudicacionRequest;
import com.proyecto.supply_core.compra.adjudicacion.entity.Adjudicacion;
import com.proyecto.supply_core.compra.adjudicacion.enums.EstadoAdjudicacion;
import com.proyecto.supply_core.compra.adjudicacion.repository.AdjudicacionRepository;
import com.proyecto.supply_core.compra.analisis.entity.Analisis;
import com.proyecto.supply_core.compra.analisis.enums.EstadoAnalisis;
import com.proyecto.supply_core.compra.analisis.repository.AnalisisRepository;
import com.proyecto.supply_core.compra.cotizacion.entity.Cotizacion;
import com.proyecto.supply_core.compra.cotizacion.enums.EstadoCotizacion;
import com.proyecto.supply_core.compra.cotizacion.repository.CotizacionRepository;
import com.proyecto.supply_core.compra.rfq.entity.Rfq;
import com.proyecto.supply_core.compra.rfq.enums.EstadoRfq;
import com.proyecto.supply_core.compra.rfq.repository.RfqRepository;
import com.proyecto.supply_core.compra.solicitud.enums.EstadoSolicitud;
import com.proyecto.supply_core.compra.solicitud.repository.SolicitudCompraRepository;
import com.proyecto.supply_core.empresa.entity.PoliticaAprobacion;
import com.proyecto.supply_core.empresa.enums.TipoPolitica;
import com.proyecto.supply_core.delegacion.service.DelegacionService;
import com.proyecto.supply_core.empresa.repository.PoliticaAprobacionRepository;
import com.proyecto.supply_core.empresa.service.PoliticaAprobacionService;
import com.proyecto.supply_core.notificacion.enums.EventoNotificacion;
import com.proyecto.supply_core.notificacion.service.NotificacionService;
import com.proyecto.supply_core.security.context.SesionActual;
import com.proyecto.supply_core.security.context.UsuarioAutenticado;
import com.proyecto.supply_core.usuario.repository.UsuarioRepository;

/**
 * UC-09 Adjudicar.
 * <ul>
 *   <li>Requiere el análisis PUBLICADO de la RFQ y una cotización que esté en ese ranking.</li>
 *   <li>Si no se elige la recomendada (posición 1), la justificación es obligatoria.</li>
 *   <li>La política ADJUDICACION que cubre el monto decide si hace falta aprobación; sin política, nace
 *       APROBADA.</li>
 *   <li>Al aprobarse: ganadora SELECCIONADA, demás NO_SELECCIONADA, RFQ y solicitud ADJUDICADA, avisos al
 *       comprador y al proveedor ganador. Al rechazarse, la RFQ sigue CERRADA para adjudicar de nuevo.</li>
 *   <li>Nadie aprueba su propia adjudicación; las filas se bloquean para evitar decisiones simultáneas.</li>
 * </ul>
 */
@Service
public class AdjudicacionService {

    private static final Logger log = LoggerFactory.getLogger(AdjudicacionService.class);
    /** Largo mínimo de una justificación útil. */
    static final int JUSTIFICACION_MINIMA = 10;
    /** Estados que impiden crear otra adjudicación para la misma RFQ. */
    private static final Set<EstadoAdjudicacion> VIGENTES = EnumSet.of(EstadoAdjudicacion.PENDIENTE_APROBACION,
            EstadoAdjudicacion.APROBADA);
    /** Campos por los que se puede ordenar la bandeja. */
    private static final Set<String> CAMPOS_ORDENABLES = Set.of("decididoEn", "montoTotal");

    private final AdjudicacionRepository adjudicacionRepository;
    private final AnalisisRepository analisisRepository;
    private final RfqRepository rfqRepository;
    private final SolicitudCompraRepository solicitudRepository;
    private final CotizacionRepository cotizacionRepository;
    private final PoliticaAprobacionService politicaService;
    private final PoliticaAprobacionRepository politicaRepository;
    private final UsuarioRepository usuarioRepository;
    private final AuditoriaService auditoriaService;
    private final NotificacionService notificacionService;
    private final DelegacionService delegaciones;

    /**
     * @param adjudicacionRepository adjudicaciones
     * @param analisisRepository     análisis publicado
     * @param rfqRepository          RFQ
     * @param solicitudRepository    solicitud de origen
     * @param cotizacionRepository   cotizaciones de la RFQ
     * @param politicaService        política ADJUDICACION aplicable
     * @param politicaRepository     rol aprobador de la política
     * @param usuarioRepository      usuarios del proveedor ganador
     * @param auditoriaService       registro de acciones
     * @param notificacionService    avisos
     * @param delegaciones           autoridad delegada para aprobar (18-c)
     */
    public AdjudicacionService(AdjudicacionRepository adjudicacionRepository, AnalisisRepository analisisRepository,
            RfqRepository rfqRepository, SolicitudCompraRepository solicitudRepository,
            CotizacionRepository cotizacionRepository, PoliticaAprobacionService politicaService,
            PoliticaAprobacionRepository politicaRepository, UsuarioRepository usuarioRepository,
            AuditoriaService auditoriaService, NotificacionService notificacionService,
            DelegacionService delegaciones) {
        this.delegaciones = delegaciones;
        this.adjudicacionRepository = adjudicacionRepository;
        this.analisisRepository = analisisRepository;
        this.rfqRepository = rfqRepository;
        this.solicitudRepository = solicitudRepository;
        this.cotizacionRepository = cotizacionRepository;
        this.politicaService = politicaService;
        this.politicaRepository = politicaRepository;
        this.usuarioRepository = usuarioRepository;
        this.auditoriaService = auditoriaService;
        this.notificacionService = notificacionService;
    }

    /**
     * Adjudica la RFQ a una cotización del análisis publicado.
     *
     * @param rfqId RFQ
     * @param req   cotización y justificación
     * @return adjudicación (PENDIENTE_APROBACION o APROBADA)
     * @throws NegocioException           si no hay análisis publicado, ya hay una adjudicación vigente o la
     *                                    cotización no está en el ranking
     * @throws ParametroInvalidoException si falta la justificación al no seguir la recomendación
     */
    @Transactional
    public AdjudicacionResponse adjudicar(Long rfqId, AdjudicarRequest req) {
        rfqRepository.bloquear(rfqId).orElseThrow(() -> new NoEncontradoException("RFQ", rfqId));
        Rfq rfq = rfqRepository.findWithDetalleById(rfqId).orElseThrow(() -> new NoEncontradoException("RFQ", rfqId));
        if (rfq.getEstado() != EstadoRfq.CERRADA) {
            throw new NegocioException("TRANSICION_INVALIDA",
                    "La " + rfq.getConsecutivo() + " está " + rfq.getEstado() + "; solo se adjudica una RFQ CERRADA");
        }
        Analisis analisis = analisisRepository.findFirstByRfq_IdAndEstado(rfqId, EstadoAnalisis.PUBLICADO)
                .orElseThrow(() -> new NegocioException("SIN_ANALISIS_PUBLICADO",
                        "Publique el análisis de la " + rfq.getConsecutivo() + " antes de adjudicar"));
        if (adjudicacionRepository.existsByRfq_IdAndEstadoIn(rfqId, VIGENTES)) {
            throw new NegocioException("ADJUDICACION_EXISTENTE", "La " + rfq.getConsecutivo() + " ya tiene una adjudicación vigente");
        }
        Analisis.Resultado elegido = analisis.getResultados().stream()
                .filter(r -> r.getCotizacion().getId().equals(req.cotizacionId())).findFirst()
                .orElseThrow(() -> new NegocioException("COTIZACION_FUERA_DEL_ANALISIS",
                        "La cotización " + req.cotizacionId() + " no está en el análisis publicado"));
        boolean sigue = elegido.getPosicionRanking() == 1;
        String justificacion = req.justificacion() == null || req.justificacion().isBlank() ? null : req.justificacion().trim();
        if (!sigue && (justificacion == null || justificacion.length() < JUSTIFICACION_MINIMA)) {
            throw new ParametroInvalidoException("justificacion: es obligatoria (mínimo " + JUSTIFICACION_MINIMA
                    + " caracteres) cuando no se elige la cotización recomendada");
        }
        Cotizacion cotizacion = elegido.getCotizacion();
        BigDecimal monto = cotizacion.getTotal() == null ? BigDecimal.ZERO : cotizacion.getTotal();

        Adjudicacion a = new Adjudicacion();
        a.setRfq(rfq);
        a.setAnalisisId(analisis.getId());
        a.setCotizacion(cotizacion);
        a.setUsuarioId(SesionActual.usuario().id());
        a.setMontoTotal(monto);
        a.setSigueRecomendacion(sigue);
        a.setJustificacion(justificacion);
        Optional<PoliticaAprobacion> politica = politicaService.aplicable(TipoPolitica.ADJUDICACION, monto);
        if (politica.isPresent()) {
            a.setPoliticaAprobacionId(politica.get().getId());
            a.setEstado(EstadoAdjudicacion.PENDIENTE_APROBACION);
            adjudicacionRepository.save(a);
            notificacionService.notificarRol(politica.get().getRolAprobador().getCodigo(),
                    EventoNotificacion.ADJUDICACION_PENDIENTE_APROBACION, "La adjudicación de la " + rfq.getConsecutivo()
                            + " a " + cotizacion.getProveedor().getRazonSocial() + " por $ " + monto.toPlainString()
                            + " espera su aprobación");
        } else {
            a.setEstado(EstadoAdjudicacion.APROBADA);
            adjudicacionRepository.save(a);
            aplicarEfectos(a);
        }
        auditoriaService.exito(AccionAuditoria.ADJUDICACION_CREAR, EntidadAuditada.ADJUDICACION, a.getId(),
                rfq.getConsecutivo() + "; cotizacion=" + cotizacion.getId() + "; sigue=" + sigue + "; monto="
                        + monto.toPlainString() + "; estado=" + a.getEstado());
        log.info("{} adjudicada a {} ({})", rfq.getConsecutivo(), cotizacion.getProveedor().getRazonSocial(), a.getEstado());
        return AdjudicacionResponse.of(a);
    }

    /**
     * Aprueba o rechaza una adjudicación pendiente.
     *
     * @param id  adjudicación
     * @param req decisión validada
     * @return adjudicación decidida
     * @throws NegocioException        si no está pendiente
     * @throws AccesoDenegadoException si es quien adjudicó o no tiene el rol de la política
     */
    @Transactional
    public AdjudicacionResponse decidir(Long id, DecisionAdjudicacionRequest req) {
        UsuarioAutenticado sesion = SesionActual.usuario();
        adjudicacionRepository.bloquear(id).orElseThrow(() -> new NoEncontradoException("Adjudicación", id));
        Adjudicacion a = adjudicacionRepository.findDetalleById(id)
                .orElseThrow(() -> new NoEncontradoException("Adjudicación", id));
        if (a.getEstado() != EstadoAdjudicacion.PENDIENTE_APROBACION) {
            throw new NegocioException("TRANSICION_INVALIDA", "La adjudicación está " + a.getEstado());
        }
        if (a.getUsuarioId().equals(sesion.id())) {
            throw new AccesoDenegadoException("No puede aprobar su propia adjudicación (segregación de funciones)");
        }
        String rol = politicaRepository.findWithRolById(a.getPoliticaAprobacionId())
                .map(p -> p.getRolAprobador().getCodigo())
                .orElseThrow(() -> new IllegalStateException("Adjudicación " + id + " sin política"));
        // con su propio rol o por una delegación vigente (nunca la de quien adjudicó)
        Long delegacionId = delegaciones.autorizar(sesion, rol, a.getUsuarioId()).orElse(null);
        a.setAprobadoPorUsuarioId(sesion.id());
        a.setDelegacionId(delegacionId);
        a.setAprobadoEn(LocalDateTime.now());
        a.setObservacionesAprobacion(req.observaciones() == null || req.observaciones().isBlank()
                ? null : req.observaciones().trim());
        if (req.aprobar()) {
            a.setEstado(EstadoAdjudicacion.APROBADA);
            rfqRepository.bloquear(a.getRfq().getId());
            aplicarEfectos(a);
        } else {
            a.setEstado(EstadoAdjudicacion.RECHAZADA);
            notificacionService.notificar(a.getUsuarioId(), EventoNotificacion.ADJUDICACION_DECIDIDA,
                    "La adjudicación de la " + a.getRfq().getConsecutivo() + " fue RECHAZADA: "
                            + a.getObservacionesAprobacion());
        }
        auditoriaService.exito(AccionAuditoria.ADJUDICACION_DECIDIR, EntidadAuditada.ADJUDICACION, id,
                a.getRfq().getConsecutivo() + "; decision=" + a.getEstado()
                        + (a.getObservacionesAprobacion() == null ? "" : "; obs=" + a.getObservacionesAprobacion())
                        + (delegacionId == null ? "" : "; porDelegacion=" + delegacionId));
        return AdjudicacionResponse.of(a);
    }

    /**
     * Última adjudicación de la RFQ.
     *
     * @param rfqId RFQ
     * @return adjudicación
     * @throws NoEncontradoException si no hay
     */
    @Transactional(readOnly = true)
    public AdjudicacionResponse deRfq(Long rfqId) {
        return AdjudicacionResponse.of(adjudicacionRepository.findFirstByRfq_IdOrderByIdDesc(rfqId)
                .orElseThrow(() -> new NoEncontradoException("La RFQ aún no tiene adjudicación")));
    }

    /**
     * Detalle de una adjudicación.
     *
     * @param id adjudicación
     * @return adjudicación
     */
    @Transactional(readOnly = true)
    public AdjudicacionResponse obtener(Long id) {
        return AdjudicacionResponse.of(adjudicacionRepository.findDetalleById(id)
                .orElseThrow(() -> new NoEncontradoException("Adjudicación", id)));
    }

    /**
     * Bandeja del aprobador: adjudicaciones pendientes de su rol, sin las propias.
     *
     * @param pageable página y orden
     * @return pendientes
     */
    @Transactional(readOnly = true)
    public PageResponse<AdjudicacionResponse> pendientes(Pageable pageable) {
        UsuarioAutenticado sesion = SesionActual.usuario();
        return PageResponse.of(adjudicacionRepository.pendientesPara(delegaciones.rolesEfectivos(sesion), sesion.id(),
                Ordenamiento.validar(pageable, CAMPOS_ORDENABLES)), AdjudicacionResponse::of);
    }

    // ----------------------------------------------------------------- apoyo

    /**
     * Efectos de una adjudicación APROBADA: estados de cotizaciones, RFQ y solicitud, y avisos.
     *
     * @param a adjudicación aprobada (RFQ cargada)
     */
    private void aplicarEfectos(Adjudicacion a) {
        Rfq rfq = a.getRfq();
        Long ganadora = a.getCotizacion().getId();
        cotizacionRepository.findByRfq_IdAndEstadoNotOrderByEnviadoEnAsc(rfq.getId(), EstadoCotizacion.BORRADOR)
                .stream()
                .filter(c -> c.getEstado() == EstadoCotizacion.EN_ANALISIS || c.getEstado() == EstadoCotizacion.ENVIADA)
                .forEach(c -> c.setEstado(c.getId().equals(ganadora)
                        ? EstadoCotizacion.SELECCIONADA : EstadoCotizacion.NO_SELECCIONADA));
        rfq.setEstado(EstadoRfq.ADJUDICADA);
        solicitudRepository.bloquear(rfq.getSolicitud().getId()).ifPresent(s -> {
            if (s.getEstado() == EstadoSolicitud.EN_RFQ) {
                s.setEstado(EstadoSolicitud.ADJUDICADA);
            }
        });
        notificacionService.notificar(a.getUsuarioId(), EventoNotificacion.ADJUDICACION_DECIDIDA,
                "La adjudicación de la " + rfq.getConsecutivo() + " a " + a.getCotizacion().getProveedor().getRazonSocial()
                        + " quedó APROBADA; ya puede emitir la orden de compra");
        usuarioRepository.findByProveedorId(a.getCotizacion().getProveedor().getId())
                .forEach(u -> notificacionService.notificar(u.getId(), EventoNotificacion.COTIZACION_SELECCIONADA,
                        "Su cotización para la " + rfq.getConsecutivo() + " fue seleccionada; recibirá la orden de compra"));
    }
}
