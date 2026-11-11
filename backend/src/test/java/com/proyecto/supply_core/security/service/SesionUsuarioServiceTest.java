package com.proyecto.supply_core.security.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.proyecto.supply_core.common.exception.NoAutenticadoException;
import com.proyecto.supply_core.usuario.entity.Usuario;
import com.proyecto.supply_core.usuario.repository.EstadoSesion;
import com.proyecto.supply_core.usuario.repository.UsuarioRepository;

class SesionUsuarioServiceTest {

    private final UsuarioRepository usuarios = mock(UsuarioRepository.class);
    private final RefreshTokenService refresh = mock(RefreshTokenService.class);
    private final SesionUsuarioService sinMemoria = new SesionUsuarioService(usuarios, refresh, 0);

    @Test
    void unTokenConLaVersionActualDeUnUsuarioActivoSigueValiendo() {
        when(usuarios.estadoSesion(7L)).thenReturn(Optional.of(new EstadoSesion(true, 3)));

        sinMemoria.exigirVigente(7L, 3);
    }

    @Test
    void unaVersionDistintaCierraLaSesion() {
        when(usuarios.estadoSesion(7L)).thenReturn(Optional.of(new EstadoSesion(true, 4)));

        var ex = assertThrows(NoAutenticadoException.class, () -> sinMemoria.exigirVigente(7L, 3));

        assertEquals(SesionUsuarioService.SESION_CERRADA, ex.getMessage());
    }

    @Test
    void unaCuentaDesactivadaOInexistenteNoSirve() {
        when(usuarios.estadoSesion(7L)).thenReturn(Optional.of(new EstadoSesion(false, 3)));
        when(usuarios.estadoSesion(8L)).thenReturn(Optional.empty());

        assertThrows(NoAutenticadoException.class, () -> sinMemoria.exigirVigente(7L, 3));
        assertThrows(NoAutenticadoException.class, () -> sinMemoria.exigirVigente(8L, 0));
    }

    @Test
    void revocarSubeLaVersionYCierraLosRefreshTokens() {
        Usuario u = new Usuario();
        u.setId(7L);
        u.setVersionSesion(3);

        sinMemoria.revocarSesiones(u);

        assertEquals(4, u.getVersionSesion());
        verify(refresh).cerrarTodas(7L);
    }

    @Test
    void conMemoriaNoVuelveALaBaseHastaQueSeOlvida() {
        SesionUsuarioService conMemoria = new SesionUsuarioService(usuarios, refresh, 60);
        when(usuarios.estadoSesion(7L)).thenReturn(Optional.of(new EstadoSesion(true, 3)));

        conMemoria.exigirVigente(7L, 3);
        conMemoria.exigirVigente(7L, 3);
        verify(usuarios, times(1)).estadoSesion(7L);

        conMemoria.olvidar(7L);
        conMemoria.exigirVigente(7L, 3);
        verify(usuarios, times(2)).estadoSesion(7L);
    }

    @Test
    void revocarLimpiaLaMemoriaDeEseUsuario() {
        SesionUsuarioService conMemoria = new SesionUsuarioService(usuarios, refresh, 60);
        when(usuarios.estadoSesion(7L)).thenReturn(Optional.of(new EstadoSesion(true, 3)));
        conMemoria.exigirVigente(7L, 3);

        Usuario u = new Usuario();
        u.setId(7L);
        conMemoria.revocarSesiones(u);
        when(usuarios.estadoSesion(7L)).thenReturn(Optional.of(new EstadoSesion(true, 4)));

        assertThrows(NoAutenticadoException.class, () -> conMemoria.exigirVigente(7L, 3));
    }
}
