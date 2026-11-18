package com.proyecto.supply_core.compra.solicitud.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.proyecto.supply_core.auditoria.enums.AccionAuditoria;
import com.proyecto.supply_core.auditoria.enums.EntidadAuditada;
import com.proyecto.supply_core.auditoria.service.AuditoriaService;
import com.proyecto.supply_core.common.tenant.EmpresaContexto;
import com.proyecto.supply_core.compra.solicitud.entity.SolicitudCompra;
import com.proyecto.supply_core.compra.solicitud.enums.EstadoSolicitud;
import com.proyecto.supply_core.compra.solicitud.repository.SolicitudCompraRepository;
import com.proyecto.supply_core.delegacion.service.DelegacionService;
import com.proyecto.supply_core.empresa.entity.Empresa;
import com.proyecto.supply_core.empresa.entity.PoliticaAprobacion;
import com.proyecto.supply_core.empresa.enums.TipoPolitica;
import com.proyecto.supply_core.empresa.repository.EmpresaRepository;
import com.proyecto.supply_core.empresa.repository.PoliticaAprobacionRepository;
import com.proyecto.supply_core.empresa.service.PoliticaAprobacionService;
import com.proyecto.supply_core.notificacion.enums.EventoNotificacion;
import com.proyecto.supply_core.notificacion.service.NotificacionService;
import com.proyecto.supply_core.security.context.Roles;

/**
 * SLA de aprobación de solicitudes (paso 18-c). Cada empresa define cuántas horas tiene una solicitud
 * PENDIENTE_APROBACION para decidirse ({@code empresa.sla_aprobacion_horas}, 0 = sin SLA). Las horas corren
 * desde el envío y son corridas, no hábiles.
 * <ul>
 *   <li><b>Al 80 %</b> se avisa una vez a los aprobadores de la política y a quienes tienen su delegación.</li>
 *   <li><b>Al vencer</b> se escala una vez: la solicitud pasa a la política de mayor tramo y se avisa a ese rol, a
 *       sus delegados y al solicitante. Si ya estaba en la de mayor tramo, solo se avisa, también a
 *       ADMIN_COMPRAS. El estado sigue PENDIENTE_APROBACION.</li>
 *   <li>Reenviar una solicitud devuelta reinicia el reloj y las marcas.</li>
 *   <li>Escalar no salta la segregación de funciones: quien pidió la solicitud sigue sin poder decidirla.</li>
 * </ul>
 * <p>Corre dentro del contexto de una empresa; el recorrido de todas las empresas lo hace {@code SlaAprobacionJob}.</p>
 */
@Service
public class SlaAprobacionService {

    private static final Logger log = LoggerFactory.getLogger(SlaAprobacionService.class);
    /** Porcentaje del SLA en que se avisa. */
    static final int PCT_AVISO = 80;

    private final SolicitudCompraRepository solicitudRepository;
    private final EmpresaRepository empresaRepository;
    private final PoliticaAprobacionService politicaService;
    private final PoliticaAprobacionRepository politicaRepository;
    private final DelegacionService delegaciones;
    private final NotificacionService notificacionService;
    private final AuditoriaService auditoriaService;

    /**
     * @param solicitudRepository solicitudes pendientes
     * @param empresaRepository   horas del SLA de la empresa
     * @param politicaService     política de mayor tramo
     * @param politicaRepository  rol aprobador de la política asignada
     * @param delegaciones        delegados a quienes también se avisa
     * @param notificacionService avisos
     * @param auditoriaService    registro de escalamientos
     */
    public SlaAprobacionService(SolicitudCompraRepository solicitudRepository, EmpresaRepository empresaRepository,
            PoliticaAprobacionService politicaService, PoliticaAprobacionRepository politicaRepository,
            DelegacionService delegaciones, NotificacionService notificacionService,
            AuditoriaService auditoriaService) {
        this.solicitudRepository = solicitudRepository;
        this.empresaRepository = empresaRepository;
        this.politicaService = politicaService;
        this.politicaRepository = politicaRepository;
        this.delegaciones = delegaciones;
        this.notificacionService = notificacionService;
        this.auditoriaService = auditoriaService;
    }

    /**
     * Resultado de una pasada del SLA.
     *
     * @param avisadas  solicitudes a las que se avisó que el SLA va por el 80 %
     * @param escaladas solicitudes que vencieron y se escalaron
     */
    public record Resultado(int avisadas, int escaladas) {
    }

