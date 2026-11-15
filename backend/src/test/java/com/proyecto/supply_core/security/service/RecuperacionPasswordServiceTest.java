package com.proyecto.supply_core.security.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;

import com.proyecto.supply_core.auditoria.enums.AccionAuditoria;
import com.proyecto.supply_core.auditoria.enums.EntidadAuditada;
import com.proyecto.supply_core.auditoria.service.AuditoriaService;
import com.proyecto.supply_core.common.exception.NoAutenticadoException;
import com.proyecto.supply_core.notificacion.email.EmailClient;
import com.proyecto.supply_core.security.entity.RecuperacionPassword;
import com.proyecto.supply_core.security.repository.RecuperacionPasswordRepository;
import com.proyecto.supply_core.usuario.entity.Usuario;
import com.proyecto.supply_core.usuario.repository.UsuarioRepository;

class RecuperacionPasswordServiceTest {

    private final RecuperacionPasswordRepository repo = mock(RecuperacionPasswordRepository.class);
    private final UsuarioRepository usuarios = mock(UsuarioRepository.class);
    private final PasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private final SesionUsuarioService sesiones = mock(SesionUsuarioService.class);
    private final AuditoriaService auditoria = mock(AuditoriaService.class);
    private final EmailClient email = mock(EmailClient.class);
    private final RecuperacionPasswordService service = new RecuperacionPasswordService(repo, usuarios, encoder,
            sesiones, auditoria, email, mock(PlatformTransactionManager.class), 30);
    private Usuario ana;

    @BeforeEach
    void setUp() {
        ana = new Usuario();
        ana.setId(5L);
        ana.setEmpresaId(1L);
        ana.setCorreo("ana@demo.com");
        ana.setNombreCompleto("Ana");
        ana.setHashPassword(encoder.encode("ClaveVieja1"));
        ana.setIntentosFallidos(2);
        ana.setBloqueadoHasta(LocalDateTime.now().plusMinutes(10));
        when(usuarios.findByCorreoIgnoreCase("ana@demo.com")).thenReturn(Optional.of(ana));
        when(usuarios.findWithRolesById(5L)).thenReturn(Optional.of(ana));
    }

    /** Pide el token y devuelve el valor en claro que viajó por correo. */
    private String pedirYLeerElToken() {
        service.solicitar(" Ana@Demo.com ");
        ArgumentCaptor<String> cuerpo = ArgumentCaptor.forClass(String.class);
        verify(email).enviar(eq("ana@demo.com"), anyString(), cuerpo.capture());
        String texto = cuerpo.getValue();
        int desde = texto.indexOf("es: ") + 4;
        return texto.substring(desde, texto.indexOf(" .", desde));
    }

    @Test
    void pedirloGuardaSoloElHashYEnviaElValorPorCorreo() {
        String token = pedirYLeerElToken();

        ArgumentCaptor<RecuperacionPassword> guardado = ArgumentCaptor.forClass(RecuperacionPassword.class);
        verify(repo).save(guardado.capture());
        assertEquals(RefreshTokenService.hash(token), guardado.getValue().getTokenHash());
        assertFalse(guardado.getValue().getTokenHash().contains(token));
        assertEquals(43, token.length()); // 256 bits en base64 sin relleno
        assertTrue(guardado.getValue().getExpiraEn().isAfter(LocalDateTime.now().plusMinutes(29)));
        verify(repo).cerrarPendientes(eq(5L), any()); // un token nuevo deja sin efecto los anteriores
    }

    @Test
    void uncorreoInexistenteOUnaCuentaInactivaNoCreanNadaNiEnvianCorreo() {
        service.solicitar("nadie@demo.com");
        ana.setActivo(false);
        service.solicitar("ana@demo.com");

        verify(repo, never()).save(any());
        verifyNoInteractions(email);
        verify(auditoria, org.mockito.Mockito.times(2)).fallo(any(), eq(AccionAuditoria.PASSWORD_RECUPERAR_PEDIR),
                eq(EntidadAuditada.USUARIO), any(), anyString());
    }

    @Test
    void siYaPidioHaceUnMinutoNoSeEnviaOtroCorreo() {
        when(repo.existsByUsuarioIdAndCreadoEnAfter(eq(5L), any())).thenReturn(true);

        service.solicitar("ana@demo.com");

        verify(repo, never()).save(any());
        verifyNoInteractions(email);
    }

    @Test
    void siElCorreoFallaNoSeRevelaNada() {
        doThrow(new IllegalStateException("smtp caído")).when(email).enviar(anyString(), anyString(), anyString());

        service.solicitar("ana@demo.com"); // no lanza: quien lo pidió recibe la misma respuesta de siempre

        verify(repo).save(any());
    }

    @Test
    void elTokenCambiaLaClaveLevantaElBloqueoYCortaLasSesiones() {
        String token = pedirYLeerElToken();
        ArgumentCaptor<RecuperacionPassword> guardado = ArgumentCaptor.forClass(RecuperacionPassword.class);
        verify(repo).save(guardado.capture());
        when(repo.findByTokenHash(RefreshTokenService.hash(token))).thenReturn(Optional.of(conUsuario(guardado.getValue())));

        service.restablecer(token, "ClaveNueva1");

        assertTrue(encoder.matches("ClaveNueva1", ana.getHashPassword()));
        assertEquals(0, ana.getIntentosFallidos());
        assertNull(ana.getBloqueadoHasta());
        verify(sesiones).revocarSesiones(ana);
        verify(auditoria).exitoDe(eq(5L), eq(AccionAuditoria.PASSWORD_RECUPERAR_USAR), eq(EntidadAuditada.USUARIO),
                eq(5L), org.mockito.ArgumentMatchers.argThat(d -> !d.contains("ClaveNueva1") && !d.contains(token)));
    }

    @Test
    void unTokenUsadoVencidoInexistenteOdeUnaCuentaInactivaNoSirve() {
        RecuperacionPassword usado = pendiente("usado");
        usado.setUsadoEn(LocalDateTime.now().minusMinutes(1));
        RecuperacionPassword vencido = pendiente("vencido");
        vencido.setExpiraEn(LocalDateTime.now().minusMinutes(1));
        RecuperacionPassword deInactivo = pendiente("inactivo");
        when(repo.findByTokenHash(RefreshTokenService.hash("usado"))).thenReturn(Optional.of(usado));
        when(repo.findByTokenHash(RefreshTokenService.hash("vencido"))).thenReturn(Optional.of(vencido));
        when(repo.findByTokenHash(RefreshTokenService.hash("inactivo"))).thenReturn(Optional.of(deInactivo));
        ana.setActivo(false);

        for (String t : new String[] { "usado", "vencido", "inactivo", "no-existe" }) {
            var ex = assertThrows(NoAutenticadoException.class, () -> service.restablecer(t, "ClaveNueva1"));
            assertEquals(RecuperacionPasswordService.TOKEN_INVALIDO, ex.getMessage());
        }
        assertTrue(encoder.matches("ClaveVieja1", ana.getHashPassword())); // nada cambió
        verify(sesiones, never()).revocarSesiones(any());
    }

    private RecuperacionPassword pendiente(String valor) {
        RecuperacionPassword r = new RecuperacionPassword();
        r.setUsuarioId(5L);
        r.setTokenHash(RefreshTokenService.hash(valor));
        r.setExpiraEn(LocalDateTime.now().plusMinutes(20));
        return r;
    }

    private static RecuperacionPassword conUsuario(RecuperacionPassword r) {
        r.setUsuarioId(5L);
        return r;
    }
}
