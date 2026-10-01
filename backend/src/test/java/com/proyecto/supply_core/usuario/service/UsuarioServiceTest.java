package com.proyecto.supply_core.usuario.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.proyecto.supply_core.auditoria.enums.AccionAuditoria;
import com.proyecto.supply_core.auditoria.enums.EntidadAuditada;
import com.proyecto.supply_core.auditoria.service.AuditoriaService;
import com.proyecto.supply_core.common.exception.NegocioException;
import com.proyecto.supply_core.security.context.SesionActual;
import com.proyecto.supply_core.security.context.UsuarioAutenticado;
import com.proyecto.supply_core.usuario.dto.CambioPasswordRequest;
import com.proyecto.supply_core.usuario.dto.UsuarioRequest;
import com.proyecto.supply_core.usuario.dto.UsuarioUpdateRequest;
import com.proyecto.supply_core.usuario.entity.Rol;
import com.proyecto.supply_core.usuario.entity.Usuario;
import com.proyecto.supply_core.usuario.repository.RolRepository;
import com.proyecto.supply_core.usuario.repository.UsuarioRepository;

class UsuarioServiceTest {

    private static final long ADMIN_ID = 1L;

    private final UsuarioRepository usuarios = mock(UsuarioRepository.class);
    private final RolRepository roles = mock(RolRepository.class);
    private final PasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private final AuditoriaService auditoria = mock(AuditoriaService.class);
    private final UsuarioService service = new UsuarioService(usuarios, roles, encoder, auditoria);
    private Usuario admin;

    @BeforeEach
    void setUp() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setAttribute(SesionActual.ATRIBUTO,
                new UsuarioAutenticado(ADMIN_ID, "admin@x.com", "Admin", Set.of("ADMIN_SISTEMA"), null));
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(req));

        admin = usuario(ADMIN_ID, "admin@x.com", rol("ADMIN_SISTEMA"));
        admin.setHashPassword(encoder.encode("ClaveActual1"));
        when(usuarios.findWithRolesById(ADMIN_ID)).thenReturn(Optional.of(admin));
        when(usuarios.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @AfterEach
    void limpiar() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void creaUsuarioConCorreoNormalizadoYClaveHasheada() {
        when(roles.findByCodigoIn(anyCollection())).thenReturn(List.of(rol("COMPRADOR")));

        var r = service.crear(new UsuarioRequest("Ana", "  ANA@Demo.com ", "Secreta123",
                Set.of("comprador"), null, null));

        assertEquals("ana@demo.com", r.correo());
        assertEquals(Set.of("COMPRADOR"), r.roles());
        verify(auditoria).exito(eq(AccionAuditoria.USUARIO_CREAR), eq(EntidadAuditada.USUARIO), any(),
                argThat(d -> d.contains("ana@demo.com") && !d.contains("Secreta123")));
    }

    @Test
    void editarConCorreoDeOtroUsuarioSeRechaza() {
        when(usuarios.existsByCorreoIgnoreCaseAndIdNot("ana@demo.com", ADMIN_ID)).thenReturn(true);
        var ex = assertThrows(NegocioException.class, () -> service.actualizar(ADMIN_ID,
                new UsuarioUpdateRequest("Admin", "ana@demo.com", Set.of("ADMIN_SISTEMA"), null, null)));
        assertEquals("CORREO_DUPLICADO", ex.getCodigo());
    }

    @Test
    void noPuedeDesactivarseASiMismo() {
        var ex = assertThrows(NegocioException.class, () -> service.desactivar(ADMIN_ID));
        assertEquals("AUTO_DESACTIVACION", ex.getCodigo());
        verifyNoInteractions(auditoria); // lo rechazado no se audita como éxito
    }

    @Test
    void noDejaSinAdministradorActivo() {
        Usuario otroAdmin = usuario(2L, "otro@x.com", rol("ADMIN_SISTEMA"));
        when(usuarios.findWithRolesById(2L)).thenReturn(Optional.of(otroAdmin));
        when(usuarios.countByActivoTrueAndRoles_Codigo("ADMIN_SISTEMA")).thenReturn(1L);
        when(roles.findByCodigoIn(anyCollection())).thenReturn(List.of(rol("COMPRADOR")));

        assertEquals("ULTIMO_ADMIN",
                assertThrows(NegocioException.class, () -> service.desactivar(2L)).getCodigo());
        assertEquals("ULTIMO_ADMIN", assertThrows(NegocioException.class, () -> service.actualizar(2L,
                new UsuarioUpdateRequest("Otro", "otro@x.com", Set.of("COMPRADOR"), null, null))).getCodigo());
    }

    @Test
    void desactivaCuandoHayOtrosAdmins() {
        Usuario otroAdmin = usuario(2L, "otro@x.com", rol("ADMIN_SISTEMA"));
        when(usuarios.findWithRolesById(2L)).thenReturn(Optional.of(otroAdmin));
        when(usuarios.countByActivoTrueAndRoles_Codigo("ADMIN_SISTEMA")).thenReturn(2L);

        assertFalse(service.desactivar(2L).activo());
        verify(auditoria).exito(eq(AccionAuditoria.USUARIO_DESACTIVAR), eq(EntidadAuditada.USUARIO), eq(2L),
                anyString());
    }

    @Test
    void cambioDePasswordExigeLaActualYUnaDistinta() {
        assertEquals("PASSWORD_ACTUAL", assertThrows(NegocioException.class,
                () -> service.cambiarPasswordPropia(new CambioPasswordRequest("mala", "NuevaClave1")))
                .getCodigo());
        assertEquals("PASSWORD_REPETIDA", assertThrows(NegocioException.class,
                () -> service.cambiarPasswordPropia(new CambioPasswordRequest("ClaveActual1", "ClaveActual1")))
                .getCodigo());

        service.cambiarPasswordPropia(new CambioPasswordRequest("ClaveActual1", "NuevaClave1"));
        assertTrue(encoder.matches("NuevaClave1", admin.getHashPassword()));
    }

    private static Rol rol(String codigo) {
        Rol r = new Rol();
        r.setCodigo(codigo);
        return r;
    }

    private static Usuario usuario(Long id, String correo, Rol rol) {
        Usuario u = new Usuario();
        u.setId(id);
        u.setEmpresaId(1L);
        u.setCorreo(correo);
        u.setNombreCompleto(correo);
        u.getRoles().add(rol);
        return u;
    }
}
