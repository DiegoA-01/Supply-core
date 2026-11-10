package com.proyecto.supply_core.security.jwt;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.Date;
import java.util.Set;
import java.util.stream.Collectors;

import javax.crypto.SecretKey;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.proyecto.supply_core.common.exception.NoAutenticadoException;
import com.proyecto.supply_core.security.context.PlataformaAutenticada;
import com.proyecto.supply_core.security.context.UsuarioAutenticado;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/**
 * Emisión y validación de JWT HS256 con la librería jjwt.
 * <p>jjwt se encarga de la criptografía (firma, algoritmo, expiración, emisor); el resto de la
 * seguridad (filtro, roles, login) es propio del proyecto, sin Spring Security.</p>
 */
@Component
public class JwtUtil {

    private static final Logger log = LoggerFactory.getLogger(JwtUtil.class);
    /** Emisor fijo: un token de otro sistema firmado con la misma clave no se acepta. */
    static final String EMISOR = "supply-core";
    private static final int LARGO_MINIMO_SECRETO = 32; // 256 bits, mínimo para HS256

    private static final String CLAIM_CORREO = "correo";
    private static final String CLAIM_NOMBRE = "nombre";
    private static final String CLAIM_ROLES = "roles";
    private static final String CLAIM_PROVEEDOR = "prov";
    private static final String CLAIM_EMPRESA = "emp";
    /** Versión de sesión del usuario al emitir el token; si ya no coincide con la de la base, el token no sirve. */
    private static final String CLAIM_VERSION_SESION = "sv";
    /** Marca de un token de la plataforma: los de empresa no la llevan y los de plataforma no llevan {@code emp}. */
    private static final String CLAIM_PLATAFORMA = "plat";
    /** Marca del desafío de MFA: sirve solo para entregar el código, no para llamar a la API. */
    private static final String CLAIM_DESAFIO_MFA = "mfa";
    /** Marca del desafío de MFA de un usuario de empresa: lleva la empresa y no sirve en la consola de la plataforma. */
    private static final String CLAIM_DESAFIO_MFA_EMPRESA = "mfae";
    /** Tiempo para escribir el código de MFA después de acertar la contraseña. */
    private static final long VIGENCIA_DESAFIO_SEGUNDOS = 300;

    private final SecretKey clave;
    private final long expiracionSegundos;
    private final Clock reloj;
    private final JwtParser parser;

    /**
     * Constructor que usa Spring (reloj del sistema).
     *
     * @param props configuración {@code app.jwt.*}
     * @throws IllegalStateException si el secreto configurado es demasiado corto
     */
    @Autowired
    public JwtUtil(JwtProperties props) {
        this(props, Clock.systemUTC());
    }

    /**
     * Constructor con reloj inyectable (para probar la expiración sin esperar).
     *
     * @param props configuración {@code app.jwt.*}
     * @param reloj fuente de la hora actual
     * @throws IllegalStateException si el secreto configurado es demasiado corto
     */
    JwtUtil(JwtProperties props, Clock reloj) {
        this.clave = Keys.hmacShaKeyFor(resolverSecreto(props.secret()));
        this.expiracionSegundos = props.expiracionMinutos() * 60;
        this.reloj = reloj;
        this.parser = Jwts.parser()
                .verifyWith(clave)
                .requireIssuer(EMISOR)
                .clock(() -> Date.from(reloj.instant()))
                .build();
    }

    /**
     * Emite un token firmado para el usuario.
     *
     * @param u datos que viajarán en el token
     * @return JWT compacto listo para el header {@code Authorization: Bearer}
     */
    public String generar(UsuarioAutenticado u) {
        return generar(u, 0);
    }

    /**
     * Emite un token que lleva la versión de sesión del usuario: deja de valer cuando esa versión sube (cambio de
     * clave o desactivación).
     *
     * @param u             datos que viajarán en el token
     * @param versionSesion versión de sesión que tiene hoy el usuario
     * @return JWT compacto listo para el header {@code Authorization: Bearer}
     */
    public String generar(UsuarioAutenticado u, int versionSesion) {
        Instant ahora = reloj.instant();
        return Jwts.builder()
                .issuer(EMISOR)
                .subject(String.valueOf(u.id()))
                .claim(CLAIM_CORREO, u.correo())
                .claim(CLAIM_NOMBRE, u.nombre())
                .claim(CLAIM_ROLES, u.roles())
                .claim(CLAIM_PROVEEDOR, u.proveedorId()) // null = no se incluye
                .claim(CLAIM_EMPRESA, u.empresaId())
                .claim(CLAIM_VERSION_SESION, versionSesion)
                .issuedAt(Date.from(ahora))
                .expiration(Date.from(ahora.plusSeconds(expiracionSegundos)))
                .signWith(clave, Jwts.SIG.HS256)
                .compact();
    }

