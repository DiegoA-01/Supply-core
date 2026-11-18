package com.proyecto.supply_core.delegacion.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.proyecto.supply_core.auditoria.enums.AccionAuditoria;
import com.proyecto.supply_core.auditoria.enums.EntidadAuditada;
import com.proyecto.supply_core.auditoria.service.AuditoriaService;
import com.proyecto.supply_core.common.exception.AccesoDenegadoException;
import com.proyecto.supply_core.common.exception.NegocioException;
import com.proyecto.supply_core.common.exception.NoEncontradoException;
import com.proyecto.supply_core.delegacion.dto.DelegacionRequest;
import com.proyecto.supply_core.delegacion.dto.DelegadoCandidato;
import com.proyecto.supply_core.delegacion.dto.DelegacionResponse;
import com.proyecto.supply_core.delegacion.entity.DelegacionAprobacion;
import com.proyecto.supply_core.delegacion.enums.EstadoDelegacion;
import com.proyecto.supply_core.delegacion.repository.DelegacionRepository;
import com.proyecto.supply_core.empresa.repository.PoliticaAprobacionRepository;
import com.proyecto.supply_core.notificacion.enums.EventoNotificacion;
import com.proyecto.supply_core.notificacion.service.NotificacionService;
import com.proyecto.supply_core.security.context.Roles;
import com.proyecto.supply_core.security.context.SesionActual;
import com.proyecto.supply_core.security.context.UsuarioAutenticado;
import com.proyecto.supply_core.usuario.entity.Usuario;
import com.proyecto.supply_core.usuario.repository.UsuarioRepository;

/**
 * Delegación de aprobación por fechas (paso 18-c).
 * <ul>
 *   <li>Delega quien tiene un rol que aprueba en alguna política activa; el delegado es otro usuario interno
 *       activo. Durante el rango, el delegado puede decidir lo que el delegante podría decidir por sus roles.</li>
 *   <li><b>La segregación de funciones sigue igual:</b> el delegado no puede decidir lo que él mismo pidió ni lo
 *       que pidió quien le delegó. Cada decisión guarda de qué delegación vino.</li>
 *   <li>El delegante o ADMIN_COMPRAS pueden revocarla antes de tiempo. El vencimiento se calcula con las fechas.</li>
 * </ul>
 * <p>La autoridad para decidir (propia o delegada) se resuelve con {@link #autorizar}.</p>
 */
@Service
public class DelegacionService {

    private final DelegacionRepository delegacionRepository;
    private final UsuarioRepository usuarioRepository;
    private final PoliticaAprobacionRepository politicaRepository;
    private final AuditoriaService auditoriaService;
    private final NotificacionService notificacionService;

    /**
     * @param delegacionRepository delegaciones
     * @param usuarioRepository    delegado y nombres
     * @param politicaRepository   roles que aprueban en alguna política activa
     * @param auditoriaService     registro de cambios
     * @param notificacionService  aviso al delegado
     */
    public DelegacionService(DelegacionRepository delegacionRepository, UsuarioRepository usuarioRepository,
            PoliticaAprobacionRepository politicaRepository, AuditoriaService auditoriaService,
            NotificacionService notificacionService) {
        this.delegacionRepository = delegacionRepository;
        this.usuarioRepository = usuarioRepository;
        this.politicaRepository = politicaRepository;
        this.auditoriaService = auditoriaService;
        this.notificacionService = notificacionService;
    }

