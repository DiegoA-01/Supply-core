package com.proyecto.supply_core.security.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.proyecto.supply_core.auditoria.enums.AccionAuditoria;
import com.proyecto.supply_core.auditoria.enums.EntidadAuditada;
import com.proyecto.supply_core.auditoria.service.AuditoriaService;
import com.proyecto.supply_core.common.exception.NoAutenticadoException;
import com.proyecto.supply_core.proveedor.entity.Proveedor;
import com.proyecto.supply_core.proveedor.enums.EstadoProveedor;
import com.proyecto.supply_core.proveedor.repository.ProveedorRepository;
import com.proyecto.supply_core.security.dto.LoginRequest;
import com.proyecto.supply_core.security.dto.LoginResponse;
import com.proyecto.supply_core.security.jwt.JwtProperties;
import com.proyecto.supply_core.security.jwt.JwtUtil;
import com.proyecto.supply_core.usuario.entity.Rol;
import com.proyecto.supply_core.usuario.entity.Usuario;
import com.proyecto.supply_core.usuario.repository.UsuarioRepository;

class AuthServiceTest {

    private final PasswordEncoder encoder = new BCryptPasswordEncoder(4); // costo bajo: test rápido
    private final UsuarioRepository repo = mock(UsuarioRepository.class);
    private final AuditoriaService auditoria = mock(AuditoriaService.class);
    private final ProveedorRepository proveedores = mock(ProveedorRepository.class);
    private AuthService service;
    private Usuario usuario;

    @BeforeEach
    void setUp() {
        JwtUtil jwt = new JwtUtil(new JwtProperties("clave-de-prueba-de-al-menos-32-caracteres!!", 60));
        service = new AuthService(repo, encoder, jwt, auditoria, proveedores);

        Rol rol = new Rol();
        rol.setCodigo("COMPRADOR");
        usuario = new Usuario();
        usuario.setId(5L);
        usuario.setCorreo("ana@demo.com");
        usuario.setNombreCompleto("Ana");
        usuario.setHashPassword(encoder.encode("Secreta123"));
        usuario.getRoles().add(rol);
        when(repo.findByCorreoIgnoreCase("ana@demo.com")).thenReturn(Optional.of(usuario));
    }

    @Test
    void loginCorrectoDevuelveTokenYAuditaExito() {
        LoginResponse r = service.login(new LoginRequest("ANA@demo.com", "Secreta123"));

        assertNotNull(r.token());
        assertEquals("Bearer", r.tipo());
        assertEquals(5L, r.usuario().id());
        assertNotNull(usuario.getUltimoAcceso());
        verify(auditoria).exitoDe(eq(5L), eq(AccionAuditoria.LOGIN), eq(EntidadAuditada.USUARIO), eq(5L), anyString());
    }

    @Test
    void claveIncorrectaYCorreoInexistenteDanElMismoMensajeYSeAuditan() {
        NoAutenticadoException clave = assertThrows(NoAutenticadoException.class,
                () -> service.login(new LoginRequest("ana@demo.com", "mala")));
        NoAutenticadoException correo = assertThrows(NoAutenticadoException.class,
                () -> service.login(new LoginRequest("nadie@demo.com", "Secreta123")));

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
                () -> service.login(new LoginRequest("ana@demo.com", "Secreta123")));

        assertEquals(AuthService.CUENTA_INACTIVA, ex.getMessage());
        verify(auditoria).fallo(eq(5L), eq(AccionAuditoria.LOGIN), eq(EntidadAuditada.USUARIO), eq(5L),
                contains("cuenta inactiva"));
        verify(auditoria, never()).exitoDe(eq(5L), eq(AccionAuditoria.LOGIN), eq(EntidadAuditada.USUARIO),
                eq(5L), anyString());
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
                    () -> service.login(new LoginRequest("ana@demo.com", "Secreta123")));
            assertEquals(AuthService.mensajeProveedor(estado), ex.getMessage());
        }

        p.setEstado(EstadoProveedor.HABILITADO);
        assertEquals(40L, service.login(new LoginRequest("ana@demo.com", "Secreta123")).usuario().proveedorId());
    }
}
