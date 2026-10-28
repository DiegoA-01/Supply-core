package com.proyecto.supply_core.plataforma.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.proyecto.supply_core.common.exception.NoAutenticadoException;
import com.proyecto.supply_core.plataforma.dto.LoginPlataformaRequest;
import com.proyecto.supply_core.plataforma.dto.MfaRequest;
import com.proyecto.supply_core.plataforma.entity.UsuarioPlataforma;
import com.proyecto.supply_core.plataforma.enums.AccionPlataforma;
import com.proyecto.supply_core.plataforma.repository.UsuarioPlataformaRepository;
import com.proyecto.supply_core.security.jwt.JwtProperties;
import com.proyecto.supply_core.security.jwt.JwtUtil;
import com.proyecto.supply_core.security.jwt.SesionProperties;

class PlataformaAuthServiceTest {

    private final PasswordEncoder encoder = new BCryptPasswordEncoder(4); // costo bajo: prueba rápida
    private final UsuarioPlataformaRepository repo = mock(UsuarioPlataformaRepository.class);
    private final PlataformaAuditoriaService auditoria = mock(PlataformaAuditoriaService.class);
    private final TotpService totp = new TotpService();
    private final JwtUtil jwt = new JwtUtil(new JwtProperties("clave-de-prueba-de-al-menos-32-caracteres!!", 15));
    private PlataformaAuthService service;
    private UsuarioPlataforma dueno;

    @BeforeEach
    void setUp() {
        service = new PlataformaAuthService(repo, encoder, jwt, totp, auditoria, new SesionProperties(7, 3, 15, 20));
        dueno = new UsuarioPlataforma();
        dueno.setId(3L);
        dueno.setCorreo("dueno@supplycore.local");
        dueno.setNombre("Dueño");
        dueno.setHashPassword(encoder.encode("ClaveSegura1234"));
        when(repo.findByCorreoIgnoreCase("dueno@supplycore.local")).thenReturn(Optional.of(dueno));
        when(repo.findById(3L)).thenReturn(Optional.of(dueno));
    }

    @Test
    void laPrimeraVezEntregaElSecretoParaVincularLaAppYElDesafio() {
        var r = service.login(new LoginPlataformaRequest(" DUENO@supplycore.local ", "ClaveSegura1234"));

        assertFalse(r.mfaConfigurado());
        assertNotNull(r.secretoMfa());
        assertEquals(dueno.getMfaSecreto(), r.secretoMfa());
        assertTrue(r.otpauthUri().contains(r.secretoMfa()));
        assertEquals(3L, jwt.validarDesafioMfa(r.desafio()));
        verify(auditoria).registrar(eq(3L), eq(AccionPlataforma.LOGIN), isNull(), eq(true), anyString());
    }

    @Test
    void conElMfaYaConfiguradoNoVuelveARevelarElSecreto() {
        dueno.setMfaSecreto(totp.nuevoSecreto());
        dueno.setMfaActivo(true);

        var r = service.login(new LoginPlataformaRequest("dueno@supplycore.local", "ClaveSegura1234"));

        assertTrue(r.mfaConfigurado());
        assertNull(r.secretoMfa());
        assertNull(r.otpauthUri());
    }

    @Test
    void correoInexistenteClaveMalaYCuentaInactivaRespondenIgual() {
        var mala = assertThrows(NoAutenticadoException.class,
                () -> service.login(new LoginPlataformaRequest("dueno@supplycore.local", "otra-clave")));
        var inexistente = assertThrows(NoAutenticadoException.class,
                () -> service.login(new LoginPlataformaRequest("nadie@x.com", "ClaveSegura1234")));
        dueno.setActivo(false);
        var inactiva = assertThrows(NoAutenticadoException.class,
                () -> service.login(new LoginPlataformaRequest("dueno@supplycore.local", "ClaveSegura1234")));

        assertEquals(PlataformaAuthService.CREDENCIALES_INVALIDAS, mala.getMessage());
        assertEquals(mala.getMessage(), inexistente.getMessage());
        assertEquals(mala.getMessage(), inactiva.getMessage());
        verify(auditoria, never()).registrar(any(), eq(AccionPlataforma.MFA), any(), eq(true), anyString());
    }