    /**
     * El usuario de la sesión delega sus decisiones de aprobación en otro usuario.
     *
     * @param req datos validados
     * @return delegación creada
     * @throws NegocioException      si se delega a sí mismo, no tiene un rol aprobador, el delegado no es un usuario
     *                               interno activo o la fecha de fin ya pasó
     * @throws NoEncontradoException si el delegado no existe
     */
    @Transactional
    public DelegacionResponse crear(DelegacionRequest req) {
        UsuarioAutenticado sesion = SesionActual.usuario();
        LocalDate hoy = LocalDate.now();
        if (req.delegadoId().equals(sesion.id())) {
            throw new NegocioException("DELEGADO_INVALIDO", "No puede delegarse a sí mismo");
        }
        Set<String> aprobadores = politicaRepository.rolesAprobadoresActivos();
        if (sesion.roles().stream().noneMatch(aprobadores::contains)) {
            throw new NegocioException("SIN_ROL_APROBADOR",
                    "Solo quien tiene un rol que aprueba en alguna política activa puede delegar");
        }
        Usuario delegado = usuarioRepository.findById(req.delegadoId())
                .orElseThrow(() -> new NoEncontradoException("Usuario", req.delegadoId()));
        if (!delegado.isActivo() || delegado.getProveedorId() != null) {
            throw new NegocioException("DELEGADO_INVALIDO", "Solo se delega en un usuario interno activo");
        }
        if (req.vigenteHasta().isBefore(hoy)) {
            throw new NegocioException("VIGENCIA_PASADA", "La delegación ya terminó el " + req.vigenteHasta());
        }
        DelegacionAprobacion d = new DelegacionAprobacion();
        d.setDeleganteId(sesion.id());
        d.setDelegadoId(delegado.getId());
        d.setVigenteDesde(req.vigenteDesde());
        d.setVigenteHasta(req.vigenteHasta());
        d.setMotivo(req.motivo() == null || req.motivo().isBlank() ? null : req.motivo().trim());
        DelegacionAprobacion guardada = delegacionRepository.save(d);
        auditoriaService.exito(AccionAuditoria.DELEGACION_CREAR, EntidadAuditada.DELEGACION, guardada.getId(),
                "delegante=" + sesion.id() + "; delegado=" + delegado.getId() + "; del " + d.getVigenteDesde() + " al "
                        + d.getVigenteHasta());
        notificacionService.notificar(delegado.getId(), EventoNotificacion.DELEGACION_ASIGNADA,
                sesion.nombre() + " le delegó sus aprobaciones del " + d.getVigenteDesde() + " al " + d.getVigenteHasta());
        return respuesta(List.of(guardada), hoy).get(0);
    }

    /**
     * Revoca una delegación antes de que termine. La revoca quien la dio o ADMIN_COMPRAS.
     *
     * @param id delegación
     * @return delegación revocada
     * @throws NoEncontradoException   si no existe
     * @throws AccesoDenegadoException si no es el delegante ni ADMIN_COMPRAS
     * @throws NegocioException        si ya estaba revocada
     */
    @Transactional
    public DelegacionResponse revocar(Long id) {
        UsuarioAutenticado sesion = SesionActual.usuario();
        DelegacionAprobacion d = delegacionRepository.findById(id)
                .orElseThrow(() -> new NoEncontradoException("Delegación", id));
        if (!d.getDeleganteId().equals(sesion.id()) && !sesion.tieneAlgunRol(Roles.ADMIN_COMPRAS)) {
            throw new AccesoDenegadoException("Solo quien delegó o compras pueden revocar la delegación");
        }
        if (d.getEstado() != EstadoDelegacion.ACTIVA) {
            throw new NegocioException("DELEGACION_REVOCADA", "La delegación ya estaba revocada");
        }
        d.setEstado(EstadoDelegacion.REVOCADA);
        d.setRevocadaEn(LocalDateTime.now());
        d.setRevocadaPorUsuarioId(sesion.id());
        auditoriaService.exito(AccionAuditoria.DELEGACION_REVOCAR, EntidadAuditada.DELEGACION, id,
                "delegante=" + d.getDeleganteId() + "; delegado=" + d.getDelegadoId());
        return respuesta(List.of(d), LocalDate.now()).get(0);
    }

    /**
     * Usuarios en quienes el usuario de la sesión puede delegar (internos y activos, menos él). Solo lo consulta quien
     * tiene un rol que aprueba en alguna política activa, el mismo requisito que para delegar.
     *
     * @return id y nombre de cada candidato, por nombre
     * @throws NegocioException si no tiene un rol que aprueba
     */
    @Transactional(readOnly = true)
    public List<DelegadoCandidato> candidatos() {
        UsuarioAutenticado sesion = SesionActual.usuario();
        Set<String> aprobadores = politicaRepository.rolesAprobadoresActivos();
        if (sesion.roles().stream().noneMatch(aprobadores::contains)) {
            throw new NegocioException("SIN_ROL_APROBADOR",
                    "Solo quien tiene un rol que aprueba en alguna política activa puede delegar");
        }
        return usuarioRepository.candidatosDeDelegacion(sesion.id()).stream()
                .map(u -> new DelegadoCandidato(u.getId(), u.getNombreCompleto())).toList();
    }

