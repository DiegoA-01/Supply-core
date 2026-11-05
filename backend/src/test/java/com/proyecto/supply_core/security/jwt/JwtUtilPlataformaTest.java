package com.proyecto.supply_core.security.jwt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Set;

import org.junit.jupiter.api.Test;

import com.proyecto.supply_core.common.exception.NoAutenticadoException;
import com.proyecto.supply_core.security.context.PlataformaAutenticada;
import com.proyecto.supply_core.security.context.UsuarioAutenticado;

/** Los tokens de la plataforma, los de una empresa y el desafío de MFA no se pueden confundir entre sí. */
class JwtUtilPlataformaTest {

    private final JwtUtil jwt = new JwtUtil(new JwtProperties("clave-de-prueba-de-al-menos-32-caracteres!!", 15));

    private final PlataformaAutenticada dueno = new PlataformaAutenticada(3L, "dueno@supplycore.local", "Dueño");

    @Test
    void elTokenDeLaPlataformaVaYVuelve() {
        PlataformaAutenticada leido = jwt.validarPlataforma(jwt.generarPlataforma(dueno));

        assertEquals(dueno, leido);
    }

    @Test
    void elTokenDeLaPlataformaNoSirveEnLaApiDeUnaEmpresa() {
        assertThrows(NoAutenticadoException.class, () -> jwt.validar(jwt.generarPlataforma(dueno)));
    }

    @Test
    void elTokenDeUnaEmpresaNoSirveEnLaConsolaDeLaPlataforma() {
        String deEmpresa = jwt.generar(new UsuarioAutenticado(5L, "ana@demo.com", "Ana", Set.of("ADMIN_SISTEMA"), null, 1L));

        assertThrows(NoAutenticadoException.class, () -> jwt.validarPlataforma(deEmpresa));
    }

    @Test
    void elDesafioDeMfaNoEsUnaSesionDeNingunTipo() {
        String desafio = jwt.generarDesafioMfa(3L);

        assertEquals(3L, jwt.validarDesafioMfa(desafio));
        assertThrows(NoAutenticadoException.class, () -> jwt.validarPlataforma(desafio));
        assertThrows(NoAutenticadoException.class, () -> jwt.validar(desafio));
    }

    @Test
    void unaSesionNoSirveComoDesafioDeMfa() {
        assertThrows(NoAutenticadoException.class, () -> jwt.validarDesafioMfa(jwt.generarPlataforma(dueno)));
    }

    @Test
    void unTokenAlteradoOBasuraSeRechaza() {
        String token = jwt.generarPlataforma(dueno);

        assertThrows(NoAutenticadoException.class, () -> jwt.validarPlataforma(token + "x"));
        assertThrows(NoAutenticadoException.class, () -> jwt.validarPlataforma("basura"));
        assertThrows(NoAutenticadoException.class, () -> jwt.validarDesafioMfa("basura"));
    }
}
