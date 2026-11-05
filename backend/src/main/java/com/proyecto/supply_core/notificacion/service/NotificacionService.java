package com.proyecto.supply_core.notificacion.service;

import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.proyecto.supply_core.auditoria.enums.AccionAuditoria;
import com.proyecto.supply_core.auditoria.enums.EntidadAuditada;
import com.proyecto.supply_core.auditoria.service.AuditoriaService;
import com.proyecto.supply_core.common.dto.PageResponse;
import com.proyecto.supply_core.common.exception.NoEncontradoException;
import com.proyecto.supply_core.common.util.Ordenamiento;
import com.proyecto.supply_core.notificacion.dto.CantidadResponse;
import com.proyecto.supply_core.notificacion.dto.ConfigNotificacionResponse;
import com.proyecto.supply_core.notificacion.dto.NotificacionResponse;
import com.proyecto.supply_core.notificacion.email.EmailClient;
import com.proyecto.supply_core.notificacion.entity.ConfigNotificacion;
import com.proyecto.supply_core.notificacion.entity.Notificacion;
import com.proyecto.supply_core.notificacion.enums.CanalNotificacion;
import com.proyecto.supply_core.notificacion.enums.EventoNotificacion;
import com.proyecto.supply_core.notificacion.repository.ConfigNotificacionRepository;
import com.proyecto.supply_core.notificacion.repository.NotificacionRepository;
import com.proyecto.supply_core.security.context.SesionActual;
import com.proyecto.supply_core.usuario.entity.Usuario;
import com.proyecto.supply_core.usuario.repository.UsuarioRepository;

/**
 * Notificaciones internas y por correo.
 * <p>Cualquier Service avisa con {@link #notificar} (a un usuario) o {@link #notificarRol} (a todos
 * los usuarios activos de un rol). Cada canal se respeta según {@code config_notificacion}
 * (sin fila = activo). La notificación interna se guarda en la misma transacción de la operación;
 * el correo se envía <b>después del commit</b> (si la operación se revierte no sale ningún correo)
 * y un fallo del correo nunca rompe la operación.</p>
 */
@Service
public class NotificacionService {

    private static final Logger log = LoggerFactory.getLogger(NotificacionService.class);
    /** Largo máximo de {@code notificacion.mensaje}. */
    static final int MAX_MENSAJE = 500;
    /** Campos por los que se puede ordenar la bandeja. */
    private static final Set<String> CAMPOS_ORDENABLES = Set.of("creadoEn");

    private final NotificacionRepository notificacionRepository;
    private final ConfigNotificacionRepository configRepository;
    private final UsuarioRepository usuarioRepository;
    private final EmailClient emailClient;
    private final AuditoriaService auditoriaService;

    /**
     * @param notificacionRepository bandeja de notificaciones
     * @param configRepository       canales activos por evento
     * @param usuarioRepository      destinatarios
     * @param emailClient            envío de correos
     * @param auditoriaService       registro de cambios de configuración
     */
    public NotificacionService(NotificacionRepository notificacionRepository,
            ConfigNotificacionRepository configRepository, UsuarioRepository usuarioRepository,
            EmailClient emailClient, AuditoriaService auditoriaService) {
        this.notificacionRepository = notificacionRepository;
        this.configRepository = configRepository;
        this.usuarioRepository = usuarioRepository;
        this.emailClient = emailClient;
        this.auditoriaService = auditoriaService;
    }

    /**
     * Notifica a un usuario. Si el usuario no existe o está inactivo no hace nada (solo log).
     *
     * @param usuarioId destinatario
     * @param evento    evento
     * @param mensaje   texto (se recorta a 500 caracteres)
     */
    @Transactional
    public void notificar(Long usuarioId, EventoNotificacion evento, String mensaje) {
        usuarioRepository.findById(usuarioId).filter(Usuario::isActivo).ifPresentOrElse(
                u -> entregar(List.of(u), evento, mensaje),
                () -> log.warn("Notificación {} descartada: usuario {} inexistente o inactivo", evento, usuarioId));
    }

    /**
     * Notifica a todos los usuarios activos que tienen el rol.
     *
     * @param codigoRol código del rol (constantes de {@code Roles})
     * @param evento    evento
     * @param mensaje   texto (se recorta a 500 caracteres)
     * @return cantidad de destinatarios
     */
    @Transactional
    public int notificarRol(String codigoRol, EventoNotificacion evento, String mensaje) {
        List<Usuario> destinatarios = usuarioRepository.findByActivoTrueAndRoles_Codigo(codigoRol);
        if (destinatarios.isEmpty()) {
            log.warn("Notificación {} sin destinatarios: no hay usuarios activos con rol {}", evento, codigoRol);
            return 0;
        }
        entregar(destinatarios, evento, mensaje);
        return destinatarios.size();
    }

    /**
     * Bandeja del usuario de la sesión, las más recientes primero.
     *
     * @param soloNoLeidas {@code true} para ver solo las pendientes
     * @param pageable     página y orden
     * @return página de notificaciones
     */
    @Transactional(readOnly = true)
    public PageResponse<NotificacionResponse> mias(boolean soloNoLeidas, Pageable pageable) {
        Long usuarioId = SesionActual.usuario().id();
        Pageable pagina = Ordenamiento.validar(pageable, CAMPOS_ORDENABLES);
        return PageResponse.of(soloNoLeidas
                ? notificacionRepository.findByUsuarioIdAndLeidaFalse(usuarioId, pagina)
                : notificacionRepository.findByUsuarioId(usuarioId, pagina), NotificacionResponse::of);
    }