    /**
     * Delegaciones que dio el usuario de la sesión.
     *
     * @return delegaciones, la más reciente primero
     */
    @Transactional(readOnly = true)
    public List<DelegacionResponse> otorgadas() {
        return respuesta(delegacionRepository.findByDeleganteIdOrderByIdDesc(SesionActual.usuario().id()),
                LocalDate.now());
    }

    /**
     * Delegaciones que recibió el usuario de la sesión.
     *
     * @return delegaciones, la más reciente primero
     */
    @Transactional(readOnly = true)
    public List<DelegacionResponse> recibidas() {
        return respuesta(delegacionRepository.findByDelegadoIdOrderByIdDesc(SesionActual.usuario().id()),
                LocalDate.now());
    }

    /**
     * Roles con los que el usuario puede aprobar hoy: los suyos más los de quienes le delegaron.
     *
     * @param sesion usuario de la sesión
     * @return códigos de rol
     */
    @Transactional(readOnly = true)
    public Set<String> rolesEfectivos(UsuarioAutenticado sesion) {
        Set<String> roles = new HashSet<>(sesion.roles());
        roles.addAll(delegacionRepository.rolesDelegados(sesion.id(), LocalDate.now()));
        return roles;
    }

    /**
     * Resuelve con qué autoridad puede decidir el usuario algo que exige un rol. Primero cuenta su propio rol; si
     * no lo tiene, busca una delegación vigente que se lo dé, sin contar la de quien originó lo que se decide.
     *
     * @param sesion usuario que decide
     * @param rol    código del rol que exige la política
     * @param autorId usuario que originó lo que se decide (solicitante o quien adjudicó); su delegado no puede decidir
     * @return vacío si decide con su propio rol; el id de la delegación si decide por delegación
     * @throws AccesoDenegadoException si no tiene el rol ni una delegación vigente que se lo dé
     */
    @Transactional(readOnly = true)
    public Optional<Long> autorizar(UsuarioAutenticado sesion, String rol, Long autorId) {
        if (sesion.tieneAlgunRol(rol)) {
            return Optional.empty();
        }
        List<Long> delegaciones = delegacionRepository.vigentesQueDanElRol(sesion.id(), rol, LocalDate.now(), autorId);
        if (delegaciones.isEmpty()) {
            throw new AccesoDenegadoException("Esto lo aprueba el rol " + rol);
        }
        return Optional.of(delegaciones.get(0));
    }

    /**
     * Usuarios que hoy pueden decidir por delegación lo que exige un rol (para avisarles).
     *
     * @param rol código del rol
     * @return ids de usuario delegado
     */
    @Transactional(readOnly = true)
    public Set<Long> delegadosDelRol(String rol) {
        return delegacionRepository.delegadosDelRol(rol, LocalDate.now());
    }

    // ----------------------------------------------------------------- apoyo

    /**
     * Convierte delegaciones a DTO con los nombres de las dos personas.
     *
     * @param delegaciones delegaciones
     * @param hoy          fecha con la que se calcula si están vigentes
     * @return DTOs
     */
    private List<DelegacionResponse> respuesta(List<DelegacionAprobacion> delegaciones, LocalDate hoy) {
        Set<Long> ids = new HashSet<>();
        delegaciones.forEach(d -> {
            ids.add(d.getDeleganteId());
            ids.add(d.getDelegadoId());
        });
        Map<Long, String> nombres = new HashMap<>();
        usuarioRepository.findAllById(ids).forEach(u -> nombres.put(u.getId(), u.getNombreCompleto()));
        return delegaciones.stream().map(d -> DelegacionResponse.of(d, nombres, hoy)).toList();
    }
}
