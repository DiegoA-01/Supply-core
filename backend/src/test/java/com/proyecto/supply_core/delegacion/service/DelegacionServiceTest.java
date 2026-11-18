package com.proyecto.supply_core.delegacion.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
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
import com.proyecto.supply_core.common.exception.AccesoDenegadoException;
import com.proyecto.supply_core.common.exception.NegocioException;
import com.proyecto.supply_core.common.exception.NoEncontradoException;
import com.proyecto.supply_core.delegacion.dto.DelegacionRequest;
import com.proyecto.supply_core.delegacion.dto.DelegadoCandidato;
import com.proyecto.supply_core.delegacion.entity.DelegacionAprobacion;
import com.proyecto.supply_core.delegacion.enums.EstadoDelegacion;
import com.proyecto.supply_core.delegacion.repository.DelegacionRepository;
import com.proyecto.supply_core.empresa.repository.PoliticaAprobacionRepository;
import com.proyecto.supply_core.notificacion.enums.EventoNotificacion;
import com.proyecto.supply_core.notificacion.service.NotificacionService;
import com.proyecto.supply_core.security.context.SesionActual;
import com.proyecto.supply_core.security.context.UsuarioAutenticado;
import com.proyecto.supply_core.usuario.entity.Usuario;
import com.proyecto.supply_core.usuario.repository.UsuarioRepository;

class DelegacionServiceTest {

    private final DelegacionRepository delegaciones = mock(DelegacionRepository.class);
    private final UsuarioRepository usuarios = mock(UsuarioRepository.class);
    private final PoliticaAprobacionRepository politicas = mock(PoliticaAprobacionRepository.class);
    private final AuditoriaService auditoria = mock(AuditoriaService.class);
    private final NotificacionService notificaciones = mock(NotificacionService.class);
    private final DelegacionService service = new DelegacionService(delegaciones, usuarios, politicas, auditoria,
            notificaciones);

