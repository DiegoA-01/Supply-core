package com.proyecto.supply_core.proveedor.service;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.proyecto.supply_core.auditoria.enums.AccionAuditoria;
import com.proyecto.supply_core.auditoria.enums.EntidadAuditada;
import com.proyecto.supply_core.auditoria.service.AuditoriaService;
import com.proyecto.supply_core.catalogo.entity.CategoriaProducto;
import com.proyecto.supply_core.catalogo.repository.CategoriaProductoRepository;
import com.proyecto.supply_core.common.dto.PageResponse;
import com.proyecto.supply_core.common.exception.AccesoDenegadoException;
import com.proyecto.supply_core.common.exception.NegocioException;
import com.proyecto.supply_core.common.exception.NoEncontradoException;
import com.proyecto.supply_core.common.exception.ParametroInvalidoException;
import com.proyecto.supply_core.common.util.EmpresaUnica;
import com.proyecto.supply_core.common.util.Ordenamiento;
import com.proyecto.supply_core.catalogo.dto.CategoriaResponse;
import com.proyecto.supply_core.notificacion.enums.EventoNotificacion;
import com.proyecto.supply_core.notificacion.service.NotificacionService;
import com.proyecto.supply_core.proveedor.dto.MotivoRequest;
import com.proyecto.supply_core.proveedor.dto.ProveedorResponse;
import com.proyecto.supply_core.proveedor.dto.RegistroProveedorRequest;
import com.proyecto.supply_core.proveedor.dto.RegistroProveedorResponse;
import com.proyecto.supply_core.proveedor.entity.Proveedor;
import com.proyecto.supply_core.proveedor.enums.EstadoProveedor;
import com.proyecto.supply_core.proveedor.repository.ProveedorRepository;
import com.proyecto.supply_core.security.context.Roles;
import com.proyecto.supply_core.security.context.SesionActual;
import com.proyecto.supply_core.security.context.UsuarioAutenticado;
import com.proyecto.supply_core.usuario.entity.Usuario;
import com.proyecto.supply_core.usuario.repository.RolRepository;
import com.proyecto.supply_core.usuario.repository.UsuarioRepository;

/**
 * Autorregistro y aprobación de proveedores.
 * <p>Flujo: el proveedor se registra (PENDIENTE, usuario inactivo) → ADMIN_COMPRAS lo habilita
 * (usuarios activos) o lo rechaza con motivo. Un habilitado se puede suspender y volver a habilitar.
 * Cada cambio queda en la auditoría.</p>
 */
@Service
public class ProveedorService {

    private static final Logger log = LoggerFactory.getLogger(ProveedorService.class);
    /** Mensaje que ve el proveedor al terminar el registro. */
    static final String MENSAJE_REGISTRO =
            "Registro recibido. Podrá iniciar sesión cuando el área de compras apruebe su solicitud.";
    /** Campos por los que se puede ordenar el listado. */
    private static final Set<String> CAMPOS_ORDENABLES = Set.of("razonSocial", "nit", "estado", "creadoEn");

    private final ProveedorRepository proveedorRepository;
    private final CategoriaProductoRepository categoriaRepository;
    private final UsuarioRepository usuarioRepository;
    private final RolRepository rolRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditoriaService auditoriaService;
    private final NotificacionService notificacionService;

    /**
     * @param proveedorRepository acceso a proveedores
     * @param categoriaRepository acceso a categorías
     * @param usuarioRepository   acceso a usuarios (el usuario del portal)
     * @param rolRepository       acceso a roles (rol PROVEEDOR)
     * @param passwordEncoder     hash BCrypt
     * @param auditoriaService    registro de acciones sensibles
     * @param notificacionService aviso a ADMIN_COMPRAS de proveedores pendientes
     */
    public ProveedorService(ProveedorRepository proveedorRepository, CategoriaProductoRepository categoriaRepository,
            UsuarioRepository usuarioRepository, RolRepository rolRepository, PasswordEncoder passwordEncoder,
            AuditoriaService auditoriaService, NotificacionService notificacionService) {
        this.proveedorRepository = proveedorRepository;
        this.categoriaRepository = categoriaRepository;
        this.usuarioRepository = usuarioRepository;
        this.rolRepository = rolRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditoriaService = auditoriaService;
        this.notificacionService = notificacionService;
    }

