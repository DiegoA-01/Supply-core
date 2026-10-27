package com.proyecto.supply_core.security.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;

import com.proyecto.supply_core.auditoria.enums.AccionAuditoria;
import com.proyecto.supply_core.auditoria.enums.EntidadAuditada;
import com.proyecto.supply_core.auditoria.service.AuditoriaService;
import com.proyecto.supply_core.common.exception.NoAutenticadoException;
import com.proyecto.supply_core.security.entity.RefreshToken;
import com.proyecto.supply_core.security.jwt.SesionProperties;
import com.proyecto.supply_core.security.repository.RefreshTokenRepository;

class RefreshTokenServiceTest {

    private final RefreshTokenRepository repo = mock(RefreshTokenRepository.class);
    private final AuditoriaService auditoria = mock(AuditoriaService.class);
    private RefreshTokenService service;

    @BeforeEach
    void setUp() {
        service = new RefreshTokenService(repo, auditoria, new SesionProperties(7, 5, 15, 20),
                mock(PlatformTransactionManager.class));
    }

    @Test
    void emitirGuardaSoloElHashYDevuelveElValorEnClaro() {
        String valor = service.emitir(5L);

        ArgumentCaptor<RefreshToken> c = ArgumentCaptor.forClass(RefreshToken.class);
        verify(repo).save(c.capture());
        RefreshToken t = c.getValue();
        assertEquals(RefreshTokenService.hash(valor), t.getTokenHash());
        assertNotEquals(valor, t.getTokenHash());
        assertEquals(64, t.getTokenHash().length());
        assertEquals(5L, t.getUsuarioId());
        assertNotNull(t.getFamilia());
        assertTrue(t.getExpiraEn().isAfter(LocalDateTime.now().plusDays(6)));
    }

    @Test
    void rotarMarcaElUsadoYEmiteOtroDeLaMismaFamilia() {
        RefreshToken actual = token("fam-1");
        when(repo.findByTokenHash(RefreshTokenService.hash("v1"))).thenReturn(Optional.of(actual));

        RefreshTokenService.Renovacion r = service.rotar("v1");

        assertEquals(5L, r.usuarioId());
        assertNotNull(actual.getUsadoEn());
        ArgumentCaptor<RefreshToken> c = ArgumentCaptor.forClass(RefreshToken.class);
        verify(repo).save(c.capture());
        assertEquals("fam-1", c.getValue().getFamilia());
        assertEquals(RefreshTokenService.hash(r.refreshToken()), c.getValue().getTokenHash());
    }

    @Test
    void unTokenYaUsadoRevocaTodaLaFamiliaYSeAudita() {
        RefreshToken actual = token("fam-1");
        actual.setUsadoEn(LocalDateTime.now().minusMinutes(1));
        when(repo.findByTokenHash(RefreshTokenService.hash("v1"))).thenReturn(Optional.of(actual));

        NoAutenticadoException ex = assertThrows(NoAutenticadoException.class, () -> service.rotar("v1"));

        assertEquals(RefreshTokenService.SESION_INVALIDA, ex.getMessage());
        verify(repo).revocarFamilia(eq("fam-1"), any(LocalDateTime.class));
        verify(auditoria).fallo(eq(5L), eq(AccionAuditoria.REFRESH_REUTILIZADO), eq(EntidadAuditada.USUARIO),
                eq(5L), anyString());
        verify(repo, never()).save(any());
    }

    @Test
    void tokenInexistenteOVencidoSeRechazaConElMismoMensaje() {
        NoAutenticadoException noExiste = assertThrows(NoAutenticadoException.class, () -> service.rotar("x"));

        RefreshToken vencido = token("fam-2");
        vencido.setExpiraEn(LocalDateTime.now().minusMinutes(1));
        when(repo.findByTokenHash(RefreshTokenService.hash("v2"))).thenReturn(Optional.of(vencido));
        NoAutenticadoException esVencido = assertThrows(NoAutenticadoException.class, () -> service.rotar("v2"));

        assertEquals(noExiste.getMessage(), esVencido.getMessage());
        verify(repo, never()).save(any());
    }

    @Test
    void cerrarSesionRevocaLaFamiliaYNoFallaSiNoExiste() {
        when(repo.findByTokenHash(RefreshTokenService.hash("v1"))).thenReturn(Optional.of(token("fam-1")));

        service.cerrarSesion("v1");
        service.cerrarSesion("desconocido");

        verify(repo).revocarFamilia(eq("fam-1"), any(LocalDateTime.class));
    }

    @Test
    void cerrarTodasRevocaLasSesionesDelUsuario() {
        service.cerrarTodas(5L);

        verify(repo).revocarDeUsuario(eq(5L), any(LocalDateTime.class));
    }

    private static RefreshToken token(String familia) {
        RefreshToken t = new RefreshToken();
        t.setUsuarioId(5L);
        t.setFamilia(familia);
        t.setExpiraEn(LocalDateTime.now().plusDays(1));
        return t;
    }
}
