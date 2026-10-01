package com.proyecto.supply_core.auditoria.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.proyecto.supply_core.auditoria.dto.AuditoriaFiltro;
import com.proyecto.supply_core.auditoria.entity.Auditoria;
import com.proyecto.supply_core.auditoria.enums.AccionAuditoria;
import com.proyecto.supply_core.auditoria.enums.EntidadAuditada;
import com.proyecto.supply_core.auditoria.enums.ResultadoAuditoria;
import com.proyecto.supply_core.auditoria.repository.AuditoriaRepository;
import com.proyecto.supply_core.common.exception.ParametroInvalidoException;
import com.proyecto.supply_core.security.context.SesionActual;
import com.proyecto.supply_core.security.context.UsuarioAutenticado;
import com.proyecto.supply_core.usuario.entity.Usuario;
import com.proyecto.supply_core.usuario.repository.UsuarioRepository;

class AuditoriaServiceTest {

    private final AuditoriaRepository repo = mock(AuditoriaRepository.class);
    private final UsuarioRepository usuarios = mock(UsuarioRepository.class);
    private final AuditoriaService service = new AuditoriaService(repo, usuarios);
    private MockHttpServletRequest request;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.7");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @AfterEach
    void limpiar() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void exitoTomaElUsuarioDeLaSesionYLaIp() {
        request.setAttribute(SesionActual.ATRIBUTO,
                new UsuarioAutenticado(3L, "a@x.com", "Ana", Set.of("ADMIN_SISTEMA"), null));

        service.exito(AccionAuditoria.USUARIO_CREAR, EntidadAuditada.USUARIO, 9L, "correo=b@x.com");

        Auditoria a = guardado();
        assertEquals(3L, a.getUsuarioId());
        assertEquals("USUARIO", a.getEntidad());
        assertEquals(9L, a.getEntidadId());
        assertEquals("USUARIO_CREAR", a.getAccion());
        assertEquals(ResultadoAuditoria.EXITO, a.getResultado());
        assertEquals("10.0.0.7", a.getIp());
    }

    @Test
    void falloSinUsuarioYDetalleRecortado() {
        service.fallo(null, AccionAuditoria.LOGIN, EntidadAuditada.USUARIO, null, "x".repeat(5000));

        Auditoria a = guardado();
        assertNull(a.getUsuarioId());
        assertEquals(ResultadoAuditoria.FALLO, a.getResultado());
        assertEquals(4000, a.getDetalle().length());
    }

    @Test
    void siNoSePuedeGuardarElFalloNoSeOcultaElErrorOriginal() {
        when(repo.save(any())).thenThrow(new IllegalStateException("BD caída"));
        assertDoesNotThrow(() -> service.fallo(1L, AccionAuditoria.LOGIN, EntidadAuditada.USUARIO, 1L, "x"));
    }

    @Test
    void siNoSePuedeGuardarUnExitoLaOperacionFalla() {
        when(repo.save(any())).thenThrow(new IllegalStateException("BD caída"));
        assertThrows(IllegalStateException.class,
                () -> service.exitoDe(1L, AccionAuditoria.LOGIN, EntidadAuditada.USUARIO, 1L, "x"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void buscarAgregaNombreYCorreoDelUsuario() {
        Auditoria conUsuario = new Auditoria(3L, "USUARIO", 9L, "USUARIO_CREAR", ResultadoAuditoria.EXITO, null, null);
        Auditoria anonima = new Auditoria(null, "USUARIO", null, "LOGIN", ResultadoAuditoria.FALLO, null, null);
        Pageable pagina = PageRequest.of(0, 50, Sort.by("creadoEn").descending());
        when(repo.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(conUsuario, anonima), pagina, 2));
        Usuario ana = new Usuario();
        ana.setId(3L);
        ana.setNombreCompleto("Ana");
        ana.setCorreo("a@x.com");
        when(usuarios.findAllById(anyIterable())).thenReturn(List.of(ana));

        var r = service.buscar(filtroVacio(), pagina);

        assertEquals(2, r.totalElementos());
        assertEquals("Ana", r.contenido().get(0).usuarioNombre());
        assertEquals("a@x.com", r.contenido().get(0).usuarioCorreo());
        assertNull(r.contenido().get(1).usuarioNombre());
    }

    @Test
    void buscarRechazaOrdenNoPermitido() {
        assertThrows(ParametroInvalidoException.class,
                () -> service.buscar(filtroVacio(), PageRequest.of(0, 10, Sort.by("detalle"))));
    }

    @Test
    void filtroDetectaRangoInvertido() {
        LocalDateTime hoy = LocalDateTime.of(2026, 10, 1, 0, 0);
        assertEquals(false, new AuditoriaFiltro(null, null, null, null, null, hoy, hoy.minusDays(1)).isRangoValido());
        assertEquals(true, new AuditoriaFiltro(null, null, null, null, null, hoy, hoy).isRangoValido());
    }

    private Auditoria guardado() {
        ArgumentCaptor<Auditoria> captor = ArgumentCaptor.forClass(Auditoria.class);
        verify(repo).save(captor.capture());
        return captor.getValue();
    }

    private static AuditoriaFiltro filtroVacio() {
        return new AuditoriaFiltro(null, null, null, null, null, null, null);
    }
}
