package com.proyecto.supply_core.security.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.proyecto.supply_core.auditoria.enums.AccionAuditoria;
import com.proyecto.supply_core.auditoria.enums.EntidadAuditada;
import com.proyecto.supply_core.auditoria.service.AuditoriaService;
import com.proyecto.supply_core.common.exception.AccesoDenegadoException;
import com.proyecto.supply_core.common.exception.NoAutenticadoException;
import com.proyecto.supply_core.plataforma.service.AccesoEmpresaService;
import com.proyecto.supply_core.proveedor.entity.Proveedor;
import com.proyecto.supply_core.proveedor.enums.EstadoProveedor;
import com.proyecto.supply_core.proveedor.repository.ProveedorRepository;
import com.proyecto.supply_core.security.dto.LoginRequest;
import com.proyecto.supply_core.security.dto.LoginResponse;
import com.proyecto.supply_core.security.jwt.DesafioMfaEmpresa;
import com.proyecto.supply_core.security.jwt.JwtProperties;
import com.proyecto.supply_core.security.jwt.JwtUtil;
import com.proyecto.supply_core.security.jwt.SesionProperties;
import com.proyecto.supply_core.usuario.entity.Rol;
import com.proyecto.supply_core.usuario.entity.Usuario;
import com.proyecto.supply_core.usuario.repository.UsuarioRepository;

class AuthServiceTest {

    private final PasswordEncoder encoder = new BCryptPasswordEncoder(4); // costo bajo: test rápido
    private final UsuarioRepository repo = mock(UsuarioRepository.class);
    private final AuditoriaService auditoria = mock(AuditoriaService.class);
    private final ProveedorRepository proveedores = mock(ProveedorRepository.class);
    private final RefreshTokenService refreshTokens = mock(RefreshTokenService.class);
    private final AccesoEmpresaService accesoEmpresa = mock(AccesoEmpresaService.class);
    private final MfaUsuarioService mfa = mock(MfaUsuarioService.class);
    private AuthService service;
    private Usuario usuario;

    @BeforeEach
    void setUp() {
        JwtUtil jwt = new JwtUtil(new JwtProperties("clave-de-prueba-de-al-menos-32-caracteres!!", 60));
        when(refreshTokens.emitir(5L)).thenReturn("refresh-1");
        service = new AuthService(repo, encoder, jwt, auditoria, proveedores, refreshTokens,
                new SesionProperties(7, 3, 15, 20), accesoEmpresa, mfa);

        Rol rol = new Rol();
        rol.setCodigo("COMPRADOR");
        usuario = new Usuario();
        usuario.setId(5L);
        usuario.setEmpresaId(1L);
        usuario.setCorreo("ana@demo.com");
        usuario.setNombreCompleto("Ana");
        usuario.setHashPassword(encoder.encode("Secreta123"));
        usuario.getRoles().add(rol);
        when(repo.findByCorreoIgnoreCase("ana@demo.com")).thenReturn(Optional.of(usuario));
    }

    @Test
    void unaEmpresaSuspendidaNoEntraAunqueLaClaveSeaCorrecta() {
        doThrow(new AccesoDenegadoException("SUSCRIPCION_SUSPENDIDA", "suspendida"))
                .when(accesoEmpresa).exigirPuedeEntrar(1L);

        var ex = assertThrows(AccesoDenegadoException.class,
                () -> service.login(new LoginRequest("demo", "ana@demo.com", "Secreta123")));

        assertEquals("SUSCRIPCION_SUSPENDIDA", ex.getCodigo());
        verify(refreshTokens, never()).emitir(any());
    }

    @Test
    void conUnaClaveMalaNoSeRevelaQueLaEmpresaEstaSuspendida() {
        doThrow(new AccesoDenegadoException("SUSCRIPCION_SUSPENDIDA", "suspendida"))
                .when(accesoEmpresa).exigirPuedeEntrar(1L);

        assertThrows(NoAutenticadoException.class,
                () -> service.login(new LoginRequest("demo", "ana@demo.com", "ClaveMala999")));
    }

