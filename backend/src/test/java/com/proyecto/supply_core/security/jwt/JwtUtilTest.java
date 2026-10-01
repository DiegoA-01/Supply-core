package com.proyecto.supply_core.security.jwt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.proyecto.supply_core.common.exception.NoAutenticadoException;
import com.proyecto.supply_core.security.context.UsuarioAutenticado;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

class JwtUtilTest {

    private static final String SECRETO = "clave-de-prueba-de-al-menos-32-caracteres!!";
    private static final Instant T0 = Instant.parse("2026-10-01T12:00:00Z");
    private final UsuarioAutenticado usuario =
            new UsuarioAutenticado(7L, "ana@demo.com", "Ana", Set.of("COMPRADOR", "APROBADOR"), null);

    private JwtUtil jwt(String secreto, Instant ahora) {
        return new JwtUtil(new JwtProperties(secreto, 60), Clock.fixed(ahora, ZoneOffset.UTC));
    }

    @Test
    void generaYValidaElMismoUsuario() {
        JwtUtil util = jwt(SECRETO, T0);
        UsuarioAutenticado leido = util.validar(util.generar(usuario));

        assertEquals(7L, leido.id());
        assertEquals("ana@demo.com", leido.correo());
        assertEquals("Ana", leido.nombre());
        assertEquals(Set.of("COMPRADOR", "APROBADOR"), leido.roles());
        assertNull(leido.proveedorId());
    }

    @Test
    void conservaElProveedor() {
        JwtUtil util = jwt(SECRETO, T0);
        var proveedor = new UsuarioAutenticado(9L, "p@x.com", "P", Set.of("PROVEEDOR"), 42L);
        assertEquals(42L, util.validar(util.generar(proveedor)).proveedorId());
    }

    @Test
    void rechazaTokenAlterado() {
        JwtUtil util = jwt(SECRETO, T0);
        String[] partes = util.generar(usuario).split("\\.");
        String otroPayload = util.generar(new UsuarioAutenticado(1L, "x@x.com", "X",
                Set.of("ADMIN_SISTEMA"), null)).split("\\.")[1];

        assertThrows(NoAutenticadoException.class,
                () -> util.validar(partes[0] + "." + otroPayload + "." + partes[2]));
    }

    @Test
    void rechazaTokenFirmadoConOtroSecreto() {
        String token = jwt("otro-secreto-distinto-de-al-menos-32-chars", T0).generar(usuario);
        assertThrows(NoAutenticadoException.class, () -> jwt(SECRETO, T0).validar(token));
    }

    @Test
    void rechazaTokenExpirado() {
        String token = jwt(SECRETO, T0).generar(usuario);
        JwtUtil despues = jwt(SECRETO, T0.plus(Duration.ofMinutes(61)));

        var ex = assertThrows(NoAutenticadoException.class, () -> despues.validar(token));
        assertEquals("La sesión expiró, inicie sesión de nuevo", ex.getMessage());
    }

    @Test
    void rechazaTokenDeOtroEmisorConLaMismaClave() {
        String ajeno = Jwts.builder().issuer("otro-sistema").subject("1")
                .expiration(Date.from(T0.plusSeconds(600)))
                .signWith(Keys.hmacShaKeyFor(SECRETO.getBytes(StandardCharsets.UTF_8)))
                .compact();
        assertThrows(NoAutenticadoException.class, () -> jwt(SECRETO, T0).validar(ajeno));
    }

    @Test
    void rechazaTokenSinFirma() {
        String sinFirma = Jwts.builder().issuer(JwtUtil.EMISOR).subject("1")
                .expiration(Date.from(T0.plusSeconds(600))).compact();
        assertThrows(NoAutenticadoException.class, () -> jwt(SECRETO, T0).validar(sinFirma));
    }

    @Test
    void rechazaBasura() {
        JwtUtil util = jwt(SECRETO, T0);
        assertThrows(NoAutenticadoException.class, () -> util.validar("no.es.jwt"));
        assertThrows(NoAutenticadoException.class, () -> util.validar(""));
    }

    @Test
    void exigeSecretoLargo() {
        assertThrows(IllegalStateException.class, () -> jwt("corto", T0));
    }
}
