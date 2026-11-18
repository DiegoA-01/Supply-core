package com.proyecto.supply_core.compra.solicitud.service;

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
import com.proyecto.supply_core.common.util.Ordenamiento;
import com.proyecto.supply_core.compra.solicitud.dto.DecisionRequest;
import com.proyecto.supply_core.compra.solicitud.dto.SolicitudResponse;
import com.proyecto.supply_core.compra.solicitud.dto.SolicitudResumenResponse;
import com.proyecto.supply_core.compra.solicitud.entity.AprobacionSolicitud;
import com.proyecto.supply_core.compra.solicitud.entity.SolicitudCompra;
import com.proyecto.supply_core.compra.solicitud.enums.DecisionAprobacion;
import com.proyecto.supply_core.compra.solicitud.enums.EstadoSolicitud;
import com.proyecto.supply_core.compra.solicitud.repository.AprobacionSolicitudRepository;
import com.proyecto.supply_core.compra.solicitud.repository.SolicitudCompraRepository;
import com.proyecto.supply_core.delegacion.service.DelegacionService;
import com.proyecto.supply_core.empresa.repository.PoliticaAprobacionRepository;
import com.proyecto.supply_core.notificacion.enums.EventoNotificacion;
import com.proyecto.supply_core.notificacion.service.NotificacionService;
import com.proyecto.supply_core.security.context.Roles;
import com.proyecto.supply_core.security.context.SesionActual;
import com.proyecto.supply_core.security.context.UsuarioAutenticado;

/**
 * UC-05 Aprobar solicitud.
 * <p>Decide quien tenga el rol aprobador de la política asignada al enviar, y nunca el propio
 * solicitante (segregación de funciones). La fila se bloquea para que dos aprobadores no decidan
 * a la vez. Cada decisión queda en {@code aprobacion_solicitud}, se audita y se avisa al solicitante
 * (y a COMPRADOR si queda APROBADA).</p>
 */
@Service
public class AprobacionSolicitudService {

    private static final Logger log = LoggerFactory.getLogger(AprobacionSolicitudService.class);
    /** Campos por los que se puede ordenar la bandeja. */
    private static final Set<String> CAMPOS_ORDENABLES = Set.of("enviadoEn", "fechaRequerida", "montoEstimado");

    private final SolicitudService solicitudService;
    private final SolicitudCompraRepository solicitudRepository;
    private final AprobacionSolicitudRepository aprobacionRepository;
    private final PoliticaAprobacionRepository politicaRepository;
    private final AuditoriaService auditoriaService;
    private final NotificacionService notificacionService;
    private final DelegacionService delegaciones;

    /**
     * @param solicitudService     carga y respuesta de solicitudes
     * @param solicitudRepository  solicitudes
     * @param aprobacionRepository historial de decisiones
     * @param politicaRepository   política asignada (rol aprobador)
     * @param auditoriaService     registro de decisiones
     * @param notificacionService  avisos al solicitante y a compras
     * @param delegaciones         autoridad delegada para decidir (18-c)
     */
    public AprobacionSolicitudService(SolicitudService solicitudService, SolicitudCompraRepository solicitudRepository,
            AprobacionSolicitudRepository aprobacionRepository, PoliticaAprobacionRepository politicaRepository,
            AuditoriaService auditoriaService, NotificacionService notificacionService,
            DelegacionService delegaciones) {
        this.delegaciones = delegaciones;
        this.solicitudService = solicitudService;
        this.solicitudRepository = solicitudRepository;
        this.aprobacionRepository = aprobacionRepository;
        this.politicaRepository = politicaRepository;
        this.auditoriaService = auditoriaService;
        this.notificacionService = notificacionService;
    }

    /**
     * Registra la decisión sobre una solicitud PENDIENTE_APROBACION.
     *
     * @param id  id de la solicitud
     * @param req decisión validada (observaciones obligatorias al rechazar o devolver)
     * @return solicitud con su nuevo estado e historial
     * @throws NegocioException        si no está pendiente de aprobación
     * @throws AccesoDenegadoException si es el solicitante o no tiene el rol aprobador de la política ni una
     *                                 delegación vigente que se lo dé
     */
    @Transactional
    public SolicitudResponse decidir(Long id, DecisionRequest req) {
        UsuarioAutenticado sesion = SesionActual.usuario();
        SolicitudCompra s = solicitudService.bloquear(id);
        if (s.getEstado() != EstadoSolicitud.PENDIENTE_APROBACION) {
            throw new NegocioException("TRANSICION_INVALIDA",
                    "La solicitud está " + s.getEstado() + "; solo se decide en PENDIENTE_APROBACION");
        }
        if (s.getUsuarioSolicitanteId().equals(sesion.id())) {
            throw new AccesoDenegadoException("No puede aprobar su propia solicitud (segregación de funciones)");
        }
        String rolAprobador = s.isApruebaDueno() ? Roles.ADMIN_SISTEMA
                : politicaRepository.findWithRolById(s.getPoliticaAprobacionId())
                        .map(p -> p.getRolAprobador().getCodigo())
                        .orElseThrow(() -> new IllegalStateException("Solicitud " + id + " sin política asignada"));
        // con su propio rol o por una delegación vigente (nunca la de quien pidió la solicitud)
        Long delegacionId = delegaciones.autorizar(sesion, rolAprobador, s.getUsuarioSolicitanteId()).orElse(null);

        DecisionAprobacion decision = req.decision();
        String observaciones = req.observaciones() == null || req.observaciones().isBlank()
                ? null : req.observaciones().trim();
        aprobacionRepository.save(new AprobacionSolicitud(id, sesion.id(), s.getPoliticaAprobacionId(), decision,
                observaciones, delegacionId));
        s.setEstado(decision.estadoResultante());

        auditoriaService.exito(AccionAuditoria.SOLICITUD_DECIDIR, EntidadAuditada.SOLICITUD_COMPRA, id,
                s.getConsecutivo() + "; decision=" + decision + (observaciones == null ? "" : "; obs=" + observaciones)
                        + (delegacionId == null ? "" : "; porDelegacion=" + delegacionId));
        notificacionService.notificar(s.getUsuarioSolicitanteId(), EventoNotificacion.SOLICITUD_DECIDIDA,
                "Su solicitud " + s.getConsecutivo() + " fue " + decision
                        + (observaciones == null ? "" : ": " + observaciones));
        if (decision == DecisionAprobacion.APROBADA) {
            notificacionService.notificarRol(Roles.COMPRADOR, EventoNotificacion.SOLICITUD_APROBADA,
                    "La solicitud " + s.getConsecutivo() + " fue aprobada y puede pasar a RFQ");
        }
        log.info("Solicitud {} {} por el usuario {}", s.getConsecutivo(), decision, sesion.id());
        return solicitudService.respuesta(s);
    }

    /**
     * Bandeja del aprobador: solicitudes pendientes cuya política exige alguno de sus roles,
     * sin incluir las propias.
     *
     * @param pageable página y orden
     * @return página de pendientes
     */
    @Transactional(readOnly = true)
    public PageResponse<SolicitudResumenResponse> pendientes(Pageable pageable) {
        UsuarioAutenticado sesion = SesionActual.usuario();
        return PageResponse.of(solicitudRepository.pendientesPara(delegaciones.rolesEfectivos(sesion), sesion.id(),
                Ordenamiento.validar(pageable, CAMPOS_ORDENABLES)), SolicitudResumenResponse::of);
    }
}
