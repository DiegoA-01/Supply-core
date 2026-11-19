package com.proyecto.supply_core.caja.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Objects;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.proyecto.supply_core.auditoria.enums.AccionAuditoria;
import com.proyecto.supply_core.auditoria.enums.EntidadAuditada;
import com.proyecto.supply_core.auditoria.service.AuditoriaService;
import com.proyecto.supply_core.caja.dto.AutorizacionRequest;
import com.proyecto.supply_core.caja.dto.AutorizacionResponse;
import com.proyecto.supply_core.caja.dto.PinRequest;
import com.proyecto.supply_core.caja.entity.AutorizacionSupervisor;
import com.proyecto.supply_core.caja.entity.TurnoCaja;
import com.proyecto.supply_core.caja.enums.AccionAutorizada;
import com.proyecto.supply_core.caja.enums.EstadoTurno;
import com.proyecto.supply_core.caja.repository.AutorizacionSupervisorRepository;
import com.proyecto.supply_core.caja.repository.TurnoCajaRepository;
import com.proyecto.supply_core.common.exception.NegocioException;
import com.proyecto.supply_core.common.exception.NoEncontradoException;
import com.proyecto.supply_core.security.context.Roles;
import com.proyecto.supply_core.security.context.SesionActual;
import com.proyecto.supply_core.usuario.entity.Rol;
import com.proyecto.supply_core.usuario.entity.Usuario;
import com.proyecto.supply_core.usuario.repository.UsuarioRepository;

/**
 * PIN de supervisor y autorizaciones de un solo uso (spec 003).
 * <ul>
 *   <li>El supervisor escribe su correo y su PIN en la pantalla del cajero; el backend devuelve un token que vale
 *       para una acción, de un turno y de quien lo pidió, durante {@value #MINUTOS_VIGENCIA} minutos.</li>
 *   <li>Nadie autoriza lo suyo: el supervisor no puede ser quien pide.</li>
 *   <li>El PIN se bloquea {@value #MINUTOS_BLOQUEO} minutos tras {@value #MAX_INTENTOS} fallos seguidos.</li>
 *   <li>Solo se guardan el hash BCrypt del PIN y el SHA-256 del token; el PIN nunca va en una dirección web.</li>
 * </ul>
 */
@Service
public class AutorizacionService {

    /** Minutos que vale una autorización. */
    static final int MINUTOS_VIGENCIA = 2;
    /** Fallos seguidos del PIN que lo bloquean. */
    static final int MAX_INTENTOS = 5;
    /** Minutos de bloqueo del PIN. */
    static final int MINUTOS_BLOQUEO = 15;

    private final AutorizacionSupervisorRepository autorizacionRepository;
    private final TurnoCajaRepository turnoRepository;
    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditoriaService auditoriaService;
    private final SecureRandom aleatorio = new SecureRandom();

    /**
     * @param autorizacionRepository autorizaciones
     * @param turnoRepository        turnos
     * @param usuarioRepository      supervisores y su PIN
     * @param passwordEncoder        BCrypt para el PIN y la contraseña
     * @param auditoriaService       registro de autorizaciones y fallos
     */
    public AutorizacionService(AutorizacionSupervisorRepository autorizacionRepository,
            TurnoCajaRepository turnoRepository, UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder,
            AuditoriaService auditoriaService) {
        this.autorizacionRepository = autorizacionRepository;
        this.turnoRepository = turnoRepository;
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditoriaService = auditoriaService;
    }

