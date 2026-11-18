package com.proyecto.supply_core.plataforma.service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.OptionalLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.proyecto.supply_core.common.exception.NoAutenticadoException;
import com.proyecto.supply_core.plataforma.dto.DesafioMfaResponse;
import com.proyecto.supply_core.plataforma.dto.LoginPlataformaRequest;
import com.proyecto.supply_core.plataforma.dto.MfaRequest;
import com.proyecto.supply_core.plataforma.dto.SesionPlataformaResponse;
import com.proyecto.supply_core.plataforma.entity.UsuarioPlataforma;
import com.proyecto.supply_core.plataforma.enums.AccionPlataforma;
import com.proyecto.supply_core.plataforma.repository.UsuarioPlataformaRepository;
import com.proyecto.supply_core.security.context.PlataformaAutenticada;
import com.proyecto.supply_core.security.jwt.JwtUtil;
import com.proyecto.supply_core.security.jwt.SesionProperties;

/**
 * Login del dueño de la plataforma en dos pasos, con MFA obligatorio:
 * <ol>
 *   <li><b>Contraseña:</b> si es correcta se entrega un desafío de 5 minutos. Si la persona aún no vinculó su app de
 *       autenticación, se le entrega también el secreto para vincularla.</li>
 *   <li><b>Código de MFA:</b> el desafío más el código de 6 dígitos de la app dan la sesión. El primer código válido
 *       confirma el MFA. Un código no se acepta dos veces.</li>
 * </ol>
 * Un correo inexistente, una contraseña mala o una cuenta bloqueada responden igual y tardan igual. Los fallos de
 * contraseña y de código suman al mismo contador y bloquean la cuenta igual que el login de las empresas. Todo queda en
 * la auditoría global.
 */
@Service
public class PlataformaAuthService {

    private static final Logger log = LoggerFactory.getLogger(PlataformaAuthService.class);
    /** Mensaje único para correo inexistente, clave errada o cuenta bloqueada. */
    static final String CREDENCIALES_INVALIDAS = "Credenciales inválidas";
    /** Mensaje para un código de MFA que no sirve. */
    static final String CODIGO_INVALIDO = "Código inválido";

    private final UsuarioPlataformaRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final TotpService totpService;
    private final PlataformaAuditoriaService auditoria;
    private final SesionProperties sesionProperties;
    /** Hash de relleno: se compara aunque el correo no exista para que ambos casos tarden igual. */
    private final String hashFicticio;

