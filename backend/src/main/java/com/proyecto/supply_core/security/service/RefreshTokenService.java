package com.proyecto.supply_core.security.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.proyecto.supply_core.auditoria.enums.AccionAuditoria;
import com.proyecto.supply_core.auditoria.enums.EntidadAuditada;
import com.proyecto.supply_core.auditoria.service.AuditoriaService;
import com.proyecto.supply_core.common.exception.NoAutenticadoException;
import com.proyecto.supply_core.common.tenant.EmpresaContexto;
import com.proyecto.supply_core.security.entity.RefreshToken;
import com.proyecto.supply_core.security.jwt.SesionProperties;
import com.proyecto.supply_core.security.repository.RefreshTokenRepository;

/**
 * Emite, rota y revoca refresh tokens.
 * <p>El valor que recibe el cliente es aleatorio (256 bits) y en la BD solo queda su hash SHA-256.
 * Cada renovación invalida el token usado y entrega otro de la misma familia; si alguien presenta un
 * token ya usado se asume robo y se revoca toda la sesión.</p>
 */
@Service
public class RefreshTokenService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);
    /** Mensaje único para cualquier refresh token que no sirva (no se explica el motivo). */
    static final String SESION_INVALIDA = "Sesión inválida, inicie sesión de nuevo";

    private final RefreshTokenRepository repository;
    private final AuditoriaService auditoriaService;
    private final SesionProperties props;
    private final PlatformTransactionManager transactionManager;
    private final SecureRandom azar = new SecureRandom();

    /**
     * @param repository       acceso a {@code refresh_token}
     * @param auditoriaService registro del uso sospechoso
     * @param props            vigencia configurada
     * @param transactionManager transacciones para el job de limpieza
     */
    public RefreshTokenService(RefreshTokenRepository repository, AuditoriaService auditoriaService,
            SesionProperties props, PlatformTransactionManager transactionManager) {
        this.transactionManager = transactionManager;
        this.repository = repository;
        this.auditoriaService = auditoriaService;
        this.props = props;
    }

    /**
     * Resultado de renovar una sesión.
     *
     * @param usuarioId    dueño de la sesión
     * @param refreshToken valor nuevo que debe guardar el cliente
     */
    public record Renovacion(Long usuarioId, String refreshToken) {
    }

    /**
     * Abre una sesión nueva (familia nueva) para el usuario.
     *
     * @param usuarioId usuario que acaba de iniciar sesión
     * @return valor del refresh token (única vez que se conoce en claro)
     */
    @Transactional
    public String emitir(Long usuarioId) {
        return crear(usuarioId, UUID.randomUUID().toString());
    }

    /**
     * Canjea un refresh token por otro de la misma sesión.
     *
     * @param valor refresh token que envió el cliente
     * @return usuario de la sesión y el token nuevo
     * @throws NoAutenticadoException si no existe, venció, fue revocado o ya se había usado
     */
    @Transactional(noRollbackFor = NoAutenticadoException.class)
    public Renovacion rotar(String valor) {
        RefreshToken actual = repository.findByTokenHash(hash(valor))
                .orElseThrow(() -> new NoAutenticadoException(SESION_INVALIDA));
        LocalDateTime ahora = LocalDateTime.now();

        if (actual.getUsadoEn() != null || actual.getRevocadoEn() != null) {
            // Un token ya canjeado o revocado no debería volver: se corta toda la sesión
            repository.revocarFamilia(actual.getFamilia(), ahora);
            log.warn("Refresh token reutilizado: usuario {} familia {}", actual.getUsuarioId(), actual.getFamilia());
            auditoriaService.fallo(actual.getUsuarioId(), AccionAuditoria.REFRESH_REUTILIZADO,
                    EntidadAuditada.USUARIO, actual.getUsuarioId(), "sesión revocada por reutilización");
            throw new NoAutenticadoException(SESION_INVALIDA);
        }
        if (actual.getExpiraEn().isBefore(ahora)) {
            throw new NoAutenticadoException(SESION_INVALIDA);
        }
        actual.setUsadoEn(ahora);
        return new Renovacion(actual.getUsuarioId(), crear(actual.getUsuarioId(), actual.getFamilia()));
    }

    /**
     * Cierra la sesión a la que pertenece el token. No falla si el token no existe (idempotente).
     *
     * @param valor refresh token de la sesión
     */
    @Transactional
    public void cerrarSesion(String valor) {
        repository.findByTokenHash(hash(valor))
                .ifPresent(t -> repository.revocarFamilia(t.getFamilia(), LocalDateTime.now()));
    }

    /**
     * Cierra todas las sesiones del usuario (todos sus dispositivos).
     *
     * @param usuarioId usuario dueño de las sesiones
     */
    @Transactional
    public void cerrarTodas(Long usuarioId) {
        repository.revocarDeUsuario(usuarioId, LocalDateTime.now());
    }

    /** Borra cada noche los tokens vencidos hace más de un día. */
    @Scheduled(cron = "0 30 3 * * *")
    public void limpiarVencidos() {
        // Los refresh tokens no pertenecen a una empresa: el job trabaja sin empresa
        int borrados = EmpresaContexto.sinEmpresa(this::borrarVencidos);
        if (borrados > 0) {
            log.info("Refresh tokens vencidos eliminados: {}", borrados);
        }
    }

    /**
     * Borra los tokens vencidos hace más de un día en su propia transacción.
     *
     * @return cantidad de tokens borrados
     */
    private int borrarVencidos() {
        return new TransactionTemplate(transactionManager)
                .execute(estado -> repository.borrarVencidos(LocalDateTime.now().minusDays(1)));
    }

    /**
     * Genera y guarda un token nuevo en la familia indicada.
     *
     * @param usuarioId dueño
     * @param familia   sesión a la que pertenece
     * @return valor en claro para el cliente
     */
    private String crear(Long usuarioId, String familia) {
        byte[] bytes = new byte[32];
        azar.nextBytes(bytes);
        String valor = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        RefreshToken t = new RefreshToken();
        t.setUsuarioId(usuarioId);
        t.setFamilia(familia);
        t.setTokenHash(hash(valor));
        t.setExpiraEn(LocalDateTime.now().plusDays(props.refreshDias()));
        repository.save(t);
        return valor;
    }

    /**
     * SHA-256 en hexadecimal del valor recibido.
     *
     * @param valor texto del token
     * @return hash de 64 caracteres
     */
    static String hash(String valor) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(valor.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }
}