    /**
     * Emite el token de sesión de un usuario de la plataforma. No lleva empresa: no sirve en la API de ninguna empresa.
     *
     * @param u usuario de la plataforma
     * @return JWT compacto
     */
    public String generarPlataforma(PlataformaAutenticada u) {
        Instant ahora = reloj.instant();
        return Jwts.builder()
                .issuer(EMISOR)
                .subject(String.valueOf(u.id()))
                .claim(CLAIM_CORREO, u.correo())
                .claim(CLAIM_NOMBRE, u.nombre())
                .claim(CLAIM_PLATAFORMA, Boolean.TRUE)
                .issuedAt(Date.from(ahora))
                .expiration(Date.from(ahora.plusSeconds(expiracionSegundos)))
                .signWith(clave, Jwts.SIG.HS256)
                .compact();
    }

    /**
     * Valida un token de sesión de la plataforma. Un token de empresa o un desafío de MFA no pasan.
     *
     * @param token JWT recibido (sin el prefijo "Bearer ")
     * @return usuario de la plataforma
     * @throws NoAutenticadoException si el token no es de la plataforma, está alterado o expiró
     */
    public PlataformaAutenticada validarPlataforma(String token) {
        Claims claims = leer(token);
        try {
            if (!Boolean.TRUE.equals(claims.get(CLAIM_PLATAFORMA, Boolean.class))) {
                throw invalido();
            }
            return new PlataformaAutenticada(Long.valueOf(claims.getSubject()), claims.get(CLAIM_CORREO, String.class),
                    claims.get(CLAIM_NOMBRE, String.class));
        } catch (JwtException | NumberFormatException e) {
            throw invalido();
        }
    }

    /**
     * Emite el desafío que se canjea por la sesión al escribir el código de MFA. Dura pocos minutos.
     *
     * @param usuarioId usuario de la plataforma que acertó la contraseña
     * @return JWT compacto de corta vida
     */
    public String generarDesafioMfa(Long usuarioId) {
        Instant ahora = reloj.instant();
        return Jwts.builder()
                .issuer(EMISOR)
                .subject(String.valueOf(usuarioId))
                .claim(CLAIM_DESAFIO_MFA, Boolean.TRUE)
                .issuedAt(Date.from(ahora))
                .expiration(Date.from(ahora.plusSeconds(VIGENCIA_DESAFIO_SEGUNDOS)))
                .signWith(clave, Jwts.SIG.HS256)
                .compact();
    }

    /**
     * Valida un desafío de MFA y devuelve el usuario al que pertenece.
     *
     * @param token desafío entregado al acertar la contraseña
     * @return id del usuario de la plataforma
     * @throws NoAutenticadoException si no es un desafío, está alterado o expiró
     */
    public Long validarDesafioMfa(String token) {
        Claims claims = leer(token);
        try {
            if (!Boolean.TRUE.equals(claims.get(CLAIM_DESAFIO_MFA, Boolean.class))) {
                throw invalido();
            }
            return Long.valueOf(claims.getSubject());
        } catch (JwtException | NumberFormatException e) {
            throw invalido();
        }
    }

    /**
     * Emite el desafío de MFA de un usuario de empresa: se canjea por la sesión al escribir el código. Dura pocos
     * minutos y lleva la empresa para poder continuar el login dentro de ella.
     *
     * @param usuarioId usuario que acertó la contraseña
     * @param empresaId empresa del usuario
     * @return JWT compacto de corta vida
     */
    public String generarDesafioMfaEmpresa(Long usuarioId, Long empresaId) {
        Instant ahora = reloj.instant();
        return Jwts.builder()
                .issuer(EMISOR)
                .subject(String.valueOf(usuarioId))
                .claim(CLAIM_DESAFIO_MFA_EMPRESA, Boolean.TRUE)
                .claim(CLAIM_EMPRESA, empresaId)
                .issuedAt(Date.from(ahora))
                .expiration(Date.from(ahora.plusSeconds(VIGENCIA_DESAFIO_SEGUNDOS)))
                .signWith(clave, Jwts.SIG.HS256)
                .compact();
    }

    /**
     * Valida el desafío de MFA de un usuario de empresa. Una sesión, un desafío de la plataforma o cualquier otro
     * token no pasan.
     *
     * @param token desafío entregado al acertar la contraseña
     * @return usuario y empresa a los que pertenece
     * @throws NoAutenticadoException si no es un desafío de empresa, está alterado o expiró
     */
    public DesafioMfaEmpresa validarDesafioMfaEmpresa(String token) {
        Claims claims = leer(token);
        try {
            Number empresa = claims.get(CLAIM_EMPRESA, Number.class);
            if (!Boolean.TRUE.equals(claims.get(CLAIM_DESAFIO_MFA_EMPRESA, Boolean.class)) || empresa == null) {
                throw invalido();
            }
            return new DesafioMfaEmpresa(Long.valueOf(claims.getSubject()), empresa.longValue());
        } catch (JwtException | NumberFormatException e) {
            throw invalido();
        }
    }

