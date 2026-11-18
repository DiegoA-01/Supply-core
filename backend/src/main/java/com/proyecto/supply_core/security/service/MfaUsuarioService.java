package com.proyecto.supply_core.security.service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.OptionalLong;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.proyecto.supply_core.auditoria.enums.AccionAuditoria;
import com.proyecto.supply_core.auditoria.enums.EntidadAuditada;
import com.proyecto.supply_core.auditoria.service.AuditoriaService;
import com.proyecto.supply_core.common.exception.NegocioException;
import com.proyecto.supply_core.common.exception.NoAutenticadoException;
import com.proyecto.supply_core.common.exception.NoEncontradoException;
import com.proyecto.supply_core.plataforma.service.TotpService;
import com.proyecto.supply_core.security.context.Roles;
import com.proyecto.supply_core.security.context.SesionActual;
import com.proyecto.supply_core.security.dto.ConfiguracionMfaResponse;
import com.proyecto.supply_core.security.dto.LoginResponse;
import com.proyecto.supply_core.security.jwt.JwtUtil;
import com.proyecto.supply_core.security.jwt.SesionProperties;
import com.proyecto.supply_core.usuario.entity.Usuario;
import com.proyecto.supply_core.usuario.repository.UsuarioRepository;

/**
 * Segundo factor (TOTP, compatible con Google Authenticator y similares) de los usuarios de una empresa.
 * <p>Es obligatorio para quien tenga el rol ADMIN_SISTEMA ({@code app.sesion.mfa-admin-obligatorio}) y voluntario
 * para los demás: quien lo activa queda obligado a usarlo en cada login. El código sirve una sola vez, los fallos
 * cuentan para el bloqueo de la cuenta igual que los de la contraseña, y si alguien pierde su teléfono otro
 * administrador (o el dueño de la plataforma) reinicia su MFA.</p>
 */
@Service
public class MfaUsuarioService {

    /** Mensaje único para cualquier código que no sirva (no se explica el motivo). */
    static final String CODIGO_INVALIDO = "Código inválido";

    private final UsuarioRepository usuarioRepository;
    private final TotpService totpService;
    private final JwtUtil jwtUtil;
    private final AuditoriaService auditoriaService;
    private final SesionUsuarioService sesiones;
    private final SesionProperties sesionProperties;
    private final boolean obligatorioParaAdmin;

    /**
     * @param usuarioRepository    usuarios de la empresa del contexto
     * @param totpService          generación y verificación de códigos
     * @param jwtUtil              emisor del desafío
     * @param auditoriaService     registro de activaciones, reinicios y fallos
     * @param sesiones             corte de sesiones al reiniciar el MFA
     * @param sesionProperties     intentos máximos y duración del bloqueo
     * @param obligatorioParaAdmin si ADMIN_SISTEMA debe usar siempre el segundo factor
     */
    public MfaUsuarioService(UsuarioRepository usuarioRepository, TotpService totpService, JwtUtil jwtUtil,
            AuditoriaService auditoriaService, SesionUsuarioService sesiones, SesionProperties sesionProperties,
            @Value("${app.sesion.mfa-admin-obligatorio:true}") boolean obligatorioParaAdmin) {
        this.usuarioRepository = usuarioRepository;
        this.totpService = totpService;
        this.jwtUtil = jwtUtil;
        this.auditoriaService = auditoriaService;
        this.sesiones = sesiones;
        this.sesionProperties = sesionProperties;
        this.obligatorioParaAdmin = obligatorioParaAdmin;
    }

    /**
     * Indica si el login de este usuario debe pasar por el segundo factor.
     *
     * @param usuario usuario que acertó la contraseña
     * @return {@code true} si ya lo activó o si es ADMIN_SISTEMA y la empresa lo exige
     */
    public boolean requiere(Usuario usuario) {
        return usuario.isMfaActivo() || (obligatorioParaAdmin && usuario.codigosRoles().contains(Roles.ADMIN_SISTEMA));
    }

