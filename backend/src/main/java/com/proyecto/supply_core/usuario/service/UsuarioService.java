package com.proyecto.supply_core.usuario.service;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.proyecto.supply_core.auditoria.enums.AccionAuditoria;
import com.proyecto.supply_core.auditoria.enums.EntidadAuditada;
import com.proyecto.supply_core.auditoria.service.AuditoriaService;
import com.proyecto.supply_core.common.dto.PageResponse;
import com.proyecto.supply_core.common.exception.NegocioException;
import com.proyecto.supply_core.common.exception.NoEncontradoException;
import com.proyecto.supply_core.common.exception.ParametroInvalidoException;
import com.proyecto.supply_core.common.util.Ordenamiento;
import com.proyecto.supply_core.security.context.Roles;
import com.proyecto.supply_core.security.context.SesionActual;
import com.proyecto.supply_core.usuario.dto.CambioPasswordRequest;
import com.proyecto.supply_core.usuario.dto.RestablecerPasswordRequest;
import com.proyecto.supply_core.usuario.dto.RolResponse;
import com.proyecto.supply_core.usuario.dto.UsuarioRequest;
import com.proyecto.supply_core.usuario.dto.UsuarioResponse;
import com.proyecto.supply_core.usuario.dto.UsuarioUpdateRequest;
import com.proyecto.supply_core.usuario.entity.Rol;
import com.proyecto.supply_core.usuario.entity.Usuario;
import com.proyecto.supply_core.usuario.repository.RolRepository;
import com.proyecto.supply_core.usuario.repository.UsuarioRepository;
import com.proyecto.supply_core.usuario.validation.CodigosRol;

/**
 * Gestión de usuarios internos y del portal proveedor.
 * <p>Las validaciones de formato y de datos (correo único, roles existentes, regla PROVEEDOR)
 * viven en los requests ({@code usuario.validation}). Aquí quedan las reglas que dependen del
 * estado: nunca se borra (se desactiva), nadie se desactiva a sí mismo y siempre queda al menos
 * un ADMIN_SISTEMA activo.</p>
 */
@Service
public class UsuarioService {

    private static final Logger log = LoggerFactory.getLogger(UsuarioService.class);
    /** Campos por los que se puede ordenar el listado (nunca hashPassword). */
    private static final Set<String> CAMPOS_ORDENABLES =
            Set.of("nombreCompleto", "correo", "activo", "ultimoAcceso", "creadoEn");

    private final UsuarioRepository usuarioRepository;
    private final RolRepository rolRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditoriaService auditoriaService;

    /**
     * @param usuarioRepository acceso a usuarios
     * @param rolRepository     acceso a roles
     * @param passwordEncoder   hash BCrypt
     * @param auditoriaService  registro de las acciones sensibles sobre usuarios
     */
    public UsuarioService(UsuarioRepository usuarioRepository, RolRepository rolRepository,
            PasswordEncoder passwordEncoder, AuditoriaService auditoriaService) {
        this.usuarioRepository = usuarioRepository;
        this.rolRepository = rolRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditoriaService = auditoriaService;
    }

    /**
     * Lista usuarios con filtros opcionales.
     *
     * @param texto    coincidencia parcial en nombre o correo ({@code null} o vacío = todos)
     * @param activo   filtra por estado ({@code null} = ambos)
     * @param rol      código de rol ({@code null} = todos)
     * @param pageable página y orden (solo campos de {@link #CAMPOS_ORDENABLES})
     * @return página de usuarios
     * @throws ParametroInvalidoException si se pide ordenar por un campo no permitido
     */
    @Transactional(readOnly = true)
    public PageResponse<UsuarioResponse> buscar(String texto, Boolean activo, String rol, Pageable pageable) {
        String filtroTexto = (texto == null || texto.isBlank()) ? null : texto.trim();
        String filtroRol = (rol == null || rol.isBlank()) ? null : CodigosRol.normalizar(rol);
        Pageable pagina = Ordenamiento.validar(pageable, CAMPOS_ORDENABLES);
        return PageResponse.of(usuarioRepository.buscar(filtroTexto, activo, filtroRol, pagina),
                UsuarioResponse::of);
    }

    /**
     * Obtiene un usuario.
     *
     * @param id id del usuario
     * @return usuario
     * @throws NoEncontradoException si no existe
     */
    @Transactional(readOnly = true)
    public UsuarioResponse obtener(Long id) {
        return UsuarioResponse.of(buscarEntidad(id));
    }