    /**
     * @param usuarioRepository usuarios de la plataforma
     * @param passwordEncoder   verificador BCrypt
     * @param jwtUtil           emisor de desafíos y sesiones
     * @param totpService       códigos de un solo uso
     * @param auditoria         auditoría global
     * @param sesionProperties  intentos máximos y duración del bloqueo
     */
    public PlataformaAuthService(UsuarioPlataformaRepository usuarioRepository, PasswordEncoder passwordEncoder,
            JwtUtil jwtUtil, TotpService totpService, PlataformaAuditoriaService auditoria,
            SesionProperties sesionProperties) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.totpService = totpService;
        this.auditoria = auditoria;
        this.sesionProperties = sesionProperties;
        this.hashFicticio = passwordEncoder.encode("supply-core-relleno-de-tiempo-plataforma");
    }

    /**
     * Primer paso: verifica la contraseña y entrega el desafío de MFA.
     *
     * @param req correo y contraseña ya validados en formato
     * @return desafío y, si falta vincular la app, el secreto para hacerlo
     * @throws NoAutenticadoException si las credenciales no sirven o la cuenta está bloqueada o inactiva
     */
    @Transactional(noRollbackFor = NoAutenticadoException.class) // el contador de fallos debe quedar guardado
    public DesafioMfaResponse login(LoginPlataformaRequest req) {
        String correo = req.correo().trim().toLowerCase(Locale.ROOT);
        UsuarioPlataforma u = usuarioRepository.findByCorreoIgnoreCase(correo).orElse(null);
        LocalDateTime ahora = LocalDateTime.now();
        boolean claveCorrecta = passwordEncoder.matches(req.password(), u == null ? hashFicticio : u.getHashPassword());

        if (u == null || !claveCorrecta || estaBloqueado(u, ahora) || !u.isActivo()) {
            Long id = u == null ? null : u.getId();
            String motivo = u == null ? "correo no registrado" : !claveCorrecta ? "contraseña incorrecta"
                    : estaBloqueado(u, ahora) ? "cuenta bloqueada" : "cuenta inactiva";
            log.info("Login de plataforma fallido para {} ({})", correo, motivo);
            auditoria.registrar(id, AccionPlataforma.LOGIN, null, false, "correo=" + correo + "; motivo=" + motivo);
            if (u != null && !claveCorrecta && !estaBloqueado(u, ahora)) {
                registrarFallo(u, ahora);
            }
            throw new NoAutenticadoException(CREDENCIALES_INVALIDAS);
        }
        boolean configurado = u.isMfaActivo();
        if (u.getMfaSecreto() == null) {
            u.setMfaSecreto(totpService.nuevoSecreto());
        }
        auditoria.registrar(u.getId(), AccionPlataforma.LOGIN, null, true,
                "correo=" + correo + "; mfaConfigurado=" + configurado);
        String desafio = jwtUtil.generarDesafioMfa(u.getId());
        return configurado ? new DesafioMfaResponse(desafio, true, null, null)
                : new DesafioMfaResponse(desafio, false, u.getMfaSecreto(), totpService.uri(u.getCorreo(), u.getMfaSecreto()));
    }

    /**
     * Segundo paso: verifica el código de MFA y entrega la sesión.
     *
     * @param req desafío y código ya validados en formato
     * @return sesión de la plataforma
     * @throws NoAutenticadoException si el desafío o el código no sirven, o la cuenta está bloqueada o inactiva
     */
    @Transactional(noRollbackFor = NoAutenticadoException.class)
    public SesionPlataformaResponse verificarMfa(MfaRequest req) {
        Long id = jwtUtil.validarDesafioMfa(req.desafio());
        UsuarioPlataforma u = usuarioRepository.findById(id).orElse(null);
        LocalDateTime ahora = LocalDateTime.now();
        if (u == null || !u.isActivo() || estaBloqueado(u, ahora) || u.getMfaSecreto() == null) {
            auditoria.registrar(id, AccionPlataforma.MFA, null, false, "motivo=cuenta no disponible");
            throw new NoAutenticadoException(CODIGO_INVALIDO);
        }
        OptionalLong paso = totpService.verificar(u.getMfaSecreto(), req.codigo(), u.getMfaUltimoPaso(), Instant.now());
        if (paso.isEmpty()) {
            auditoria.registrar(u.getId(), AccionPlataforma.MFA, null, false, "motivo=código incorrecto o ya usado");
            registrarFallo(u, ahora);
            throw new NoAutenticadoException(CODIGO_INVALIDO);
        }
        u.setMfaUltimoPaso(paso.getAsLong());
        if (!u.isMfaActivo()) {
            u.setMfaActivo(true);
            auditoria.registrar(u.getId(), AccionPlataforma.MFA_CONFIGURAR, null, true, "correo=" + u.getCorreo());
        }
        u.setUltimoAcceso(ahora);
        u.setIntentosFallidos(0);
        u.setBloqueadoHasta(null);
        auditoria.registrar(u.getId(), AccionPlataforma.MFA, null, true, "correo=" + u.getCorreo());
        String token = jwtUtil.generarPlataforma(new PlataformaAutenticada(u.getId(), u.getCorreo(), u.getNombre()));
        return new SesionPlataformaResponse(token, "Bearer", jwtUtil.getExpiracionSegundos(), u.getId(), u.getCorreo(),
                u.getNombre());
    }

    // ----------------------------------------------------------------- apoyo

    /**
     * Indica si la cuenta sigue bloqueada.
     *
     * @param u     usuario
     * @param ahora momento actual
     * @return {@code true} si el bloqueo no ha terminado
     */
    private static boolean estaBloqueado(UsuarioPlataforma u, LocalDateTime ahora) {
        return u.getBloqueadoHasta() != null && u.getBloqueadoHasta().isAfter(ahora);
    }

    /**
     * Suma un fallo (de contraseña o de código) y bloquea la cuenta al llegar al máximo de intentos seguidos.
     *
     * @param u     usuario que falló
     * @param ahora momento del intento
     */
    private void registrarFallo(UsuarioPlataforma u, LocalDateTime ahora) {
        int fallos = u.getIntentosFallidos() + 1;
        if (fallos >= sesionProperties.maxIntentos()) {
            u.setIntentosFallidos(0);
            u.setBloqueadoHasta(ahora.plusMinutes(sesionProperties.bloqueoMinutos()));
            auditoria.registrar(u.getId(), AccionPlataforma.LOGIN, null, false,
                    "cuenta bloqueada " + sesionProperties.bloqueoMinutos() + " min tras " + fallos + " intentos");
        } else {
            u.setIntentosFallidos(fallos);
        }
    }
}