    /**
     * Primer paso del login con MFA: en vez de la sesión entrega un desafío. Mientras no haya vinculado su app también
     * entrega el secreto para hacerlo.
     *
     * @param usuario usuario que acertó la contraseña (se le crea el secreto si aún no tiene)
     * @return respuesta de login sin sesión
     */
    public LoginResponse desafio(Usuario usuario) {
        boolean configurado = usuario.isMfaActivo();
        if (usuario.getMfaSecreto() == null) {
            usuario.setMfaSecreto(totpService.nuevoSecreto());
        }
        String desafio = jwtUtil.generarDesafioMfaEmpresa(usuario.getId(), usuario.getEmpresaId());
        return configurado ? LoginResponse.conDesafio(desafio, true, null, null)
                : LoginResponse.conDesafio(desafio, false, usuario.getMfaSecreto(),
                        totpService.uri(usuario.getCorreo(), usuario.getMfaSecreto()));
    }

    /**
     * Segundo paso del login: verifica el código. Si es el primero que acierta, deja el MFA activo. Debe correr dentro
     * de la empresa del desafío y en una transacción que conserve el contador de fallos.
     *
     * @param usuarioId usuario del desafío
     * @param codigo    código de 6 dígitos
     * @return el usuario, con el código ya registrado como usado
     * @throws NoAutenticadoException si el código no sirve, ya se usó o la cuenta está bloqueada o inactiva
     */
    public Usuario verificarCodigo(Long usuarioId, String codigo) {
        Usuario u = usuarioRepository.findWithRolesById(usuarioId).orElse(null);
        LocalDateTime ahora = LocalDateTime.now();
        if (u == null || !u.isActivo() || estaBloqueado(u, ahora) || u.getMfaSecreto() == null) {
            auditoriaService.fallo(usuarioId, AccionAuditoria.MFA_FALLO, EntidadAuditada.USUARIO, usuarioId,
                    "motivo=cuenta no disponible");
            throw new NoAutenticadoException(CODIGO_INVALIDO);
        }
        OptionalLong paso = totpService.verificar(u.getMfaSecreto(), codigo, u.getMfaUltimoPaso(), Instant.now());
        if (paso.isEmpty()) {
            auditoriaService.fallo(u.getId(), AccionAuditoria.MFA_FALLO, EntidadAuditada.USUARIO, u.getId(),
                    "correo=" + u.getCorreo() + "; motivo=código incorrecto o ya usado");
            registrarFallo(u, ahora);
            throw new NoAutenticadoException(CODIGO_INVALIDO);
        }
        u.setMfaUltimoPaso(paso.getAsLong());
        if (!u.isMfaActivo()) {
            u.setMfaActivo(true);
            auditoriaService.exitoDe(u.getId(), AccionAuditoria.MFA_ACTIVAR, EntidadAuditada.USUARIO, u.getId(),
                    "correo=" + u.getCorreo() + "; al iniciar sesión");
        }
        return u;
    }

    /**
     * El usuario de la sesión pide vincular su app de autenticación (MFA voluntario).
     *
     * @return secreto y dirección del QR
     * @throws NegocioException si ya tiene el MFA activo
     */
    @Transactional
    public ConfiguracionMfaResponse configurar() {
        Usuario u = propio();
        if (u.isMfaActivo()) {
            throw new NegocioException("MFA_YA_ACTIVO", "El segundo factor ya está activo");
        }
        if (u.getMfaSecreto() == null) {
            u.setMfaSecreto(totpService.nuevoSecreto());
        }
        return new ConfiguracionMfaResponse(u.getMfaSecreto(), totpService.uri(u.getCorreo(), u.getMfaSecreto()));
    }