    /**
     * Cantidad de notificaciones sin leer del usuario de la sesión.
     *
     * @return contador
     */
    @Transactional(readOnly = true)
    public CantidadResponse noLeidas() {
        return new CantidadResponse(notificacionRepository.countByUsuarioIdAndLeidaFalse(SesionActual.usuario().id()));
    }

    /**
     * Marca una notificación propia como leída. Una ajena responde 404 (no se revela que existe).
     *
     * @param id id de la notificación
     * @return notificación actualizada
     */
    @Transactional
    public NotificacionResponse marcarLeida(Long id) {
        Notificacion n = notificacionRepository.findByIdAndUsuarioId(id, SesionActual.usuario().id())
                .orElseThrow(() -> new NoEncontradoException("Notificación no encontrada"));
        n.marcarLeida();
        return NotificacionResponse.of(n);
    }

    /**
     * Marca como leídas todas las notificaciones del usuario de la sesión.
     *
     * @return cantidad marcada
     */
    @Transactional
    public CantidadResponse marcarTodasLeidas() {
        return new CantidadResponse(notificacionRepository.marcarTodasLeidas(SesionActual.usuario().id()));
    }

    /**
     * Configuración de canales por evento.
     *
     * @return filas ordenadas por evento y canal
     */
    @Transactional(readOnly = true)
    public List<ConfigNotificacionResponse> configuracion() {
        return configRepository.findAllByOrderByEventoAscCanalAsc().stream().map(ConfigNotificacionResponse::of)
                .toList();
    }

    /**
     * Activa o desactiva un canal para un evento (auditado).
     *
     * @param id     id de la configuración
     * @param activo nuevo estado
     * @return configuración actualizada
     */
    @Transactional
    public ConfigNotificacionResponse cambiarConfiguracion(Long id, boolean activo) {
        ConfigNotificacion c = configRepository.findById(id)
                .orElseThrow(() -> new NoEncontradoException("Configuración de notificación no encontrada"));
        if (c.isActivo() != activo) {
            c.setActivo(activo);
            auditoriaService.exito(AccionAuditoria.CONFIG_NOTIFICACION_CAMBIAR, EntidadAuditada.CONFIG_NOTIFICACION,
                    c.getId(), c.getEvento() + "/" + c.getCanal() + " activo=" + activo);
        }
        return ConfigNotificacionResponse.of(c);
    }

    /**
     * Guarda la notificación interna y programa el correo según los canales activos.
     *
     * @param destinatarios usuarios activos
     * @param evento        evento
     * @param mensaje       texto
     */
    private void entregar(List<Usuario> destinatarios, EventoNotificacion evento, String mensaje) {
        String texto = recortar(mensaje);
        if (canalActivo(evento, CanalNotificacion.INTERNA)) {
            notificacionRepository.saveAll(
                    destinatarios.stream().map(u -> new Notificacion(u.getId(), evento, texto)).toList());
        }
        if (canalActivo(evento, CanalNotificacion.EMAIL)) {
            List<String> correos = destinatarios.stream().map(Usuario::getCorreo).toList();
            despuesDelCommit(() -> correos.forEach(c -> enviarCorreo(c, evento, texto)));
        }
    }

    /**
     * Indica si el evento se notifica por el canal (sin fila de configuración = activo).
     *
     * @param evento evento
     * @param canal  canal
     * @return {@code true} si se debe enviar
     */
    private boolean canalActivo(EventoNotificacion evento, CanalNotificacion canal) {
        return configRepository.findByEventoAndCanal(evento, canal).map(ConfigNotificacion::isActivo).orElse(true);
    }

    /**
     * Ejecuta la acción cuando la transacción actual confirme; sin transacción, de inmediato.
     *
     * @param accion acción a ejecutar
     */
    private static void despuesDelCommit(Runnable accion) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                /** Envía al confirmar la transacción. */
                @Override
                public void afterCommit() {
                    accion.run();
                }
            });
        } else {
            accion.run();
        }
    }

    /**
     * Envía un correo sin propagar errores (el aviso interno ya quedó guardado).
     *
     * @param correo destinatario
     * @param evento evento (define el asunto)
     * @param texto  cuerpo
     */
    private void enviarCorreo(String correo, EventoNotificacion evento, String texto) {
        try {
            emailClient.enviar(correo, "Supply-Core: " + evento.asunto(), texto);
        } catch (RuntimeException e) {
            log.error("No se pudo enviar el correo de {} a {}: {}", evento, correo, e.getMessage());
        }
    }

    /**
     * Recorta el mensaje al largo de la columna.
     *
     * @param mensaje texto original
     * @return texto de máximo 500 caracteres
     */
    static String recortar(String mensaje) {
        String t = mensaje == null ? "" : mensaje.trim();
        return t.length() <= MAX_MENSAJE ? t : t.substring(0, MAX_MENSAJE - 1) + "…";
    }
}
