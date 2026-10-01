package com.proyecto.supply_core.security.service;

import java.time.LocalDateTime;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.proyecto.supply_core.auditoria.enums.AccionAuditoria;
import com.proyecto.supply_core.auditoria.enums.EntidadAuditada;
import com.proyecto.supply_core.auditoria.service.AuditoriaService;
import com.proyecto.supply_core.common.exception.NoAutenticadoException;
import com.proyecto.supply_core.proveedor.entity.Proveedor;
import com.proyecto.supply_core.proveedor.enums.EstadoProveedor;
import com.proyecto.supply_core.proveedor.repository.ProveedorRepository;
import com.proyecto.supply_core.security.context.UsuarioAutenticado;
import com.proyecto.supply_core.security.dto.LoginRequest;
import com.proyecto.supply_core.security.dto.LoginResponse;
import com.proyecto.supply_core.security.dto.UsuarioSesionResponse;
import com.proyecto.supply_core.security.jwt.JwtUtil;
import com.proyecto.supply_core.usuario.entity.Usuario;
import com.proyecto.supply_core.usuario.repository.UsuarioRepository;

/** UC-01 Iniciar sesión: valida credenciales, audita el intento y emite el JWT. */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    /** Mensaje único para correo inexistente o clave errada (no revela cuál falló). */
    static final String CREDENCIALES_INVALIDAS = "Credenciales inválidas";
    /** Mensaje para cuenta desactivada (solo se muestra si la clave era correcta). */
    static final String CUENTA_INACTIVA = "Cuenta inactiva, contacte al administrador";

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final AuditoriaService auditoriaService;
    private final ProveedorRepository proveedorRepository;
    /** Hash de relleno: se compara aunque el correo no exista para que ambos casos tarden igual. */
    private final String hashFicticio;

    /**
     * @param usuarioRepository   acceso a usuarios
     * @param passwordEncoder     verificador BCrypt
     * @param jwtUtil             emisor de tokens
     * @param auditoriaService    registro de intentos de login (éxito y fallo)
     * @param proveedorRepository estado del proveedor de los usuarios del portal
     */
    public AuthService(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder, JwtUtil jwtUtil,
            AuditoriaService auditoriaService, ProveedorRepository proveedorRepository) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.auditoriaService = auditoriaService;
        this.proveedorRepository = proveedorRepository;
        this.hashFicticio = passwordEncoder.encode("supply-core-relleno-de-tiempo");
    }

    /**
     * Autentica al usuario, registra su último acceso y devuelve el token.
     *
     * @param request correo y contraseña ya validados en formato
     * @return token y datos de sesión
     * @throws NoAutenticadoException si las credenciales son inválidas o la cuenta está inactiva
     */
    @Transactional
    public LoginResponse login(LoginRequest request) {
        String correo = request.correo().trim().toLowerCase(Locale.ROOT);
        Usuario usuario = usuarioRepository.findByCorreoIgnoreCase(correo).orElse(null);

        // Mismo mensaje y mismo tiempo si el correo no existe o la clave falla: no se revela cuál dato está mal
        String hash = usuario == null ? hashFicticio : usuario.getHashPassword();
        boolean claveCorrecta = passwordEncoder.matches(request.password(), hash);
        if (usuario == null || !claveCorrecta) {
            Long id = usuario == null ? null : usuario.getId();
            String motivo = usuario == null ? "correo no registrado" : "contraseña incorrecta";
            log.info("Login fallido para {} ({})", correo, motivo);
            auditoriaService.fallo(id, AccionAuditoria.LOGIN, EntidadAuditada.USUARIO, id,
                    "correo=" + correo + "; motivo=" + motivo);
            throw new NoAutenticadoException(CREDENCIALES_INVALIDAS);
        }
        // Solo se informa el estado de la cuenta a quien demostró conocer la contraseña.
        // Usuario de proveedor: manda el estado del proveedor (aunque alguien reactive el usuario a mano).
        if (usuario.getProveedorId() != null) {
            EstadoProveedor estado = proveedorRepository.findById(usuario.getProveedorId())
                    .map(Proveedor::getEstado).orElse(EstadoProveedor.RECHAZADO);
            if (estado != EstadoProveedor.HABILITADO) {
                auditoriaService.fallo(usuario.getId(), AccionAuditoria.LOGIN, EntidadAuditada.USUARIO,
                        usuario.getId(), "correo=" + correo + "; motivo=proveedor " + estado);
                throw new NoAutenticadoException(mensajeProveedor(estado));
            }
        }
        if (!usuario.isActivo()) {
            auditoriaService.fallo(usuario.getId(), AccionAuditoria.LOGIN, EntidadAuditada.USUARIO,
                    usuario.getId(), "correo=" + correo + "; motivo=cuenta inactiva");
            throw new NoAutenticadoException(CUENTA_INACTIVA);
        }

        usuario.setUltimoAcceso(LocalDateTime.now());
        auditoriaService.exitoDe(usuario.getId(), AccionAuditoria.LOGIN, EntidadAuditada.USUARIO,
                usuario.getId(), "correo=" + correo);

        UsuarioAutenticado sesion = new UsuarioAutenticado(usuario.getId(), usuario.getCorreo(),
                usuario.getNombreCompleto(), usuario.codigosRoles(), usuario.getProveedorId());
        return new LoginResponse(jwtUtil.generar(sesion), "Bearer", jwtUtil.getExpiracionSegundos(),
                UsuarioSesionResponse.of(sesion));
    }

    /**
     * Mensaje para un usuario de proveedor que aún no puede entrar.
     *
     * @param estado estado del proveedor (distinto de HABILITADO)
     * @return texto para el usuario
     */
    static String mensajeProveedor(EstadoProveedor estado) {
        return switch (estado) {
            case PENDIENTE -> "Su registro de proveedor está pendiente de aprobación";
            case RECHAZADO -> "Su registro de proveedor fue rechazado, contacte al área de compras";
            case SUSPENDIDO -> "Su cuenta de proveedor está suspendida, contacte al área de compras";
            case HABILITADO -> throw new IllegalArgumentException("Un proveedor HABILITADO sí puede entrar");
        };
    }
}
