package com.proyecto.supply_core.security.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.proyecto.supply_core.auditoria.enums.AccionAuditoria;
import com.proyecto.supply_core.auditoria.enums.EntidadAuditada;
import com.proyecto.supply_core.auditoria.service.AuditoriaService;
import com.proyecto.supply_core.common.exception.NegocioException;
import com.proyecto.supply_core.common.exception.NoAutenticadoException;
import com.proyecto.supply_core.plataforma.service.TotpService;
import com.proyecto.supply_core.security.context.SesionActual;
import com.proyecto.supply_core.security.context.UsuarioAutenticado;
import com.proyecto.supply_core.security.dto.LoginResponse;
import com.proyecto.supply_core.security.jwt.JwtProperties;
import com.proyecto.supply_core.security.jwt.JwtUtil;
import com.proyecto.supply_core.security.jwt.SesionProperties;
import com.proyecto.supply_core.usuario.entity.Rol;
import com.proyecto.supply_core.usuario.entity.Usuario;
import com.proyecto.supply_core.usuario.repository.UsuarioRepository;

class MfaUsuarioServiceTest {

    private final UsuarioRepository usuarios = mock(UsuarioRepository.class);
    private final TotpService totp = new TotpService();
    private final JwtUtil jwt = new JwtUtil(new JwtProperties("clave-de-prueba-de-al-menos-32-caracteres!!", 15));
    private final AuditoriaService auditoria = mock(AuditoriaService.class);
    private final SesionUsuarioService sesiones = mock(SesionUsuarioService.class);
    private final MfaUsuarioService service = new MfaUsuarioService(usuarios, totp, jwt, auditoria, sesiones,
            new SesionProperties(7, 3, 15, 20), true);
    private Usuario admin;
    private Usuario comprador;

