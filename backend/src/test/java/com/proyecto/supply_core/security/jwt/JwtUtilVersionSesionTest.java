package com.proyecto.supply_core.security.jwt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Set;

import org.junit.jupiter.api.Test;

import com.proyecto.supply_core.common.exception.NoAutenticadoException;
import com.proyecto.supply_core.security.context.UsuarioAutenticado;

class JwtUtilVersionSesionTest {

    private final JwtUtil jwt = new JwtUtil(new JwtProperties("clave-de-prueba-de-al-menos-32-caracteres!!", 15));
    private final UsuarioAutenticado usuario = new UsuarioAutenticado(5L, "ana@x.com", "Ana", Set.of("COMPRADOR"),
            null, 1L);

    @Test
    void elTokenLlevaLaVersionConLaQueSeEmitio() {
        String token = jwt.generar(usuario, 4);

        assertEquals(4, jwt.versionSesion(token));
        assertEquals(5L, jwt.validar(token).id());
    }

    @Test
    void unTokenSinVersionCuentaComoCero() {
        assertEquals(0, jwt.versionSesion(jwt.generar(usuario)));
    }

    @Test
    void unTokenAlteradoNoSeLee() {
        String token = jwt.generar(usuario, 4);

        assertThrows(NoAutenticadoException.class, () -> jwt.versionSesion(token + "x"));
    }
}