    /**
     * El usuario de la sesión confirma con un código que su app quedó vinculada: desde ahí el login lo exige.
     *
     * @param codigo código de 6 dígitos que muestra la app
     * @throws NegocioException si ya está activo, no pidió el secreto o el código no sirve
     */
    @Transactional
    public void activar(String codigo) {
        Usuario u = propio();
        if (u.isMfaActivo()) {
            throw new NegocioException("MFA_YA_ACTIVO", "El segundo factor ya está activo");
        }
        if (u.getMfaSecreto() == null) {
            throw new NegocioException("MFA_SIN_CONFIGURAR", "Primero pide el secreto para vincular tu app");
        }
        OptionalLong paso = totpService.verificar(u.getMfaSecreto(), codigo, u.getMfaUltimoPaso(), Instant.now());
        if (paso.isEmpty()) {
            throw new NegocioException("CODIGO_MFA_INVALIDO", "El código no es correcto; revisa la hora de tu teléfono");
        }
        u.setMfaUltimoPaso(paso.getAsLong());
        u.setMfaActivo(true);
        auditoriaService.exito(AccionAuditoria.MFA_ACTIVAR, EntidadAuditada.USUARIO, u.getId(),
                "correo=" + u.getCorreo());
    }

    /**
     * Un administrador quita el segundo factor de otro usuario (perdió su teléfono). Se corta toda su sesión; si es
     * ADMIN_SISTEMA tendrá que vincular una app nueva en su próximo login.
     *
     * @param id usuario al que se le reinicia el MFA
     * @throws NegocioException      si intenta reiniciar el suyo propio
     * @throws NoEncontradoException si el usuario no existe en la empresa
     */
    @Transactional
    public void reiniciar(Long id) {
        if (id.equals(SesionActual.usuario().id())) {
            throw new NegocioException("AUTO_REINICIO_MFA", "No puede reiniciar su propio segundo factor");
        }
        Usuario u = usuarioRepository.findWithRolesById(id).orElseThrow(() -> new NoEncontradoException("Usuario", id));
        vaciar(u);
        auditoriaService.exito(AccionAuditoria.MFA_DESACTIVAR, EntidadAuditada.USUARIO, id, "correo=" + u.getCorreo());
    }

    /**
     * Quita el segundo factor de un usuario y le corta las sesiones. Lo usa también la consola de la plataforma.
     *
     * @param u usuario cargado dentro de su empresa
     */
    public void vaciar(Usuario u) {
        u.setMfaSecreto(null);
        u.setMfaActivo(false);
        u.setMfaUltimoPaso(null);
        sesiones.revocarSesiones(u);
    }

    // ----------------------------------------------------------------- apoyo

    /**
     * Usuario de la sesión actual, cargado de la base.
     *
     * @return entidad del usuario
     */
    private Usuario propio() {
        Long id = SesionActual.usuario().id();
        return usuarioRepository.findWithRolesById(id).orElseThrow(() -> new NoEncontradoException("Usuario", id));
    }

    /**
     * Indica si la cuenta sigue bloqueada.
     *
     * @param u     usuario
     * @param ahora momento actual
     * @return {@code true} si el bloqueo no ha terminado
     */
    private static boolean estaBloqueado(Usuario u, LocalDateTime ahora) {
        return u.getBloqueadoHasta() != null && u.getBloqueadoHasta().isAfter(ahora);
    }

    /**
     * Suma un fallo de código y bloquea la cuenta al llegar al máximo de intentos seguidos.
     *
     * @param u     usuario que falló
     * @param ahora momento del intento
     */
    private void registrarFallo(Usuario u, LocalDateTime ahora) {
        int fallos = u.getIntentosFallidos() + 1;
        if (fallos >= sesionProperties.maxIntentos()) {
            u.setIntentosFallidos(0);
            u.setBloqueadoHasta(ahora.plusMinutes(sesionProperties.bloqueoMinutos()));
            auditoriaService.fallo(u.getId(), AccionAuditoria.CUENTA_BLOQUEADA, EntidadAuditada.USUARIO, u.getId(),
                    "bloqueada " + sesionProperties.bloqueoMinutos() + " min tras " + fallos + " intentos");
        } else {
            u.setIntentosFallidos(fallos);
        }
    }
}
