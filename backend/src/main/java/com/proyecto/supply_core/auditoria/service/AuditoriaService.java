package com.proyecto.supply_core.auditoria.service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.proyecto.supply_core.auditoria.dto.AuditoriaFiltro;
import com.proyecto.supply_core.auditoria.dto.AuditoriaResponse;
import com.proyecto.supply_core.auditoria.entity.Auditoria;
import com.proyecto.supply_core.auditoria.enums.AccionAuditoria;
import com.proyecto.supply_core.auditoria.enums.EntidadAuditada;
import com.proyecto.supply_core.auditoria.enums.ResultadoAuditoria;
import com.proyecto.supply_core.auditoria.repository.AuditoriaRepository;
import com.proyecto.supply_core.auditoria.repository.AuditoriaSpecs;
import com.proyecto.supply_core.common.dto.PageResponse;
import com.proyecto.supply_core.common.exception.NoEncontradoException;
import com.proyecto.supply_core.common.exception.ParametroInvalidoException;
import com.proyecto.supply_core.common.util.Ordenamiento;
import com.proyecto.supply_core.security.context.SesionActual;
import com.proyecto.supply_core.security.context.UsuarioAutenticado;
import com.proyecto.supply_core.usuario.entity.Usuario;
import com.proyecto.supply_core.usuario.repository.UsuarioRepository;

/**
 * Registro y consulta de la auditoría de acciones sensibles.
 * <ul>
 * <li>{@link #exito}: se une a la transacción de la operación. Si la operación se revierte,
 * su registro también (no quedan éxitos falsos); si no se puede auditar, la operación falla.</li>
 * <li>{@link #fallo}: transacción propia ({@code REQUIRES_NEW}), porque la operación que falla
 * se revierte y el intento debe quedar registrado igual. Si no se puede guardar, se registra en
 * el log y no se oculta el error original.</li>
 * </ul>
 */
@Service
public class AuditoriaService {

    private static final Logger log = LoggerFactory.getLogger(AuditoriaService.class);
    private static final int LARGO_MAXIMO_DETALLE = 4000;
    private static final int LARGO_MAXIMO_IP = 45;
    /** Campos por los que se puede ordenar la consulta. */
    private static final Set<String> CAMPOS_ORDENABLES = Set.of("creadoEn", "accion", "entidad", "resultado");

    private final AuditoriaRepository auditoriaRepository;
    private final UsuarioRepository usuarioRepository;

    /**
     * @param auditoriaRepository acceso a la auditoría
     * @param usuarioRepository   acceso a usuarios (para mostrar nombre y correo)
     */
    public AuditoriaService(AuditoriaRepository auditoriaRepository, UsuarioRepository usuarioRepository) {
        this.auditoriaRepository = auditoriaRepository;
        this.usuarioRepository = usuarioRepository;
    }

    /**
     * Registra una acción exitosa del usuario de la sesión actual.
     *
     * @param accion    acción realizada
     * @param entidad   tipo de entidad afectada
     * @param entidadId id del registro afectado
     * @param detalle   contexto legible, sin contraseñas ni datos sensibles
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public void exito(AccionAuditoria accion, EntidadAuditada entidad, Long entidadId, String detalle) {
        Long usuarioId = SesionActual.opcional().map(UsuarioAutenticado::id).orElse(null);
        exitoDe(usuarioId, accion, entidad, entidadId, detalle);
    }

    /**
     * Registra una acción exitosa indicando explícitamente quién la hizo
     * (p. ej. el login, cuando la sesión aún no existe).
     *
     * @param usuarioId quién ejecutó la acción
     * @param accion    acción realizada
     * @param entidad   tipo de entidad afectada
     * @param entidadId id del registro afectado
     * @param detalle   contexto legible
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public void exitoDe(Long usuarioId, AccionAuditoria accion, EntidadAuditada entidad, Long entidadId,
            String detalle) {
        auditoriaRepository.save(nuevo(usuarioId, accion, entidad, entidadId, ResultadoAuditoria.EXITO, detalle));
    }

    /**
     * Registra un intento fallido en una transacción independiente.
     *
     * @param usuarioId quién lo intentó ({@code null} si no se sabe)
     * @param accion    acción intentada
     * @param entidad   tipo de entidad afectada
     * @param entidadId id del registro afectado ({@code null} si no aplica)
     * @param detalle   motivo del fallo, sin datos sensibles
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fallo(Long usuarioId, AccionAuditoria accion, EntidadAuditada entidad, Long entidadId,
            String detalle) {
        try {
            auditoriaRepository.save(nuevo(usuarioId, accion, entidad, entidadId, ResultadoAuditoria.FALLO, detalle));
        } catch (RuntimeException e) {
            log.error("No se pudo auditar el fallo {} sobre {} {}", accion, entidad, entidadId, e);
        }
    }

    /**
     * Consulta paginada con filtros (UC-16). Solo lectura.
     *
     * @param filtro   filtros opcionales ya validados
     * @param pageable página y orden (solo campos de {@link #CAMPOS_ORDENABLES})
     * @return página de registros con nombre y correo del usuario
     * @throws ParametroInvalidoException si se pide ordenar por un campo no permitido
     */
    @Transactional(readOnly = true)
    public PageResponse<AuditoriaResponse> buscar(AuditoriaFiltro filtro, Pageable pageable) {
        Page<Auditoria> pagina = auditoriaRepository.findAll(AuditoriaSpecs.conFiltro(filtro),
                Ordenamiento.validar(pageable, CAMPOS_ORDENABLES));
        Map<Long, Usuario> usuarios = usuariosDe(pagina.getContent());
        return PageResponse.of(pagina, a -> responder(a, usuarios));
    }