    @Test
    void loginCorrectoDevuelveTokenYAuditaExito() {
        LoginResponse r = service.login(new LoginRequest("demo", "ANA@demo.com", "Secreta123"));

        assertNotNull(r.token());
        assertEquals("refresh-1", r.refreshToken());
        assertEquals("Bearer", r.tipo());
        assertEquals(5L, r.usuario().id());
        assertNotNull(usuario.getUltimoAcceso());
        verify(auditoria).exitoDe(eq(5L), eq(AccionAuditoria.LOGIN), eq(EntidadAuditada.USUARIO), eq(5L), anyString());
    }

    @Test
    void claveIncorrectaYCorreoInexistenteDanElMismoMensajeYSeAuditan() {
        NoAutenticadoException clave = assertThrows(NoAutenticadoException.class,
                () -> service.login(new LoginRequest("demo", "ana@demo.com", "mala")));
        NoAutenticadoException correo = assertThrows(NoAutenticadoException.class,
                () -> service.login(new LoginRequest("demo", "nadie@demo.com", "Secreta123")));

        assertEquals(AuthService.CREDENCIALES_INVALIDAS, clave.getMessage());
        assertEquals(clave.getMessage(), correo.getMessage());
        verify(auditoria).fallo(eq(5L), eq(AccionAuditoria.LOGIN), eq(EntidadAuditada.USUARIO), eq(5L),
                contains("contraseña incorrecta"));
        verify(auditoria).fallo(isNull(), eq(AccionAuditoria.LOGIN), eq(EntidadAuditada.USUARIO), isNull(),
                contains("correo no registrado"));
    }

    @Test
    void cuentaInactivaSeRechazaYSeAudita() {
        usuario.setActivo(false);
        NoAutenticadoException ex = assertThrows(NoAutenticadoException.class,
                () -> service.login(new LoginRequest("demo", "ana@demo.com", "Secreta123")));

        assertEquals(AuthService.CUENTA_INACTIVA, ex.getMessage());
        verify(auditoria).fallo(eq(5L), eq(AccionAuditoria.LOGIN), eq(EntidadAuditada.USUARIO), eq(5L),
                contains("cuenta inactiva"));
        verify(auditoria, never()).exitoDe(eq(5L), eq(AccionAuditoria.LOGIN), eq(EntidadAuditada.USUARIO),
                eq(5L), anyString());
    }

    @Test
    void tresClavesMalasBloqueanLaCuentaYBloqueadaRechazaAunqueLaClaveSeaCorrecta() {
        for (int i = 0; i < 3; i++) {
            assertThrows(NoAutenticadoException.class,
                    () -> service.login(new LoginRequest("demo", "ana@demo.com", "mala")));
        }

        assertNotNull(usuario.getBloqueadoHasta());
        assertEquals(0, usuario.getIntentosFallidos());
        verify(auditoria).fallo(eq(5L), eq(AccionAuditoria.CUENTA_BLOQUEADA), eq(EntidadAuditada.USUARIO), eq(5L),
                anyString());

        NoAutenticadoException ex = assertThrows(NoAutenticadoException.class,
                () -> service.login(new LoginRequest("demo", "ana@demo.com", "Secreta123")));
        assertEquals(AuthService.CREDENCIALES_INVALIDAS, ex.getMessage());
        verify(refreshTokens, never()).emitir(5L);
    }

    @Test
    void loginCorrectoReiniciaElContadorDeFallos() {
        assertThrows(NoAutenticadoException.class, () -> service.login(new LoginRequest("demo", "ana@demo.com", "mala")));
        assertEquals(1, usuario.getIntentosFallidos());

        service.login(new LoginRequest("demo", "ana@demo.com", "Secreta123"));

        assertEquals(0, usuario.getIntentosFallidos());
        assertNull(usuario.getBloqueadoHasta());
    }

    @Test
    void elBloqueoVencidoYaNoImpideEntrar() {
        usuario.setBloqueadoHasta(LocalDateTime.now().minusMinutes(1));

        assertNotNull(service.login(new LoginRequest("demo", "ana@demo.com", "Secreta123")).token());
        assertNull(usuario.getBloqueadoHasta());
    }

    @Test
    void refrescarEntregaJwtNuevoYElRefreshRotado() {
        when(refreshTokens.rotar("viejo")).thenReturn(new RefreshTokenService.Renovacion(5L, "nuevo"));
        when(repo.findWithRolesById(5L)).thenReturn(Optional.of(usuario));

        LoginResponse r = service.refrescar("viejo");

        assertNotNull(r.token());
        assertEquals("nuevo", r.refreshToken());
    }