    /**
     * Verifica firma, emisor y vigencia y devuelve el contenido.
     *
     * @param token JWT recibido
     * @return claims
     * @throws NoAutenticadoException si el token está mal formado, alterado, es de otro emisor o expiró
     */
    private Claims leer(String token) {
        try {
            return parser.parseSignedClaims(token).getPayload();
        } catch (ExpiredJwtException e) {
            throw new NoAutenticadoException("La sesión expiró, inicie sesión de nuevo");
        } catch (JwtException | IllegalArgumentException e) {
            throw invalido();
        }
    }

    /**
     * Verifica firma, algoritmo, emisor y vigencia, y reconstruye el usuario.
     *
     * @param token JWT recibido (sin el prefijo "Bearer ")
     * @return usuario contenido en el token
     * @throws NoAutenticadoException si el token está mal formado, alterado, es de otro emisor o expiró
     */
    public UsuarioAutenticado validar(String token) {
        Claims claims;
        try {
            claims = parser.parseSignedClaims(token).getPayload();
        } catch (ExpiredJwtException e) {
            throw new NoAutenticadoException("La sesión expiró, inicie sesión de nuevo");
        } catch (JwtException | IllegalArgumentException e) {
            throw invalido();
        }
        try {
            Number proveedor = claims.get(CLAIM_PROVEEDOR, Number.class);
            Number empresa = claims.get(CLAIM_EMPRESA, Number.class);
            if (empresa == null) {
                throw invalido(); // un token sin empresa (anterior a SaaS) ya no sirve: debe iniciar sesión de nuevo
            }
            return new UsuarioAutenticado(
                    Long.valueOf(claims.getSubject()),
                    claims.get(CLAIM_CORREO, String.class),
                    claims.get(CLAIM_NOMBRE, String.class),
                    roles(claims.get(CLAIM_ROLES)),
                    proveedor == null ? null : proveedor.longValue(),
                    empresa.longValue());
        } catch (JwtException | NumberFormatException e) {
            throw invalido(); // firma válida pero contenido inesperado: se trata como inválido
        }
    }

    /**
     * Versión de sesión con la que se emitió el token (0 si no lleva ninguna).
     *
     * @param token JWT de una empresa ya validado
     * @return versión de sesión
     * @throws NoAutenticadoException si el token no se puede leer
     */
    public int versionSesion(String token) {
        Number version = leer(token).get(CLAIM_VERSION_SESION, Number.class);
        return version == null ? 0 : version.intValue();
    }

    /**
     * Vigencia configurada de los tokens.
     *
     * @return segundos desde la emisión hasta la expiración
     */
    public long getExpiracionSegundos() {
        return expiracionSegundos;
    }

    /**
     * Convierte el claim {@code roles} (lista JSON) a un conjunto de textos.
     *
     * @param valor valor crudo del claim
     * @return roles
     * @throws NoAutenticadoException si no es una lista
     */
    private static Set<String> roles(Object valor) {
        if (valor instanceof Collection<?> lista) {
            return lista.stream().map(String::valueOf).collect(Collectors.toSet());
        }
        throw invalido();
    }

    /**
     * Usa el secreto configurado o genera uno aleatorio si está vacío (solo desarrollo).
     *
     * @param configurado valor de {@code app.jwt.secret}
     * @return bytes del secreto (mínimo 32)
     * @throws IllegalStateException si el secreto configurado tiene menos de 32 caracteres
     */
    private static byte[] resolverSecreto(String configurado) {
        if (configurado != null && !configurado.isBlank()) {
            byte[] bytes = configurado.getBytes(StandardCharsets.UTF_8);
            if (bytes.length < LARGO_MINIMO_SECRETO) {
                throw new IllegalStateException("JWT_SECRET debe tener al menos 32 caracteres");
            }
            return bytes;
        }
        log.warn("JWT_SECRET no definido: se usa un secreto aleatorio (los tokens se invalidan al reiniciar)");
        byte[] aleatorio = new byte[LARGO_MINIMO_SECRETO];
        new SecureRandom().nextBytes(aleatorio);
        return aleatorio;
    }

    /**
     * Error único para cualquier token inválido (no se explica qué parte falló).
     *
     * @return excepción 401
     */
    private static NoAutenticadoException invalido() {
        return new NoAutenticadoException("Token inválido");
    }
}