    @Test
    void tresClavesMalasBloqueanLaCuentaAunqueLaSiguienteSeaCorrecta() {
        for (int i = 0; i < 3; i++) {
            assertThrows(NoAutenticadoException.class,
                    () -> service.login(new LoginPlataformaRequest("dueno@supplycore.local", "mala" + System.nanoTime())));
        }

        assertTrue(dueno.getBloqueadoHasta().isAfter(LocalDateTime.now()));
        var ex = assertThrows(NoAutenticadoException.class,
                () -> service.login(new LoginPlataformaRequest("dueno@supplycore.local", "ClaveSegura1234")));
        assertEquals(PlataformaAuthService.CREDENCIALES_INVALIDAS, ex.getMessage());
    }

    @Test
    void elPrimerCodigoValidoConfirmaElMfaYEntregaLaSesionDeLaPlataforma() {
        var paso1 = service.login(new LoginPlataformaRequest("dueno@supplycore.local", "ClaveSegura1234"));
        String codigo = totp.codigo(paso1.secretoMfa(), totp.paso(Instant.now()));

        var sesion = service.verificarMfa(new MfaRequest(paso1.desafio(), codigo));

        assertEquals(3L, sesion.usuarioId());
        assertEquals("Bearer", sesion.tipo());
        assertEquals(3L, jwt.validarPlataforma(sesion.token()).id());
        assertTrue(dueno.isMfaActivo());
        assertNotNull(dueno.getMfaUltimoPaso());
        assertNotNull(dueno.getUltimoAcceso());
        verify(auditoria).registrar(eq(3L), eq(AccionPlataforma.MFA_CONFIGURAR), isNull(), eq(true), anyString());
        verify(auditoria).registrar(eq(3L), eq(AccionPlataforma.MFA), isNull(), eq(true), anyString());
    }

    @Test
    void elMismoCodigoNoSeAceptaDosVeces() {
        var paso1 = service.login(new LoginPlataformaRequest("dueno@supplycore.local", "ClaveSegura1234"));
        String codigo = totp.codigo(paso1.secretoMfa(), totp.paso(Instant.now()));
        service.verificarMfa(new MfaRequest(paso1.desafio(), codigo));

        var otro = service.login(new LoginPlataformaRequest("dueno@supplycore.local", "ClaveSegura1234"));
        var ex = assertThrows(NoAutenticadoException.class, () -> service.verificarMfa(new MfaRequest(otro.desafio(), codigo)));

        assertEquals(PlataformaAuthService.CODIGO_INVALIDO, ex.getMessage());
    }

    @Test
    void unCodigoMaloSumaFallosYTresBloqueanLaCuenta() {
        var paso1 = service.login(new LoginPlataformaRequest("dueno@supplycore.local", "ClaveSegura1234"));
        String malo = totp.codigo(paso1.secretoMfa(), totp.paso(Instant.now()) + 10);

        for (int i = 0; i < 3; i++) {
            assertThrows(NoAutenticadoException.class, () -> service.verificarMfa(new MfaRequest(paso1.desafio(), malo)));
        }

        assertFalse(dueno.isMfaActivo());
        assertNotNull(dueno.getBloqueadoHasta());
        String bueno = totp.codigo(paso1.secretoMfa(), totp.paso(Instant.now()));
        assertThrows(NoAutenticadoException.class, () -> service.verificarMfa(new MfaRequest(paso1.desafio(), bueno)));
    }

    @Test
    void unDesafioInvalidoOUnaSesionComoDesafioSeRechazan() {
        assertThrows(NoAutenticadoException.class, () -> service.verificarMfa(new MfaRequest("basura", "123456")));
        String sesion = jwt.generarPlataforma(new com.proyecto.supply_core.security.context.PlataformaAutenticada(3L,
                "dueno@supplycore.local", "Dueño"));
        assertThrows(NoAutenticadoException.class, () -> service.verificarMfa(new MfaRequest(sesion, "123456")));
    }

    @Test
    void sinHaberPedidoElSecretoNoHayMfaQueVerificar() {
        var desafio = jwt.generarDesafioMfa(3L); // sin pasar por el login: el usuario aún no tiene secreto

        assertThrows(NoAutenticadoException.class, () -> service.verificarMfa(new MfaRequest(desafio, "123456")));
    }
}