    @Test
    void refrescarConUsuarioDesactivadoCierraTodasLasSesiones() {
        when(refreshTokens.rotar("viejo")).thenReturn(new RefreshTokenService.Renovacion(5L, "nuevo"));
        when(repo.findWithRolesById(5L)).thenReturn(Optional.of(usuario));
        usuario.setActivo(false);

        assertThrows(NoAutenticadoException.class, () -> service.refrescar("viejo"));

        verify(refreshTokens).cerrarTodas(5L);
    }

    @Test
    void proveedorSoloEntraSiEstaHabilitado() {
        usuario.setProveedorId(40L);
        Proveedor p = new Proveedor();
        when(proveedores.findById(40L)).thenReturn(Optional.of(p));

        for (EstadoProveedor estado : new EstadoProveedor[] { EstadoProveedor.PENDIENTE,
                EstadoProveedor.RECHAZADO, EstadoProveedor.SUSPENDIDO }) {
            p.setEstado(estado);
            var ex = assertThrows(NoAutenticadoException.class,
                    () -> service.login(new LoginRequest("demo", "ana@demo.com", "Secreta123")));
            assertEquals(AuthService.mensajeProveedor(estado), ex.getMessage());
        }

        p.setEstado(EstadoProveedor.HABILITADO);
        assertEquals(40L, service.login(new LoginRequest("demo", "ana@demo.com", "Secreta123")).usuario().proveedorId());
    }

    @Test
    void quienDebeUsarMfaRecibeUnDesafioYNoUnaSesion() {
        when(mfa.requiere(usuario)).thenReturn(true);
        when(mfa.desafio(usuario)).thenReturn(LoginResponse.conDesafio("desafio", true, null, null));

        LoginResponse r = service.login(new LoginRequest("demo", "ana@demo.com", "Secreta123"));

        assertEquals("desafio", r.desafioMfa());
        assertNull(r.token());
        assertNull(r.refreshToken());
        verify(refreshTokens, never()).emitir(any());
        assertNull(usuario.getUltimoAcceso()); // todavía no entró
    }

    @Test
    void elCodigoCorrectoAbreLaSesionYUnoMaloNo() {
        when(mfa.verificarCodigo(5L, "123456")).thenReturn(usuario);
        when(mfa.verificarCodigo(5L, "000000")).thenThrow(new NoAutenticadoException("Código inválido"));

        LoginResponse r = service.verificarMfa(5L, "123456");

        assertNotNull(r.token());
        assertEquals("refresh-1", r.refreshToken());
        assertNotNull(usuario.getUltimoAcceso());
        assertThrows(NoAutenticadoException.class, () -> service.verificarMfa(5L, "000000"));
        verify(refreshTokens, org.mockito.Mockito.times(1)).emitir(5L);
    }

    @Test
    void elTokenDeSesionLlevaLaVersionDeSesionDelUsuario() {
        usuario.setVersionSesion(7);

        LoginResponse r = service.login(new LoginRequest("demo", "ana@demo.com", "Secreta123"));

        JwtUtil jwt = new JwtUtil(new JwtProperties("clave-de-prueba-de-al-menos-32-caracteres!!", 60));
        assertEquals(7, jwt.versionSesion(r.token()));
    }

    @Test
    void elDesafioDeMfaSeLeeDelJwt() {
        JwtUtil jwt = new JwtUtil(new JwtProperties("clave-de-prueba-de-al-menos-32-caracteres!!", 60));
        AuthService conJwtPropio = new AuthService(repo, encoder, jwt, auditoria, proveedores, refreshTokens,
                new SesionProperties(7, 3, 15, 20), accesoEmpresa, mfa);

        DesafioMfaEmpresa d = conJwtPropio.leerDesafio(jwt.generarDesafioMfaEmpresa(5L, 1L));

        assertEquals(5L, d.usuarioId());
        assertEquals(1L, d.empresaId());
        assertThrows(NoAutenticadoException.class, () -> conJwtPropio.leerDesafio("basura"));
    }
}
