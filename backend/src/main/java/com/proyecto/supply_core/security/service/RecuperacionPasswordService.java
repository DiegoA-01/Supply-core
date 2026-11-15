package com.proyecto.supply_core.security.service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.proyecto.supply_core.auditoria.enums.AccionAuditoria;
import com.proyecto.supply_core.auditoria.enums.EntidadAuditada;
import com.proyecto.supply_core.auditoria.service.AuditoriaService;
import com.proyecto.supply_core.common.exception.NoAutenticadoException;
import com.proyecto.supply_core.common.tenant.EmpresaContexto;
import com.proyecto.supply_core.notificacion.email.EmailClient;
import com.proyecto.supply_core.security.entity.RecuperacionPassword;
import com.proyecto.supply_core.security.repository.RecuperacionPasswordRepository;
import com.proyecto.supply_core.usuario.entity.Usuario;
import com.proyecto.supply_core.usuario.repository.UsuarioRepository;

/**
 * Recuperación de una contraseña olvidada con un token de un solo uso enviado por correo.
 * <p>El valor del token es aleatorio (256 bits) y en la base solo queda su hash. Pedirlo nunca revela si el correo
 * existe: responde igual en todos los casos. Al usarlo se cambia la clave, se levanta el bloqueo de la cuenta y se
 * cortan todas sus sesiones. Un token nuevo deja sin efecto los anteriores.</p>
 */
@Service
public class RecuperacionPasswordService {

    private static final Logger log = LoggerFactory.getLogger(RecuperacionPasswordService.class);
    /** Mensaje único para cualquier token que no sirva (no se explica el motivo). */
    public static final String TOKEN_INVALIDO ="El enlace de recuperación no es válido o ya venció";
    /** Segundos mínimos entre dos pedidos de un mismo usuario (frena el correo masivo a un tercero). */
    private static final long ESPERA_ENTRE_PEDIDOS_SEGUNDOS = 60;

    private final RecuperacionPasswordRepository repository;
    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final SesionUsuarioService sesiones;
    private final AuditoriaService auditoriaService;
    private final EmailClient emailClient;
    private final PlatformTransactionManager transactionManager;
    private final long vigenciaMinutos;
    private final SecureRandom azar = new SecureRandom();

    /**
     * @param repository         tokens de recuperación
     * @param usuarioRepository  usuarios de la empresa del contexto
     * @param passwordEncoder    hash BCrypt de la clave nueva
     * @param sesiones           corte de las sesiones al cambiar la clave
     * @param auditoriaService   registro de lo pedido y lo usado
     * @param emailClient        envío del token
     * @param transactionManager transacciones para el job de limpieza
     * @param vigenciaMinutos    minutos que sirve un token ({@code app.sesion.recuperacion-minutos})
     */
    public RecuperacionPasswordService(RecuperacionPasswordRepository repository, UsuarioRepository usuarioRepository,
            PasswordEncoder passwordEncoder, SesionUsuarioService sesiones, AuditoriaService auditoriaService,
            EmailClient emailClient, PlatformTransactionManager transactionManager,
            @Value("${app.sesion.recuperacion-minutos:30}") long vigenciaMinutos) {
        this.repository = repository;
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.sesiones = sesiones;
        this.auditoriaService = auditoriaService;
        this.emailClient = emailClient;
        this.transactionManager = transactionManager;
        this.vigenciaMinutos = vigenciaMinutos;
    }