    /**
     * Revisa las solicitudes pendientes de la empresa del contexto: avisa las que van por el 80 % del SLA y escala
     * las vencidas.
     *
     * @param ahora momento contra el que se mide el tiempo
     * @return cuántas se avisaron y cuántas se escalaron
     */
    @Transactional
    public Resultado procesar(LocalDateTime ahora) {
        Empresa empresa = empresaRepository.findById(EmpresaContexto.requerida()).orElse(null);
        if (empresa == null || empresa.getSlaAprobacionHoras() <= 0) {
            return new Resultado(0, 0);
        }
        long slaMinutos = empresa.getSlaAprobacionHoras() * 60L;
        LocalDateTime corteAviso = ahora.minusMinutes(slaMinutos * PCT_AVISO / 100);
        int avisadas = 0;
        int escaladas = 0;
        for (Long id : solicitudRepository.idsPendientesDesde(corteAviso)) {
            // se relee con bloqueo: pudo decidirse o escalarse mientras tanto
            SolicitudCompra s = solicitudRepository.bloquear(id).orElse(null);
            if (s == null || s.getEstado() != EstadoSolicitud.PENDIENTE_APROBACION || s.getEscaladaEn() != null
                    || s.getEnviadoEn() == null) {
                continue;
            }
            long transcurridos = Duration.between(s.getEnviadoEn(), ahora).toMinutes();
            if (transcurridos >= slaMinutos) {
                escalar(s, ahora, empresa.getSlaAprobacionHoras());
                escaladas++;
            } else if (s.getSlaAvisoEn() == null && transcurridos >= slaMinutos * PCT_AVISO / 100) {
                avisar(s, ahora, empresa.getSlaAprobacionHoras());
                avisadas++;
            }
        }
        return new Resultado(avisadas, escaladas);
    }

    // ----------------------------------------------------------------- apoyo

    /**
     * Avisa a los aprobadores (y a sus delegados) que la solicitud va por el 80 % del SLA.
     *
     * @param s     solicitud pendiente
     * @param ahora momento del aviso
     * @param horas horas del SLA
     */
    private void avisar(SolicitudCompra s, LocalDateTime ahora, int horas) {
        Optional<String> rol = s.isApruebaDueno() ? Optional.of(Roles.ADMIN_SISTEMA) : rolDe(s.getPoliticaAprobacionId());
        if (rol.isEmpty()) {
            return;
        }
        String mensaje = "La solicitud " + s.getConsecutivo() + " lleva más del " + PCT_AVISO + " % de las " + horas
                + " h para decidirse; si no se decide, se escalará";
        notificarRolYDelegados(rol.get(), EventoNotificacion.SOLICITUD_SLA_POR_VENCER, mensaje);
        s.setSlaAvisoEn(ahora);
        log.info("SLA: aviso de la solicitud {} al rol {}", s.getConsecutivo(), rol.get());
    }

    /**
     * Escala la solicitud a la política de mayor tramo y avisa.
     *
     * @param s     solicitud vencida
     * @param ahora momento del escalamiento
     * @param horas horas del SLA
     */
    private void escalar(SolicitudCompra s, LocalDateTime ahora, int horas) {
        Long anterior = s.getPoliticaAprobacionId();
        // la que decide el dueño ya está en el nivel más alto: no cambia de política
        Optional<PoliticaAprobacion> mayor = s.isApruebaDueno() ? Optional.empty()
                : politicaService.deMayorTramo(TipoPolitica.SOLICITUD);
        boolean cambia = mayor.isPresent() && !mayor.get().getId().equals(anterior);
        String rolDestino;
        if (cambia) {
            s.setPoliticaAprobacionId(mayor.get().getId());
            rolDestino = mayor.get().getRolAprobador().getCodigo();
        } else {
            rolDestino = s.isApruebaDueno() ? Roles.ADMIN_SISTEMA : rolDe(anterior).orElse(Roles.ADMIN_COMPRAS);
        }
        String mensaje = "La solicitud " + s.getConsecutivo() + " superó las " + horas + " h sin decidirse"
                + (cambia ? " y pasó a la aprobación del rol " + rolDestino : " y ya está en el nivel más alto");
        notificarRolYDelegados(rolDestino, EventoNotificacion.SOLICITUD_ESCALADA, mensaje);
        if (!cambia && !Roles.ADMIN_COMPRAS.equals(rolDestino)) {
            notificacionService.notificarRol(Roles.ADMIN_COMPRAS, EventoNotificacion.SOLICITUD_ESCALADA, mensaje);
        }
        notificacionService.notificar(s.getUsuarioSolicitanteId(), EventoNotificacion.SOLICITUD_ESCALADA, mensaje);
        s.setEscaladaEn(ahora);
        if (s.getSlaAvisoEn() == null) {
            s.setSlaAvisoEn(ahora);
        }
        auditoriaService.exito(AccionAuditoria.SOLICITUD_ESCALAR, EntidadAuditada.SOLICITUD_COMPRA, s.getId(),
                s.getConsecutivo() + "; sla=" + horas + " h; politica " + anterior + " -> " + s.getPoliticaAprobacionId()
                        + "; rol=" + rolDestino);
        log.info("SLA: solicitud {} escalada al rol {}", s.getConsecutivo(), rolDestino);
    }

    /**
     * Código del rol aprobador de una política.
     *
     * @param politicaId política
     * @return rol, vacío si la política ya no existe
     */
    private Optional<String> rolDe(Long politicaId) {
        if (politicaId == null) {
            return Optional.empty();
        }
        return politicaRepository.findWithRolById(politicaId).map(p -> p.getRolAprobador().getCodigo());
    }

    /**
     * Avisa a los usuarios del rol y a quienes lo ejercen hoy por delegación.
     *
     * @param rol     código del rol
     * @param evento  evento
     * @param mensaje texto
     */
    private void notificarRolYDelegados(String rol, EventoNotificacion evento, String mensaje) {
        notificacionService.notificarRol(rol, evento, mensaje);
        List<Long> delegados = List.copyOf(delegaciones.delegadosDelRol(rol));
        delegados.forEach(u -> notificacionService.notificar(u, evento, mensaje));
    }
}