    /**
     * Registra un proveedor desde el portal (ruta pública).
     * <p>El request ya llega validado: NIT y correo únicos, categorías activas, contraseña segura.
     * Si dos registros simultáneos usan el mismo NIT o correo, el UNIQUE de la BD responde 409.</p>
     *
     * @param req datos del proveedor y de su usuario del portal
     * @return id del proveedor y mensaje para mostrar
     */
    @Transactional
    public RegistroProveedorResponse registrar(RegistroProveedorRequest req) {
        Proveedor p = new Proveedor();
        p.setRazonSocial(req.razonSocial().trim());
        p.setNit(req.nit().trim());
        p.setCorreoContacto(normalizarCorreo(req.correo()));
        p.setTelefono(textoOpcional(req.telefono()));
        p.setDireccion(textoOpcional(req.direccion()));
        p.setCategorias(resolverCategorias(req.categoriaIds()));
        p.setEstado(EstadoProveedor.PENDIENTE);
        Proveedor guardado = proveedorRepository.save(p);

        Usuario u = new Usuario();
        u.setEmpresaId(EmpresaUnica.ID);
        u.setProveedorId(guardado.getId());
        u.setNombreCompleto(req.nombreContacto().trim());
        u.setCorreo(normalizarCorreo(req.correo()));
        u.setHashPassword(passwordEncoder.encode(req.password()));
        u.setActivo(false); // se activa al habilitar el proveedor
        u.getRoles().add(rolRepository.findByCodigo(Roles.PROVEEDOR)
                .orElseThrow(() -> new IllegalStateException("Falta el rol PROVEEDOR (V2)")));
        Usuario usuario = usuarioRepository.save(u);

        auditoriaService.exitoDe(usuario.getId(), AccionAuditoria.PROVEEDOR_REGISTRAR, EntidadAuditada.PROVEEDOR,
                guardado.getId(), "nit=" + guardado.getNit() + ", razonSocial=" + guardado.getRazonSocial());
        log.info("Proveedor {} registrado (NIT {}), pendiente de aprobación", guardado.getId(), guardado.getNit());
        notificacionService.notificarRol(Roles.ADMIN_COMPRAS, EventoNotificacion.PROVEEDOR_PENDIENTE,
                "Nuevo proveedor pendiente de aprobación: " + guardado.getRazonSocial() + " (NIT " + guardado.getNit() + ")");
        return new RegistroProveedorResponse(guardado.getId(), guardado.getEstado(), MENSAJE_REGISTRO);
    }

    /**
     * Categorías activas para el formulario de registro.
     *
     * @return categorías ordenadas por nombre
     */
    @Transactional(readOnly = true)
    public List<CategoriaResponse> categoriasDisponibles() {
        return categoriaRepository.findByActivoTrueOrderByNombreAsc().stream().map(CategoriaResponse::of).toList();
    }

    /**
     * Lista proveedores con filtros opcionales.
     *
     * @param texto    coincidencia en razón social o NIT
     * @param estado   estado
     * @param pageable página y orden (solo campos de {@link #CAMPOS_ORDENABLES})
     * @return página de proveedores
     * @throws ParametroInvalidoException si se pide ordenar por un campo no permitido
     */
    @Transactional(readOnly = true)
    public PageResponse<ProveedorResponse> buscar(String texto, EstadoProveedor estado, Pageable pageable) {
        String filtro = (texto == null || texto.isBlank()) ? null : texto.trim();
        return PageResponse.of(proveedorRepository.buscar(filtro, estado,
                Ordenamiento.validar(pageable, CAMPOS_ORDENABLES)), ProveedorResponse::of);
    }

    /**
     * Detalle de un proveedor.
     *
     * @param id id del proveedor
     * @return proveedor
     * @throws NoEncontradoException si no existe
     */
    @Transactional(readOnly = true)
    public ProveedorResponse obtener(Long id) {
        return ProveedorResponse.of(buscarEntidad(id));
    }

    /**
     * Datos del proveedor del usuario logueado (portal).
     *
     * @return su propio proveedor
     * @throws AccesoDenegadoException si el usuario no pertenece a un proveedor
     */
    @Transactional(readOnly = true)
    public ProveedorResponse miProveedor() {
        UsuarioAutenticado u = SesionActual.usuario();
        if (!u.esProveedor()) {
            throw new AccesoDenegadoException("Su usuario no pertenece a un proveedor");
        }
        return obtener(u.proveedorId());
    }