    /**
     * Crea el token y lo envía por correo si el usuario existe y está activo. No dice si existe: quien llama
     * responde siempre lo mismo. Debe correr dentro de la empresa del código recibido.
     *
     * @param correo correo escrito por quien olvidó la clave
     */
    @Transactional
    public void solicitar(String correo) {
        String limpio = correo.trim().toLowerCase(Locale.ROOT);
        Usuario usuario = usuarioRepository.findByCorreoIgnoreCase(limpio).orElse(null);
        LocalDateTime ahora = LocalDateTime.now();
        if (usuario == null || !usuario.isActivo()) {
            auditoriaService.fallo(null, AccionAuditoria.PASSWORD_RECUPERAR_PEDIR, EntidadAuditada.USUARIO, null,
                    "correo=" + limpio + "; motivo=" + (usuario == null ? "correo no registrado" : "cuenta inactiva"));
            return;
        }
        if (repository.existsByUsuarioIdAndCreadoEnAfter(usuario.getId(),
                ahora.minusSeconds(ESPERA_ENTRE_PEDIDOS_SEGUNDOS))) {
            return; // acaba de pedirlo: no se manda otro correo
        }
        repository.cerrarPendientes(usuario.getId(), ahora);

        byte[] bytes = new byte[32];
        azar.nextBytes(bytes);
        String valor = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        RecuperacionPassword r = new RecuperacionPassword();
        r.setUsuarioId(usuario.getId());
        r.setTokenHash(RefreshTokenService.hash(valor));
        r.setExpiraEn(ahora.plusMinutes(vigenciaMinutos));
        repository.save(r);
        auditoriaService.exitoDe(usuario.getId(), AccionAuditoria.PASSWORD_RECUPERAR_PEDIR, EntidadAuditada.USUARIO,
                usuario.getId(), "correo=" + limpio);

        String destino = usuario.getCorreo();
        String texto = "Hola " + usuario.getNombreCompleto() + ", recibimos una solicitud para restablecer tu "
                + "contraseña de Supply-Core. Tu código de recuperación (sirve una sola vez y vence en "
                + vigenciaMinutos + " minutos) es: " + valor + " . Si no fuiste tú, ignora este mensaje: "
                + "tu contraseña sigue igual.";
        despuesDelCommit(() -> enviar(destino, texto));
    }

    /**
     * Cambia la clave con el token recibido por correo. Debe correr dentro de la empresa del código recibido.
     *
     * @param token         valor recibido por correo
     * @param passwordNueva contraseña nueva ya validada
     * @throws NoAutenticadoException si el token no existe, ya se usó, venció o la cuenta está inactiva
     */
    @Transactional
    public void restablecer(String token, String passwordNueva) {
        LocalDateTime ahora = LocalDateTime.now();
        RecuperacionPassword r = repository.findByTokenHash(RefreshTokenService.hash(token.trim()))
                .orElseThrow(() -> new NoAutenticadoException(TOKEN_INVALIDO));
        if (r.getUsadoEn() != null || r.getExpiraEn().isBefore(ahora)) {
            throw new NoAutenticadoException(TOKEN_INVALIDO);
        }
        Usuario usuario = usuarioRepository.findWithRolesById(r.getUsuarioId()).orElse(null);
        if (usuario == null || !usuario.isActivo()) {
            throw new NoAutenticadoException(TOKEN_INVALIDO);
        }
        usuario.setHashPassword(passwordEncoder.encode(passwordNueva));
        usuario.setIntentosFallidos(0);
        usuario.setBloqueadoHasta(null);
        repository.cerrarPendientes(usuario.getId(), ahora);
        sesiones.revocarSesiones(usuario);
        // nunca se audita la contraseña ni el token, solo el hecho
        auditoriaService.exitoDe(usuario.getId(), AccionAuditoria.PASSWORD_RECUPERAR_USAR, EntidadAuditada.USUARIO,
                usuario.getId(), "correo=" + usuario.getCorreo());
    }

    /** Borra cada noche los tokens vencidos hace más de un día. */
    @Scheduled(cron = "0 45 3 * * *")
    public void limpiarVencidos() {
        // los tokens llevan empresa pero el job barre todas: trabaja sin empresa
        int borrados = EmpresaContexto.sinEmpresa(() -> new TransactionTemplate(transactionManager)
                .execute(estado -> repository.borrarVencidos(LocalDateTime.now().minusDays(1))));
        if (borrados > 0) {
            log.info("Tokens de recuperación vencidos eliminados: {}", borrados);
        }
    }

    // ----------------------------------------------------------------- apoyo

    /**
     * Ejecuta la acción al confirmarse la transacción (si no hay transacción, de inmediato).
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
     * Envía el correo; si falla solo queda en el log, para no revelar nada a quien lo pidió.
     *
     * @param destino correo del usuario
     * @param texto   cuerpo
     */
    private void enviar(String destino, String texto) {
        try {
            emailClient.enviar(destino, "Supply-Core: recupera tu contraseña", texto);
        } catch (RuntimeException e) {
            log.error("No se pudo enviar el correo de recuperación a {}: {}", destino, e.getMessage());
        }
    }
}
