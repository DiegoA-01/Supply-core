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
        Instant ahora = reloj.instant();
        return Jwts.builder()
                .issuer(EMISOR)
                .subject(String.valueOf(u.id()))
                .claim(CLAIM_CORREO, u.correo())
                .claim(CLAIM_NOMBRE, u.nombre())
                .claim(CLAIM_ROLES, u.roles())
                .claim(CLAIM_PROVEEDOR, u.proveedorId()) // null = no se incluye
                .issuedAt(Date.from(ahora))
                .expiration(Date.from(ahora.plusSeconds(expiracionSegundos)))
                .signWith(clave, Jwts.SIG.HS256)
                .compact();
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
            return new UsuarioAutenticado(
                    Long.valueOf(claims.getSubject()),
                    claims.get(CLAIM_CORREO, String.class),
                    claims.get(CLAIM_NOMBRE, String.class),
                    roles(claims.get(CLAIM_ROLES)),
                    proveedor == null ? null : proveedor.longValue());
        } catch (JwtException | NumberFormatException e) {
            throw invalido(); // firma válida pero contenido inesperado: se trata como inválido
        }
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