    /**
     * Detalle de un registro.
     *
     * @param id id del registro
     * @return registro con datos del usuario
     * @throws NoEncontradoException si no existe
     */
    @Transactional(readOnly = true)
    public AuditoriaResponse obtener(Long id) {
        Auditoria a = auditoriaRepository.findById(id)
                .orElseThrow(() -> new NoEncontradoException("Registro de auditoría", id));
        return responder(a, usuariosDe(List.of(a)));
    }

    // ----------------------------------------------------------------- apoyo

    /**
     * Arma el registro con la IP de la petición y el detalle recortado.
     *
     * @param usuarioId quién
     * @param accion    qué
     * @param entidad   sobre qué tipo de entidad
     * @param entidadId sobre qué registro
     * @param resultado éxito o fallo
     * @param detalle   contexto
     * @return entidad lista para guardar
     */
    private Auditoria nuevo(Long usuarioId, AccionAuditoria accion, EntidadAuditada entidad, Long entidadId,
            ResultadoAuditoria resultado, String detalle) {
        return new Auditoria(usuarioId, entidad.name(), entidadId, accion.name(), resultado,
                recortar(ipActual(), LARGO_MAXIMO_IP), recortar(detalle, LARGO_MAXIMO_DETALLE));
    }

    /**
     * Carga en una sola consulta los usuarios que aparecen en los registros.
     *
     * @param registros registros de auditoría
     * @return usuarios por id
     */
    private Map<Long, Usuario> usuariosDe(List<Auditoria> registros) {
        Set<Long> ids = registros.stream().map(Auditoria::getUsuarioId).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Map.of();
        }
        return usuarioRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Usuario::getId, Function.identity()));
    }

    /**
     * Convierte un registro en DTO.
     *
     * @param a        registro
     * @param usuarios usuarios ya cargados por id
     * @return DTO
     */
    private static AuditoriaResponse responder(Auditoria a, Map<Long, Usuario> usuarios) {
        Usuario u = a.getUsuarioId() == null ? null : usuarios.get(a.getUsuarioId());
        return AuditoriaResponse.of(a, u == null ? null : u.getNombreCompleto(), u == null ? null : u.getCorreo());
    }

    /**
     * IP del cliente de la petición actual. Se usa la dirección de la conexión
     * (no {@code X-Forwarded-For}, que el cliente puede falsificar si no hay un proxy de confianza).
     *
     * @return IP o {@code null} si no hay petición web (p. ej. un job)
     */
    private static String ipActual() {
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        if (attrs instanceof ServletRequestAttributes servlet) {
            return servlet.getRequest().getRemoteAddr();
        }
        return null;
    }

    /**
     * Recorta un texto al largo máximo de su columna.
     *
     * @param texto  texto original (puede ser {@code null})
     * @param maximo largo máximo
     * @return texto recortado o {@code null}
     */
    private static String recortar(String texto, int maximo) {
        return texto == null || texto.length() <= maximo ? texto : texto.substring(0, maximo);
    }
}