    /**
     * Valida correo y PIN del supervisor y crea un permiso de un solo uso. Los fallos del PIN se guardan aunque la
     * petición falle (para que el bloqueo funcione).
     *
     * @param req correo, PIN, acción, turno y referencia
     * @return token y vencimiento
     * @throws NoEncontradoException si el turno no es de la empresa
     * @throws NegocioException      si el turno no está abierto, el correo o el PIN no sirven, el PIN está bloqueado o
     *                               el supervisor es quien pide
     */
    @Transactional(noRollbackFor = NegocioException.class)
    public AutorizacionResponse otorgar(AutorizacionRequest req) {
        Long solicitante = SesionActual.usuario().id();
        TurnoCaja turno = turnoRepository.findById(req.turnoId())
                .orElseThrow(() -> new NoEncontradoException("Turno no encontrado"));
        if (turno.getEstado() != EstadoTurno.ABIERTO) {
            throw new NegocioException("TURNO_NO_ABIERTO", "Solo se autoriza en un turno abierto");
        }
        Usuario supervisor = usuarioRepository.findByCorreoIgnoreCase(req.correo().trim()).orElse(null);
        if (supervisor == null || !supervisor.isActivo() || supervisor.getPinSupervisorHash() == null
                || !puedeAutorizar(supervisor)) {
            auditoriaService.fallo(solicitante, AccionAuditoria.AUTORIZACION_FALLO, EntidadAuditada.TURNO_CAJA,
                    turno.getId(), "accion=" + req.accion() + "; correo o PIN no válidos");
            throw invalida();
        }
        LocalDateTime ahora = LocalDateTime.now();
        if (supervisor.getPinBloqueadoHasta() != null && supervisor.getPinBloqueadoHasta().isAfter(ahora)) {
            auditoriaService.fallo(solicitante, AccionAuditoria.AUTORIZACION_FALLO, EntidadAuditada.TURNO_CAJA,
                    turno.getId(), "accion=" + req.accion() + "; PIN bloqueado del usuario " + supervisor.getId());
            throw new NegocioException("PIN_BLOQUEADO",
                    "El PIN de ese supervisor está bloqueado por intentos fallidos. Intente en unos minutos");
        }
        if (!passwordEncoder.matches(req.pin(), supervisor.getPinSupervisorHash())) {
            int intentos = supervisor.getPinIntentosFallidos() + 1;
            if (intentos >= MAX_INTENTOS) {
                supervisor.setPinBloqueadoHasta(ahora.plusMinutes(MINUTOS_BLOQUEO));
                intentos = 0;
            }
            supervisor.setPinIntentosFallidos(intentos);
            usuarioRepository.save(supervisor);
            auditoriaService.fallo(solicitante, AccionAuditoria.AUTORIZACION_FALLO, EntidadAuditada.TURNO_CAJA,
                    turno.getId(), "accion=" + req.accion() + "; PIN incorrecto del usuario " + supervisor.getId());
            throw invalida();
        }
        if (supervisor.getId().equals(solicitante)) {
            throw new NegocioException("AUTORIZA_LO_SUYO",
                    "Nadie autoriza lo suyo: otro supervisor debe escribir su correo y su PIN");
        }
        supervisor.setPinIntentosFallidos(0);
        supervisor.setPinBloqueadoHasta(null);

        String token = nuevoToken();
        AutorizacionSupervisor a = new AutorizacionSupervisor();
        a.setSupervisorUsuarioId(supervisor.getId());
        a.setSolicitanteUsuarioId(solicitante);
        a.setAccion(req.accion());
        a.setTurnoId(turno.getId());
        a.setReferenciaId(req.referenciaId());
        a.setTokenHash(hash(token));
        a.setVenceEn(ahora.plusMinutes(MINUTOS_VIGENCIA));
        AutorizacionSupervisor guardada = autorizacionRepository.save(a);
        auditoriaService.exito(AccionAuditoria.AUTORIZACION_OTORGAR, EntidadAuditada.AUTORIZACION, guardada.getId(),
                "accion=" + req.accion() + "; turno=" + turno.getId() + "; supervisor=" + supervisor.getId()
                        + "; solicitante=" + solicitante
                        + (req.referenciaId() == null ? "" : "; referencia=" + req.referenciaId()));
        return new AutorizacionResponse(token, guardada.getVenceEn(), supervisor.getNombreCompleto());
    }