    /**
     * Crea un usuario en la empresa del administrador que lo registra.
     * <p>El request ya llega validado ({@code @Valid}). Si dos altas simultáneas usan el mismo
     * correo, el UNIQUE de la BD responde 409.</p>
     *
     * @param req datos del nuevo usuario
     * @return usuario creado
     */
    @Transactional
    public UsuarioResponse crear(UsuarioRequest req) {
        Usuario u = new Usuario();
        u.setEmpresaId(buscarEntidad(SesionActual.usuario().id()).getEmpresaId());
        u.setNombreCompleto(req.nombreCompleto().trim());
        u.setCorreo(normalizarCorreo(req.correo()));
        u.setHashPassword(passwordEncoder.encode(req.password()));
        u.setCentroCostoId(req.centroCostoId());
        u.setProveedorId(req.proveedorId());
        u.setRoles(resolverRoles(req.roles()));
        Usuario guardado = usuarioRepository.save(u);
        log.info("Usuario {} creado por {}", guardado.getId(), SesionActual.usuario().id());
        auditoriaService.exito(AccionAuditoria.USUARIO_CREAR, EntidadAuditada.USUARIO, guardado.getId(),
                describir(guardado));
        return UsuarioResponse.of(guardado);
    }

    /**
     * Actualiza datos y roles de un usuario.
     *
     * @param id  usuario a editar
     * @param req nuevos datos
     * @return usuario actualizado
     * @throws NoEncontradoException si no existe
     * @throws NegocioException      si el correo lo usa otro usuario o se quitaría el último admin activo
     */
    @Transactional
    public UsuarioResponse actualizar(Long id, UsuarioUpdateRequest req) {
        Usuario u = buscarEntidad(id);
        String correo = normalizarCorreo(req.correo());
        if (usuarioRepository.existsByCorreoIgnoreCaseAndIdNot(correo, id)) {
            throw new NegocioException("CORREO_DUPLICADO", "Ya existe un usuario con el correo " + correo);
        }
        Set<Rol> roles = resolverRoles(req.roles());

        boolean pierdeAdmin = u.codigosRoles().contains(Roles.ADMIN_SISTEMA)
                && roles.stream().noneMatch(r -> Roles.ADMIN_SISTEMA.equals(r.getCodigo()));
        if (pierdeAdmin && u.isActivo()) {
            validarNoEsUltimoAdmin();
        }

        String antes = describir(u);
        u.setNombreCompleto(req.nombreCompleto().trim());
        u.setCorreo(correo);
        u.setCentroCostoId(req.centroCostoId());
        u.setProveedorId(req.proveedorId());
        u.getRoles().clear();
        u.getRoles().addAll(roles);
        auditoriaService.exito(AccionAuditoria.USUARIO_EDITAR, EntidadAuditada.USUARIO, id,
                "antes: " + antes + " | después: " + describir(u));
        return UsuarioResponse.of(u);
    }

    /**
     * Reactiva un usuario.
     *
     * @param id usuario
     * @return usuario activo
     * @throws NoEncontradoException si no existe
     */
    @Transactional
    public UsuarioResponse activar(Long id) {
        Usuario u = buscarEntidad(id);
        u.setActivo(true);
        auditoriaService.exito(AccionAuditoria.USUARIO_ACTIVAR, EntidadAuditada.USUARIO, id, "correo=" + u.getCorreo());
        return UsuarioResponse.of(u);
    }

    /**
     * Desactiva un usuario (no se borra: su historial se conserva).
     *
     * @param id usuario
     * @return usuario inactivo
     * @throws NoEncontradoException si no existe
     * @throws NegocioException      si intenta desactivarse a sí mismo o es el último admin activo
     */
    @Transactional
    public UsuarioResponse desactivar(Long id) {
        if (id.equals(SesionActual.usuario().id())) {
            throw new NegocioException("AUTO_DESACTIVACION", "No puede desactivar su propio usuario");
        }
        Usuario u = buscarEntidad(id);
        if (u.isActivo() && u.codigosRoles().contains(Roles.ADMIN_SISTEMA)) {
            validarNoEsUltimoAdmin();
        }
        u.setActivo(false);
        log.info("Usuario {} desactivado por {}", id, SesionActual.usuario().id());
        auditoriaService.exito(AccionAuditoria.USUARIO_DESACTIVAR, EntidadAuditada.USUARIO, id,
                "correo=" + u.getCorreo());
        return UsuarioResponse.of(u);
    }