    private final LocalDate hoy = LocalDate.now();
    private MockHttpServletRequest request;
    private Usuario delegado;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest();
        sesion(20L, "APROBADOR");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        delegado = usuario(30L, "Sofía", true, null);
        when(usuarios.findById(30L)).thenReturn(Optional.of(delegado));
        when(politicas.rolesAprobadoresActivos()).thenReturn(Set.of("APROBADOR", "ADMIN_COMPRAS"));
        when(delegaciones.save(any())).thenAnswer(i -> {
            DelegacionAprobacion d = i.getArgument(0);
            d.setId(9L);
            return d;
        });
        when(usuarios.findAllById(any())).thenReturn(List.of(usuario(20L, "Mario", true, null), delegado));
    }

    @AfterEach
    void limpiar() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void crearGuardaLaDelegacionAvisaAlDelegadoYAudita() {
        var r = service.crear(new DelegacionRequest(30L, hoy, hoy.plusDays(7), "  Vacaciones  "));

        assertEquals(9L, r.id());
        assertEquals(20L, r.deleganteId());
        assertEquals("Mario", r.deleganteNombre());
        assertEquals("Sofía", r.delegadoNombre());
        assertEquals("Vacaciones", r.motivo());
        assertTrue(r.vigente());
        assertEquals(EstadoDelegacion.ACTIVA, r.estado());
        verify(auditoria).exito(eq(AccionAuditoria.DELEGACION_CREAR), eq(EntidadAuditada.DELEGACION), eq(9L), anyString());
        verify(notificaciones).notificar(eq(30L), eq(EventoNotificacion.DELEGACION_ASIGNADA), anyString());
    }

    @Test
    void unaDelegacionFuturaNoEstaVigenteTodavia() {
        var r = service.crear(new DelegacionRequest(30L, hoy.plusDays(3), hoy.plusDays(10), null));

        assertFalse(r.vigente());
        assertEquals(null, r.motivo());
    }

    @Test
    void noSeDelegaASiMismoNiSinRolAprobadorNiAUnUsuarioInvalidoNiConFechaPasada() {
        assertEquals("DELEGADO_INVALIDO", codigo(() -> service.crear(new DelegacionRequest(20L, hoy, hoy, null))));

        sesion(20L, "COMPRADOR");
        assertEquals("SIN_ROL_APROBADOR", codigo(() -> service.crear(new DelegacionRequest(30L, hoy, hoy, null))));
        sesion(20L, "APROBADOR");

        delegado.setActivo(false);
        assertEquals("DELEGADO_INVALIDO", codigo(() -> service.crear(new DelegacionRequest(30L, hoy, hoy, null))));
        delegado.setActivo(true);
        delegado.setProveedorId(40L); // un usuario de proveedor no aprueba compras
        assertEquals("DELEGADO_INVALIDO", codigo(() -> service.crear(new DelegacionRequest(30L, hoy, hoy, null))));
        delegado.setProveedorId(null);

        assertEquals("VIGENCIA_PASADA", codigo(() -> service.crear(
                new DelegacionRequest(30L, hoy.minusDays(10), hoy.minusDays(1), null))));
        assertThrows(NoEncontradoException.class, () -> service.crear(new DelegacionRequest(99L, hoy, hoy, null)));
        verify(delegaciones, never()).save(any());
    }

    @Test
    void losCandidatosSonLosInternosActivosYSoloLosVeQuienPuedeDelegar() {
        when(usuarios.candidatosDeDelegacion(20L)).thenReturn(List.of(delegado));

        List<DelegadoCandidato> candidatos = service.candidatos();

        assertEquals(1, candidatos.size());
        assertEquals(30L, candidatos.get(0).id());
        assertEquals("Sofía", candidatos.get(0).nombre());

        sesion(20L, "COMPRADOR");
        assertEquals("SIN_ROL_APROBADOR", codigo(() -> service.candidatos()));
    }

    @Test
    void revocaElDelegantePeroNoOtroUsuarioYNoDosVeces() {
        DelegacionAprobacion d = delegacion(5L, 20L, 30L, hoy, hoy.plusDays(5));
        when(delegaciones.findById(5L)).thenReturn(Optional.of(d));

        sesion(77L, "APROBADOR");
        assertThrows(AccesoDenegadoException.class, () -> service.revocar(5L));

        sesion(20L, "APROBADOR");
        var r = service.revocar(5L);
        assertEquals(EstadoDelegacion.REVOCADA, r.estado());
        assertFalse(r.vigente());
        assertEquals(20L, d.getRevocadaPorUsuarioId());
        verify(auditoria).exito(eq(AccionAuditoria.DELEGACION_REVOCAR), eq(EntidadAuditada.DELEGACION), eq(5L), anyString());
        assertEquals("DELEGACION_REVOCADA", codigo(() -> service.revocar(5L)));
        assertThrows(NoEncontradoException.class, () -> service.revocar(6L));
    }

    @Test
    void adminComprasPuedeRevocarLaDeOtro() {
        DelegacionAprobacion d = delegacion(5L, 20L, 30L, hoy, hoy.plusDays(5));
        when(delegaciones.findById(5L)).thenReturn(Optional.of(d));
        sesion(88L, "ADMIN_COMPRAS");

        assertEquals(EstadoDelegacion.REVOCADA, service.revocar(5L).estado());
    }

    @Test
    void conSuPropioRolDecideSinDelegacion() {
        sesion(20L, "APROBADOR");

        Optional<Long> r = service.autorizar(SesionActual.usuario(), "APROBADOR", 10L);

        assertTrue(r.isEmpty());
        verify(delegaciones, never()).vigentesQueDanElRol(any(), any(), any(), any());
    }

    @Test
    void sinElRolDecidePorUnaDelegacionVigenteQueNoSeaDeQuienLoOriginoYSinoSeNiega() {
        sesion(30L, "ALMACENISTA");
        when(delegaciones.vigentesQueDanElRol(eq(30L), eq("APROBADOR"), any(), eq(10L))).thenReturn(List.of(9L, 12L));

        assertEquals(Optional.of(9L), service.autorizar(SesionActual.usuario(), "APROBADOR", 10L));

        // si la única delegación es de quien originó lo que se decide, la consulta no la devuelve
        when(delegaciones.vigentesQueDanElRol(eq(30L), eq("APROBADOR"), any(), eq(20L))).thenReturn(List.of());
        var ex = assertThrows(AccesoDenegadoException.class,
                () -> service.autorizar(SesionActual.usuario(), "APROBADOR", 20L));
        assertTrue(ex.getMessage().contains("APROBADOR"));
    }

    @Test
    void losRolesEfectivosSumanLosPropiosYLosDelegados() {
        sesion(30L, "ALMACENISTA");
        when(delegaciones.rolesDelegados(eq(30L), any())).thenReturn(Set.of("APROBADOR"));

        assertEquals(Set.of("ALMACENISTA", "APROBADOR"), service.rolesEfectivos(SesionActual.usuario()));
    }

    @Test
    void otorgadasYRecibidasListanLasDelUsuarioDeLaSesion() {
        when(delegaciones.findByDeleganteIdOrderByIdDesc(20L))
                .thenReturn(List.of(delegacion(5L, 20L, 30L, hoy, hoy.plusDays(5))));
        when(delegaciones.findByDelegadoIdOrderByIdDesc(20L)).thenReturn(List.of());

        assertEquals(1, service.otorgadas().size());
        assertEquals(0, service.recibidas().size());
        assertTrue(service.otorgadas().get(0).vigente());
    }

    // ----------------------------------------------------------------- apoyo

    private String codigo(Runnable accion) {
        return assertThrows(NegocioException.class, accion::run).getCodigo();
    }

    private void sesion(Long id, String rol) {
        request.setAttribute(SesionActual.ATRIBUTO, new UsuarioAutenticado(id, "u@x.com", "Mario", Set.of(rol), null, 1L));
    }

    private static Usuario usuario(Long id, String nombre, boolean activo, Long proveedorId) {
        Usuario u = new Usuario();
        u.setId(id);
        u.setNombreCompleto(nombre);
        u.setActivo(activo);
        u.setProveedorId(proveedorId);
        return u;
    }

    private static DelegacionAprobacion delegacion(Long id, Long delegante, Long delegado, LocalDate desde, LocalDate hasta) {
        DelegacionAprobacion d = new DelegacionAprobacion();
        d.setId(id);
        d.setDeleganteId(delegante);
        d.setDelegadoId(delegado);
        d.setVigenteDesde(desde);
        d.setVigenteHasta(hasta);
        return d;
    }
}