    /**
     * Consume un permiso: debe ser de quien lo pidió, para esa acción, ese turno y esa referencia, sin usar y vigente.
     * Se llama dentro de la transacción de la acción, así que si la acción falla el permiso no se gasta.
     *
     * @param token        token recibido
     * @param accion       acción que se ejecuta
     * @param turnoId      turno
     * @param referenciaId referencia de la acción ({@code null} si no tiene)
     * @return id del supervisor que autorizó
     * @throws NegocioException {@code AUTORIZACION_INVALIDA} si el permiso no sirve
     */
    @Transactional
    public Long consumir(String token, AccionAutorizada accion, Long turnoId, Long referenciaId) {
        if (token == null || token.isBlank()) {
            throw new NegocioException("AUTORIZACION_REQUERIDA", "Esta acción necesita la autorización de un supervisor");
        }
        AutorizacionSupervisor a = autorizacionRepository.bloquearPorToken(hash(token.trim())).orElse(null);
        if (a == null || a.getUsadaEn() != null || !a.getVenceEn().isAfter(LocalDateTime.now())
                || a.getAccion() != accion || !a.getTurnoId().equals(turnoId)
                || !a.getSolicitanteUsuarioId().equals(SesionActual.usuario().id())
                || !Objects.equals(a.getReferenciaId(), referenciaId)) {
            throw new NegocioException("AUTORIZACION_INVALIDA",
                    "La autorización no sirve para esta acción, ya se usó o venció: pídala de nuevo");
        }
        a.setUsadaEn(LocalDateTime.now());
        return a.getSupervisorUsuarioId();
    }

    /**
     * Fija o cambia el PIN de la sesión, confirmando su contraseña.
     *
     * @param req contraseña actual y PIN nuevo
     * @throws NegocioException {@code PASSWORD_INCORRECTA} si la contraseña no coincide
     */
    @Transactional
    public void cambiarPin(PinRequest req) {
        Usuario u = usuarioRepository.findById(SesionActual.usuario().id())
                .orElseThrow(() -> new NoEncontradoException("Usuario no encontrado"));
        if (!passwordEncoder.matches(req.passwordActual(), u.getHashPassword())) {
            throw new NegocioException("PASSWORD_INCORRECTA", "La contraseña no coincide");
        }
        u.setPinSupervisorHash(passwordEncoder.encode(req.pin()));
        u.setPinIntentosFallidos(0);
        u.setPinBloqueadoHasta(null);
        auditoriaService.exito(AccionAuditoria.PIN_CAMBIAR, EntidadAuditada.USUARIO, u.getId(), "PIN de supervisor fijado");
    }

    /**
     * @return {@code true} si la sesión ya tiene un PIN fijado
     */
    @Transactional(readOnly = true)
    public boolean tienePin() {
        return usuarioRepository.findById(SesionActual.usuario().id()).map(u -> u.getPinSupervisorHash() != null)
                .orElse(false);
    }

    // ----------------------------------------------------------------- apoyo

    /**
     * @param u usuario con sus roles
     * @return {@code true} si es supervisor de ventas o dueño
     */
    private static boolean puedeAutorizar(Usuario u) {
        return u.getRoles().stream().map(Rol::getCodigo)
                .anyMatch(c -> c.equals(Roles.SUPERVISOR_VENTAS) || c.equals(Roles.ADMIN_SISTEMA));
    }

    /**
     * @return mensaje que no dice si falló el correo o el PIN
     */
    private static NegocioException invalida() {
        return new NegocioException("AUTORIZACION_INVALIDA", "Correo o PIN de supervisor no válidos");
    }

    /**
     * @return 32 bytes aleatorios en Base64 para URL
     */
    private String nuevoToken() {
        byte[] bytes = new byte[32];
        aleatorio.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * @param token token
     * @return SHA-256 en hexadecimal
     */
    static String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }
}