    /**
     * El administrador asigna una contraseña nueva (p. ej. si el usuario la olvidó).
     *
     * @param id  usuario
     * @param req contraseña nueva ya validada
     * @throws NoEncontradoException si no existe
     */
    @Transactional
    public void restablecerPassword(Long id, RestablecerPasswordRequest req) {
        Usuario u = buscarEntidad(id);
        u.setHashPassword(passwordEncoder.encode(req.passwordNueva()));
        log.info("Contraseña del usuario {} restablecida por {}", id, SesionActual.usuario().id());
        // nunca se audita la contraseña, solo el hecho
        auditoriaService.exito(AccionAuditoria.USUARIO_RESTABLECER_PASSWORD, EntidadAuditada.USUARIO, id,
                "correo=" + u.getCorreo());
    }

    /**
     * El usuario logueado cambia su propia contraseña.
     *
     * @param req contraseña actual y nueva
     * @throws NegocioException si la actual no coincide o la nueva es igual a la actual
     */
    @Transactional
    public void cambiarPasswordPropia(CambioPasswordRequest req) {
        Usuario u = buscarEntidad(SesionActual.usuario().id());
        if (!passwordEncoder.matches(req.passwordActual(), u.getHashPassword())) {
            throw new NegocioException("PASSWORD_ACTUAL", "La contraseña actual no es correcta");
        }
        if (req.passwordActual().equals(req.passwordNueva())) {
            throw new NegocioException("PASSWORD_REPETIDA", "La nueva contraseña debe ser distinta a la actual");
        }
        u.setHashPassword(passwordEncoder.encode(req.passwordNueva()));
        auditoriaService.exito(AccionAuditoria.PASSWORD_CAMBIAR, EntidadAuditada.USUARIO, u.getId(), null);
    }

    /**
     * Datos del usuario logueado.
     *
     * @return perfil propio
     */
    @Transactional(readOnly = true)
    public UsuarioResponse perfil() {
        return obtener(SesionActual.usuario().id());
    }

    /**
     * Catálogo de roles.
     *
     * @return roles ordenados por nombre
     */
    @Transactional(readOnly = true)
    public List<RolResponse> listarRoles() {
        return rolRepository.findAllByOrderByNombreAsc().stream().map(RolResponse::of).toList();
    }

    // ----------------------------------------------------------------- apoyo

    /**
     * Busca la entidad con sus roles.
     *
     * @param id id del usuario
     * @return entidad
     * @throws NoEncontradoException si no existe
     */
    private Usuario buscarEntidad(Long id) {
        return usuarioRepository.findWithRolesById(id)
                .orElseThrow(() -> new NoEncontradoException("Usuario", id));
    }

    /**
     * Convierte códigos a entidades. {@code @RolesExistentes} ya garantizó que existen.
     *
     * @param codigos códigos de rol (cualquier mayúscula/espacio)
     * @return entidades de rol
     * @throws NegocioException si alguno no existe (solo si se llamó al Service sin {@code @Valid})
     */
    private Set<Rol> resolverRoles(Set<String> codigos) {
        Set<String> limpios = codigos.stream().map(CodigosRol::normalizar).collect(Collectors.toSet());
        Set<Rol> roles = new HashSet<>(rolRepository.findByCodigoIn(limpios));
        if (roles.size() != limpios.size()) {
            throw new NegocioException("ROL_INEXISTENTE", "Hay roles inexistentes en " + limpios);
        }
        return roles;
    }

    /**
     * Impide dejar el sistema sin administradores activos.
     *
     * @throws NegocioException si solo queda un ADMIN_SISTEMA activo
     */
    private void validarNoEsUltimoAdmin() {
        if (usuarioRepository.countByActivoTrueAndRoles_Codigo(Roles.ADMIN_SISTEMA) <= 1) {
            throw new NegocioException("ULTIMO_ADMIN", "Debe quedar al menos un administrador del sistema activo");
        }
    }

    /**
     * Resumen legible del usuario para la auditoría (sin contraseña).
     *
     * @param u usuario
     * @return texto con correo, roles, estado, centro de costo y proveedor
     */
    private static String describir(Usuario u) {
        return "correo=" + u.getCorreo() + ", roles=" + new TreeSet<>(u.codigosRoles())
                + ", activo=" + u.isActivo() + ", centroCostoId=" + u.getCentroCostoId()
                + ", proveedorId=" + u.getProveedorId();
    }

    /**
     * Normaliza el correo para guardarlo y compararlo siempre igual.
     *
     * @param correo correo recibido
     * @return correo sin espacios y en minúsculas
     */
    private static String normalizarCorreo(String correo) {
        return correo.trim().toLowerCase(Locale.ROOT);
    }
}