    /**
     * Habilita un proveedor PENDIENTE o SUSPENDIDO y activa sus usuarios del portal.
     *
     * @param id id del proveedor
     * @return proveedor habilitado
     * @throws NoEncontradoException si no existe
     * @throws NegocioException      si el estado actual no permite habilitarlo
     */
    @Transactional
    public ProveedorResponse habilitar(Long id) {
        Proveedor p = cambiarEstado(id, EstadoProveedor.HABILITADO, null);
        usuarioRepository.findByProveedorId(id).forEach(u -> u.setActivo(true));
        auditoriaService.exito(AccionAuditoria.PROVEEDOR_HABILITAR, EntidadAuditada.PROVEEDOR, id, "nit=" + p.getNit());
        return ProveedorResponse.of(p);
    }

    /**
     * Rechaza un proveedor PENDIENTE; sus usuarios siguen inactivos.
     *
     * @param id  id del proveedor
     * @param req motivo obligatorio
     * @return proveedor rechazado
     * @throws NoEncontradoException si no existe
     * @throws NegocioException      si no está PENDIENTE
     */
    @Transactional
    public ProveedorResponse rechazar(Long id, MotivoRequest req) {
        Proveedor p = cambiarEstado(id, EstadoProveedor.RECHAZADO, req.motivo().trim());
        auditoriaService.exito(AccionAuditoria.PROVEEDOR_RECHAZAR, EntidadAuditada.PROVEEDOR, id,
                "nit=" + p.getNit() + "; motivo=" + p.getMotivoEstado());
        return ProveedorResponse.of(p);
    }

    /**
     * Suspende un proveedor HABILITADO y desactiva sus usuarios del portal.
     *
     * @param id  id del proveedor
     * @param req motivo obligatorio
     * @return proveedor suspendido
     * @throws NoEncontradoException si no existe
     * @throws NegocioException      si no está HABILITADO
     */
    @Transactional
    public ProveedorResponse suspender(Long id, MotivoRequest req) {
        Proveedor p = cambiarEstado(id, EstadoProveedor.SUSPENDIDO, req.motivo().trim());
        usuarioRepository.findByProveedorId(id).forEach(u -> u.setActivo(false));
        auditoriaService.exito(AccionAuditoria.PROVEEDOR_SUSPENDER, EntidadAuditada.PROVEEDOR, id,
                "nit=" + p.getNit() + "; motivo=" + p.getMotivoEstado());
        return ProveedorResponse.of(p);
    }

    // ----------------------------------------------------------------- apoyo

    /**
     * Aplica una transición de estado validándola y registra quién y cuándo revisó.
     *
     * @param id      id del proveedor
     * @param destino nuevo estado
     * @param motivo  motivo (obligatorio para RECHAZADO y SUSPENDIDO; {@code null} al habilitar)
     * @return proveedor actualizado
     * @throws NegocioException si la transición no es válida
     */
    private Proveedor cambiarEstado(Long id, EstadoProveedor destino, String motivo) {
        Proveedor p = buscarEntidad(id);
        if (!p.getEstado().puedePasarA(destino)) {
            throw new NegocioException("TRANSICION_INVALIDA",
                    "Un proveedor " + p.getEstado() + " no puede pasar a " + destino);
        }
        p.setEstado(destino);
        p.setMotivoEstado(motivo);
        p.setRevisadoPorUsuarioId(SesionActual.usuario().id());
        p.setRevisadoEn(LocalDateTime.now());
        return p;
    }

    /**
     * Busca la entidad con sus categorías.
     *
     * @param id id del proveedor
     * @return entidad
     * @throws NoEncontradoException si no existe
     */
    private Proveedor buscarEntidad(Long id) {
        return proveedorRepository.findWithCategoriasById(id)
                .orElseThrow(() -> new NoEncontradoException("Proveedor", id));
    }

    /**
     * Convierte ids a entidades. {@code @CategoriasActivas} ya garantizó que existen y están activas.
     *
     * @param ids ids de categoría
     * @return categorías
     * @throws NegocioException si alguna no sirve (solo si se llamó al Service sin {@code @Valid})
     */
    private Set<CategoriaProducto> resolverCategorias(Set<Long> ids) {
        Set<CategoriaProducto> categorias = new HashSet<>(categoriaRepository.findByIdInAndActivoTrue(ids));
        if (categorias.size() != ids.size()) {
            throw new NegocioException("CATEGORIA_INVALIDA", "Hay categorías inexistentes o inactivas en " + ids);
        }
        return categorias;
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

    /**
     * Convierte texto vacío en {@code null} para no guardar cadenas en blanco.
     *
     * @param texto texto recibido
     * @return texto sin espacios extremos o {@code null}
     */
    private static String textoOpcional(String texto) {
        return texto == null || texto.isBlank() ? null : texto.trim();
    }
}
