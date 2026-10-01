package com.proyecto.supply_core.proveedor.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
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
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.proyecto.supply_core.auditoria.enums.AccionAuditoria;
import com.proyecto.supply_core.auditoria.enums.EntidadAuditada;
import com.proyecto.supply_core.auditoria.service.AuditoriaService;
import com.proyecto.supply_core.catalogo.entity.CategoriaProducto;
import com.proyecto.supply_core.catalogo.repository.CategoriaProductoRepository;
import com.proyecto.supply_core.common.exception.AccesoDenegadoException;
import com.proyecto.supply_core.common.exception.NegocioException;
import com.proyecto.supply_core.proveedor.dto.MotivoRequest;
import com.proyecto.supply_core.proveedor.dto.RegistroProveedorRequest;
import com.proyecto.supply_core.proveedor.entity.Proveedor;
import com.proyecto.supply_core.proveedor.enums.EstadoProveedor;
import com.proyecto.supply_core.proveedor.repository.ProveedorRepository;
import com.proyecto.supply_core.notificacion.enums.EventoNotificacion;
import com.proyecto.supply_core.notificacion.service.NotificacionService;
import com.proyecto.supply_core.security.context.Roles;
import com.proyecto.supply_core.security.context.SesionActual;
import com.proyecto.supply_core.security.context.UsuarioAutenticado;
import com.proyecto.supply_core.usuario.entity.Rol;
import com.proyecto.supply_core.usuario.entity.Usuario;
import com.proyecto.supply_core.usuario.repository.RolRepository;
import com.proyecto.supply_core.usuario.repository.UsuarioRepository;

class ProveedorServiceTest {