    @BeforeEach
    void setUp() {
        admin = usuario(1L, "admin@demo.com", "ADMIN_SISTEMA");
        comprador = usuario(2L, "ana@demo.com", "COMPRADOR");
        when(usuarios.findWithRolesById(1L)).thenReturn(Optional.of(admin));
        when(usuarios.findWithRolesById(2L)).thenReturn(Optional.of(comprador));
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setAttribute(SesionActual.ATRIBUTO,
                new UsuarioAutenticado(1L, "admin@demo.com", "Admin", Set.of("ADMIN_SISTEMA"), null, 1L));
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(req));
    }

    @AfterEach
    void limpiar() {
        RequestContextHolder.resetRequestAttributes();
    }

    private String codigoActual(String secreto) {
        return totp.codigo(secreto, totp.paso(Instant.now()));
    }

    @Test
    void elAdminSiempreDebeUsarMfaYLosDemasSoloSiLoActivaron() {
        assertTrue(service.requiere(admin));
        assertFalse(service.requiere(comprador));
        comprador.setMfaActivo(true);
        assertTrue(service.requiere(comprador));

        MfaUsuarioService sinObligar = new MfaUsuarioService(usuarios, totp, jwt, auditoria, sesiones,
                new SesionProperties(7, 3, 15, 20), false);
        assertFalse(sinObligar.requiere(admin));
    }

    @Test
    void laPrimeraVezEntregaElSecretoParaVincularLaAppYDespuesYaNo() {
        LoginResponse primera = service.desafio(admin);

        assertFalse(primera.mfaConfigurado());
        assertNotNull(primera.secretoMfa());
        assertTrue(primera.otpauthUri().contains(primera.secretoMfa()));
        assertEquals(1L, jwt.validarDesafioMfaEmpresa(primera.desafioMfa()).usuarioId());
        assertEquals(1L, jwt.validarDesafioMfaEmpresa(primera.desafioMfa()).empresaId());
        assertNull(primera.token());

        admin.setMfaActivo(true);
        LoginResponse siguiente = service.desafio(admin);
        assertTrue(siguiente.mfaConfigurado());
        assertNull(siguiente.secretoMfa());
        assertNull(siguiente.otpauthUri());
    }

    @Test
    void elPrimerCodigoCorrectoActivaElMfaYElMismoCodigoNoSirveDosVeces() {
        String secreto = service.desafio(admin).secretoMfa();
        String codigo = codigoActual(secreto);

        Usuario u = service.verificarCodigo(1L, codigo);

        assertTrue(u.isMfaActivo());
        assertNotNull(u.getMfaUltimoPaso());
        verify(auditoria).exitoDe(eq(1L), eq(AccionAuditoria.MFA_ACTIVAR), eq(EntidadAuditada.USUARIO), eq(1L),
                anyString());
        var ex = assertThrows(NoAutenticadoException.class, () -> service.verificarCodigo(1L, codigo));
        assertEquals(MfaUsuarioService.CODIGO_INVALIDO, ex.getMessage());
    }

    @Test
    void tresCodigosMalosBloqueanLaCuentaAunqueElSiguienteSeaCorrecto() {
        String secreto = service.desafio(admin).secretoMfa();
        String malo = totp.codigo(secreto, totp.paso(Instant.now()) + 10);

        for (int i = 0; i < 3; i++) {
            assertThrows(NoAutenticadoException.class, () -> service.verificarCodigo(1L, malo));
        }

        assertTrue(admin.getBloqueadoHasta().isAfter(LocalDateTime.now()));
        String bueno = codigoActual(secreto);
        assertThrows(NoAutenticadoException.class, () -> service.verificarCodigo(1L, bueno));
        assertFalse(admin.isMfaActivo());
    }

    @Test
    void unaCuentaInactivaOSinSecretoNoPasa() {
        assertThrows(NoAutenticadoException.class, () -> service.verificarCodigo(1L, "123456")); // sin secreto
        admin.setMfaSecreto(totp.nuevoSecreto());
        admin.setActivo(false);
        assertThrows(NoAutenticadoException.class, () -> service.verificarCodigo(1L, "123456"));
        assertThrows(NoAutenticadoException.class, () -> service.verificarCodigo(99L, "123456"));
    }

    @Test
    void elMfaVoluntarioSeVinculaConUnCodigoDeLaApp() {
        when(usuarios.findWithRolesById(1L)).thenReturn(Optional.of(admin));

        var config = service.configurar();
        assertNotNull(config.secretoMfa());
        assertEquals(config.secretoMfa(), admin.getMfaSecreto());

        var malo = assertThrows(NegocioException.class, () -> service.activar("000000"));
        assertEquals("CODIGO_MFA_INVALIDO", malo.getCodigo());
        assertFalse(admin.isMfaActivo());

        service.activar(codigoActual(config.secretoMfa()));
        assertTrue(admin.isMfaActivo());
        assertEquals("MFA_YA_ACTIVO", assertThrows(NegocioException.class, () -> service.configurar()).getCodigo());
        assertEquals("MFA_YA_ACTIVO", assertThrows(NegocioException.class, () -> service.activar("123456")).getCodigo());
    }

    @Test
    void activarSinHaberPedidoElSecretoSeRechaza() {
        assertEquals("MFA_SIN_CONFIGURAR", assertThrows(NegocioException.class, () -> service.activar("123456")).getCodigo());
    }

    @Test
    void reiniciarQuitaElSegundoFactorYCortaLasSesionesPeroNoElPropio() {
        comprador.setMfaSecreto("SECRETO");
        comprador.setMfaActivo(true);
        comprador.setMfaUltimoPaso(5L);

        service.reiniciar(2L);

        assertFalse(comprador.isMfaActivo());
        assertNull(comprador.getMfaSecreto());
        assertNull(comprador.getMfaUltimoPaso());
        verify(sesiones).revocarSesiones(comprador);
        verify(auditoria).exito(eq(AccionAuditoria.MFA_DESACTIVAR), eq(EntidadAuditada.USUARIO), eq(2L), anyString());

        assertEquals("AUTO_REINICIO_MFA", assertThrows(NegocioException.class, () -> service.reiniciar(1L)).getCodigo());
        verify(sesiones, never()).revocarSesiones(admin);
    }

    @Test
    void elDesafioDeEmpresaNoSirveComoDesafioDeLaPlataformaNiViceversa() {
        String deEmpresa = jwt.generarDesafioMfaEmpresa(1L, 1L);
        String deLaPlataforma = jwt.generarDesafioMfa(1L);

        assertThrows(NoAutenticadoException.class, () -> jwt.validarDesafioMfa(deEmpresa));
        assertThrows(NoAutenticadoException.class, () -> jwt.validarDesafioMfaEmpresa(deLaPlataforma));
        assertThrows(NoAutenticadoException.class, () -> jwt.validarDesafioMfaEmpresa(jwt.generar(
                new UsuarioAutenticado(1L, "a@x.com", "A", Set.of(), null, 1L))));
    }

    private static Usuario usuario(Long id, String correo, String rolCodigo) {
        Rol rol = new Rol();
        rol.setCodigo(rolCodigo);
        Usuario u = new Usuario();
        u.setId(id);
        u.setEmpresaId(1L);
        u.setCorreo(correo);
        u.setNombreCompleto(correo);
        u.getRoles().add(rol);
        return u;
    }
}
