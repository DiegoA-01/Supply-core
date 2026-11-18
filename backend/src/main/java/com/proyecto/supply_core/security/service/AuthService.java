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
import com.proyecto.supply_core.plataforma.service.AccesoEmpresaService;
import com.proyecto.supply_core.proveedor.repository.ProveedorRepository;
import com.proyecto.supply_core.security.context.UsuarioAutenticado;
import com.proyecto.supply_core.security.dto.LoginRequest;
import com.proyecto.supply_core.security.dto.LoginResponse;
import com.proyecto.supply_core.security.dto.UsuarioSesionResponse;
import com.proyecto.supply_core.security.jwt.DesafioMfaEmpresa;
import com.proyecto.supply_core.security.jwt.JwtUtil;
import com.proyecto.supply_core.security.jwt.SesionProperties;
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
    private final RefreshTokenService refreshTokenService;
    private final SesionProperties sesionProperties;
    private final AccesoEmpresaService accesoEmpresa;
    private final MfaUsuarioService mfa;
    /** Hash de relleno: se compara aunque el correo no exista para que ambos casos tarden igual. */
    private final String hashFicticio;

    /**
     * @param usuarioRepository   acceso a usuarios
     * @param passwordEncoder     verificador BCrypt
     * @param jwtUtil             emisor de tokens
     * @param auditoriaService    registro de intentos de login (éxito y fallo)
     * @param proveedorRepository estado del proveedor de los usuarios del portal
     * @param refreshTokenService sesiones renovables
     * @param sesionProperties    intentos máximos y duración del bloqueo
     * @param accesoEmpresa       suscripción de la empresa (suspendida o cancelada no entra)
     * @param mfa                 segundo factor de quien debe usarlo
     */
    public AuthService(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder, JwtUtil jwtUtil,
            AuditoriaService auditoriaService, ProveedorRepository proveedorRepository,
            RefreshTokenService refreshTokenService, SesionProperties sesionProperties,
            AccesoEmpresaService accesoEmpresa, MfaUsuarioService mfa) {
        this.accesoEmpresa = accesoEmpresa;
        this.mfa = mfa;
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.auditoriaService = auditoriaService;
        this.proveedorRepository = proveedorRepository;
        this.refreshTokenService = refreshTokenService;
        this.sesionProperties = sesionProperties;
        this.hashFicticio = passwordEncoder.encode("supply-core-relleno-de-tiempo");
    }

    /**
     * Autentica al usuario, registra su último acceso y devuelve el token.
     *
     * @param request correo y contraseña ya validados en formato
     * @return token y datos de sesión
     * @throws NoAutenticadoException si las credenciales son inválidas o la cuenta está inactiva
     */
    @Transactional(noRollbackFor = NoAutenticadoException.class) // el contador de fallos debe quedar guardado
    public LoginResponse login(LoginRequest request) {
        String correo = request.correo().trim().toLowerCase(Locale.ROOT);
        Usuario usuario = usuarioRepository.findByCorreoIgnoreCase(correo).orElse(null);
        LocalDateTime ahora = LocalDateTime.now();

        // Mismo mensaje y mismo tiempo si el correo no existe o la clave falla: no se revela cuál dato está mal
        String hash = usuario == null ? hashFicticio : usuario.getHashPassword();
        boolean claveCorrecta = passwordEncoder.matches(request.password(), hash);

        // Cuenta bloqueada: mismo mensaje genérico (no confirma que el correo existe), aunque la clave sea correcta
        if (usuario != null && usuario.getBloqueadoHasta() != null && usuario.getBloqueadoHasta().isAfter(ahora)) {
            auditoriaService.fallo(usuario.getId(), AccionAuditoria.LOGIN, EntidadAuditada.USUARIO,
                    usuario.getId(), "correo=" + correo + "; motivo=cuenta bloqueada hasta "
                            + usuario.getBloqueadoHasta());
            throw new NoAutenticadoException(CREDENCIALES_INVALIDAS);
        }
        if (usuario == null || !claveCorrecta) {
            Long id = usuario == null ? null : usuario.getId();
            String motivo = usuario == null ? "correo no registrado" : "contraseña incorrecta";
            log.info("Login fallido para {} ({})", correo, motivo);
            auditoriaService.fallo(id, AccionAuditoria.LOGIN, EntidadAuditada.USUARIO, id,
                    "correo=" + correo + "; motivo=" + motivo);
            if (usuario != null) {
                registrarFallo(usuario, ahora);
            }
            throw new NoAutenticadoException(CREDENCIALES_INVALIDAS);
        }
        exigirPuedeEntrar(usuario, correo);

        if (mfa.requiere(usuario)) {
            // contraseña correcta pero todavía no hay sesión: falta el código del segundo factor
            auditoriaService.exitoDe(usuario.getId(), AccionAuditoria.LOGIN, EntidadAuditada.USUARIO,
                    usuario.getId(), "correo=" + correo + "; falta el segundo factor");
            return mfa.desafio(usuario);
        }
        usuario.setUltimoAcceso(ahora);
        usuario.setIntentosFallidos(0);
        usuario.setBloqueadoHasta(null);
        auditoriaService.exitoDe(usuario.getId(), AccionAuditoria.LOGIN, EntidadAuditada.USUARIO,
                usuario.getId(), "correo=" + correo);
        return emitirSesion(usuario, refreshTokenService.emitir(usuario.getId()));
    }

    /**
     * Lee el desafío del primer paso para saber en qué empresa continuar el login.
     *
     * @param desafio desafío entregado al acertar la contraseña
     * @return usuario y empresa del desafío
     * @throws NoAutenticadoException si no es un desafío de empresa, está alterado o venció
     */
    public DesafioMfaEmpresa leerDesafio(String desafio) {
        return jwtUtil.validarDesafioMfaEmpresa(desafio);
    }

    /**
     * Segundo paso del login con MFA: verifica el código y abre la sesión. Debe correr dentro de la empresa del
     * desafío.
     *
     * @param usuarioId usuario del desafío
     * @param codigo    código de 6 dígitos
     * @return token y datos de sesión
     * @throws NoAutenticadoException si el código no sirve o la cuenta no puede entrar
     */
    @Transactional(noRollbackFor = NoAutenticadoException.class) // los fallos de código deben quedar contados
    public LoginResponse verificarMfa(Long usuarioId, String codigo) {
        Usuario usuario = mfa.verificarCodigo(usuarioId, codigo);
        exigirPuedeEntrar(usuario, usuario.getCorreo());
        usuario.setUltimoAcceso(LocalDateTime.now());
        usuario.setIntentosFallidos(0);
        usuario.setBloqueadoHasta(null);
        auditoriaService.exitoDe(usuario.getId(), AccionAuditoria.LOGIN, EntidadAuditada.USUARIO,
                usuario.getId(), "correo=" + usuario.getCorreo() + "; con segundo factor");
        return emitirSesion(usuario, refreshTokenService.emitir(usuario.getId()));
    }

    /**
     * Login con un código de empresa que no existe: gasta el mismo tiempo que una clave mala y responde igual,
     * para que no se pueda averiguar qué empresas existen.
     *
     * @param request credenciales recibidas
     * @return nunca devuelve: siempre lanza
     * @throws NoAutenticadoException siempre
     */
    public LoginResponse loginEmpresaDesconocida(LoginRequest request) {
        passwordEncoder.matches(request.password(), hashFicticio);
        log.info("Login fallido: empresa desconocida ({})", request.empresa());
        auditoriaService.fallo(null, AccionAuditoria.LOGIN, EntidadAuditada.USUARIO, null,
                "empresa=" + request.empresa() + "; motivo=empresa no registrada");
        throw new NoAutenticadoException(CREDENCIALES_INVALIDAS);
    }

    /**
     * Renueva la sesión: canjea el refresh token por un JWT nuevo y un refresh token nuevo.
     * Revalida al usuario, así que una cuenta desactivada o un proveedor suspendido pierden el acceso
     * como máximo al vencer el JWT actual.
     *
     * @param refreshToken valor entregado en el login o en la última renovación
     * @return JWT y refresh token nuevos
     * @throws NoAutenticadoException si el token no sirve o el usuario ya no puede entrar
     */
    @Transactional(noRollbackFor = NoAutenticadoException.class)
    public LoginResponse refrescar(String refreshToken) {
        RefreshTokenService.Renovacion r = refreshTokenService.rotar(refreshToken);
        Usuario usuario = usuarioRepository.findWithRolesById(r.usuarioId()).orElse(null);
        if (usuario == null) {
            refreshTokenService.cerrarTodas(r.usuarioId());
            throw new NoAutenticadoException(RefreshTokenService.SESION_INVALIDA);
        }
        try {
            exigirPuedeEntrar(usuario, usuario.getCorreo());
        } catch (NoAutenticadoException e) {
            refreshTokenService.cerrarTodas(usuario.getId());
            throw e;
        }
        return emitirSesion(usuario, r.refreshToken());
    }

    /**
     * Cierra la sesión a la que pertenece el refresh token.
     *
     * @param refreshToken valor de la sesión que se cierra
     * @param usuarioId    usuario autenticado que la cierra (para auditar)
     */
    @Transactional
    public void cerrarSesion(String refreshToken, Long usuarioId) {
        refreshTokenService.cerrarSesion(refreshToken);
        auditoriaService.exitoDe(usuarioId, AccionAuditoria.LOGOUT, EntidadAuditada.USUARIO, usuarioId, "una sesión");
    }

    /**
     * Cierra todas las sesiones del usuario (todos sus dispositivos).
     *
     * @param usuarioId usuario autenticado
     */
    @Transactional
    public void cerrarTodas(Long usuarioId) {
        refreshTokenService.cerrarTodas(usuarioId);
        auditoriaService.exitoDe(usuarioId, AccionAuditoria.LOGOUT, EntidadAuditada.USUARIO, usuarioId,
                "todas las sesiones");
    }

    /**
     * Suma un fallo y bloquea la cuenta al llegar al máximo de intentos seguidos.
     *
     * @param usuario usuario que falló la clave
     * @param ahora   momento del intento
     */
    private void registrarFallo(Usuario usuario, LocalDateTime ahora) {
        int fallos = usuario.getIntentosFallidos() + 1;
        if (fallos >= sesionProperties.maxIntentos()) {
            usuario.setIntentosFallidos(0);
            usuario.setBloqueadoHasta(ahora.plusMinutes(sesionProperties.bloqueoMinutos()));
            auditoriaService.fallo(usuario.getId(), AccionAuditoria.CUENTA_BLOQUEADA, EntidadAuditada.USUARIO,
                    usuario.getId(), "bloqueada " + sesionProperties.bloqueoMinutos() + " min tras " + fallos
                            + " intentos fallidos");
        } else {
            usuario.setIntentosFallidos(fallos);
        }
    }

    /**
     * Verifica que el proveedor (si lo es) esté habilitado y la cuenta activa.
     * Solo se informa el estado a quien demostró conocer la contraseña o tenía una sesión válida.
     *
     * @param usuario usuario que quiere entrar
     * @param correo  correo para el detalle de auditoría
     * @throws NoAutenticadoException si el proveedor no está habilitado o la cuenta está inactiva
     */
    private void exigirPuedeEntrar(Usuario usuario, String correo) {
        // Empresa suspendida o cancelada: solo se le dice a quien ya demostró ser un usuario de esa empresa
        accesoEmpresa.exigirPuedeEntrar(usuario.getEmpresaId());
        // Usuario de proveedor: manda el estado del proveedor (aunque alguien reactive el usuario a mano)
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
    }

    /**
     * Arma la respuesta de sesión con un JWT nuevo.
     *
     * @param usuario      usuario autenticado
     * @param refreshToken refresh token que se entrega al cliente
     * @return respuesta de login
     */
    private LoginResponse emitirSesion(Usuario usuario, String refreshToken) {
        UsuarioAutenticado sesion = new UsuarioAutenticado(usuario.getId(), usuario.getCorreo(),
                usuario.getNombreCompleto(), usuario.codigosRoles(), usuario.getProveedorId(), usuario.getEmpresaId());
        return new LoginResponse(jwtUtil.generar(sesion, usuario.getVersionSesion()), "Bearer",
                jwtUtil.getExpiracionSegundos(),
                UsuarioSesionResponse.of(sesion), refreshToken);
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