    private final ProveedorRepository proveedores = mock(ProveedorRepository.class);
    private final CategoriaProductoRepository categorias = mock(CategoriaProductoRepository.class);
    private final UsuarioRepository usuarios = mock(UsuarioRepository.class);
    private final RolRepository roles = mock(RolRepository.class);
    private final AuditoriaService auditoria = mock(AuditoriaService.class);
    private final NotificacionService notificaciones = mock(NotificacionService.class);
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private final ProveedorService service =
            new ProveedorService(proveedores, categorias, usuarios, roles, encoder, auditoria, notificaciones);
    private MockHttpServletRequest request;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest();
        sesion(new UsuarioAutenticado(1L, "compras@x.com", "Compras", Set.of("ADMIN_COMPRAS"), null));
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        when(proveedores.save(any())).thenAnswer(inv -> {
            Proveedor p = inv.getArgument(0);
            p.setId(40L);
            return p;
        });
        when(usuarios.save(any())).thenAnswer(inv -> {
            Usuario u = inv.getArgument(0);
            u.setId(77L);
            return u;
        });
    }

    @AfterEach
    void limpiar() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void registroCreaProveedorPendienteYUsuarioInactivoConRolProveedor() {
        CategoriaProducto cat = new CategoriaProducto();
        cat.setId(3L);
        cat.setNombre("Tecnología");
        when(categorias.findByIdInAndActivoTrue(anyCollection())).thenReturn(List.of(cat));
        Rol rol = new Rol();
        rol.setCodigo("PROVEEDOR");
        when(roles.findByCodigo("PROVEEDOR")).thenReturn(Optional.of(rol));

        var r = service.registrar(new RegistroProveedorRequest("Tech SAS", "900123456-7", " ", null,
                Set.of(3L), "Laura", "Ventas@Tech.com", "Clave1234"));

        assertEquals(40L, r.proveedorId());
        assertEquals(EstadoProveedor.PENDIENTE, r.estado());

        ArgumentCaptor<Usuario> captor = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarios).save(captor.capture());
        Usuario u = captor.getValue();
        assertFalse(u.isActivo());
        assertEquals(40L, u.getProveedorId());
        assertEquals("ventas@tech.com", u.getCorreo());
        assertEquals(Set.of("PROVEEDOR"), u.codigosRoles());
        assertTrue(encoder.matches("Clave1234", u.getHashPassword()));
        verify(auditoria).exitoDe(eq(77L), eq(AccionAuditoria.PROVEEDOR_REGISTRAR), eq(EntidadAuditada.PROVEEDOR),
                eq(40L), anyString());
        verify(notificaciones).notificarRol(eq(Roles.ADMIN_COMPRAS), eq(EventoNotificacion.PROVEEDOR_PENDIENTE),
                anyString());
    }

    @Test
    void habilitarActivaSusUsuariosYRegistraRevisor() {
        Proveedor p = proveedor(EstadoProveedor.PENDIENTE);
        Usuario u = new Usuario();
        u.setActivo(false);
        when(usuarios.findByProveedorId(40L)).thenReturn(List.of(u));

        var r = service.habilitar(40L);

        assertEquals(EstadoProveedor.HABILITADO, r.estado());
        assertTrue(u.isActivo());
        assertEquals(1L, p.getRevisadoPorUsuarioId());
        assertNotNull(p.getRevisadoEn());
        verify(auditoria).exito(eq(AccionAuditoria.PROVEEDOR_HABILITAR), eq(EntidadAuditada.PROVEEDOR), eq(40L),
                anyString());
    }

    @Test
    void rechazarGuardaMotivoYNoActivaUsuarios() {
        proveedor(EstadoProveedor.PENDIENTE);

        var r = service.rechazar(40L, new MotivoRequest("  Documentos incompletos  "));

        assertEquals(EstadoProveedor.RECHAZADO, r.estado());
        assertEquals("Documentos incompletos", r.motivoEstado());
    }

    @Test
    void suspenderDesactivaSusUsuarios() {
        proveedor(EstadoProveedor.HABILITADO);
        Usuario u = new Usuario();
        u.setActivo(true);
        when(usuarios.findByProveedorId(40L)).thenReturn(List.of(u));

        assertEquals(EstadoProveedor.SUSPENDIDO, service.suspender(40L, new MotivoRequest("Incumplió entregas")).estado());
        assertFalse(u.isActivo());
    }

    @Test
    void transicionesInvalidasSeRechazanSinAuditar() {
        proveedor(EstadoProveedor.RECHAZADO);
        assertEquals("TRANSICION_INVALIDA",
                assertThrows(NegocioException.class, () -> service.habilitar(40L)).getCodigo());

        proveedor(EstadoProveedor.PENDIENTE);
        assertThrows(NegocioException.class, () -> service.suspender(40L, new MotivoRequest("motivo largo")));

        proveedor(EstadoProveedor.HABILITADO);
        assertThrows(NegocioException.class, () -> service.rechazar(40L, new MotivoRequest("motivo largo")));
        verifyNoInteractions(auditoria);
    }

    @Test
    void miProveedorExigeUsuarioDeProveedor() {
        assertThrows(AccesoDenegadoException.class, service::miProveedor);

        sesion(new UsuarioAutenticado(77L, "v@t.com", "Laura", Set.of("PROVEEDOR"), 40L));
        proveedor(EstadoProveedor.HABILITADO);
        assertEquals(40L, service.miProveedor().id());
    }

    @Test
    void reglasDeTransicionDelEnum() {
        assertTrue(EstadoProveedor.PENDIENTE.puedePasarA(EstadoProveedor.HABILITADO));
        assertTrue(EstadoProveedor.PENDIENTE.puedePasarA(EstadoProveedor.RECHAZADO));
        assertTrue(EstadoProveedor.HABILITADO.puedePasarA(EstadoProveedor.SUSPENDIDO));
        assertTrue(EstadoProveedor.SUSPENDIDO.puedePasarA(EstadoProveedor.HABILITADO));
        assertFalse(EstadoProveedor.RECHAZADO.puedePasarA(EstadoProveedor.HABILITADO));
        assertFalse(EstadoProveedor.HABILITADO.puedePasarA(EstadoProveedor.PENDIENTE));
    }

    private Proveedor proveedor(EstadoProveedor estado) {
        Proveedor p = new Proveedor();
        p.setId(40L);
        p.setNit("900123456-7");
        p.setRazonSocial("Tech SAS");
        p.setEstado(estado);
        when(proveedores.findWithCategoriasById(40L)).thenReturn(Optional.of(p));
        return p;
    }

    private void sesion(UsuarioAutenticado u) {
        request.setAttribute(SesionActual.ATRIBUTO, u);
    }
}
