package com.proyecto.supply_core.compra.solicitud.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.proyecto.supply_core.auditoria.enums.AccionAuditoria;
import com.proyecto.supply_core.auditoria.enums.EntidadAuditada;
import com.proyecto.supply_core.auditoria.service.AuditoriaService;
import com.proyecto.supply_core.common.exception.AccesoDenegadoException;
import com.proyecto.supply_core.common.exception.NegocioException;
import com.proyecto.supply_core.compra.solicitud.dto.DecisionRequest;
import com.proyecto.supply_core.compra.solicitud.dto.SolicitudResponse;
import com.proyecto.supply_core.compra.solicitud.entity.AprobacionSolicitud;
import com.proyecto.supply_core.compra.solicitud.entity.SolicitudCompra;
import com.proyecto.supply_core.compra.solicitud.enums.DecisionAprobacion;
import com.proyecto.supply_core.compra.solicitud.enums.EstadoSolicitud;
import com.proyecto.supply_core.compra.solicitud.repository.AprobacionSolicitudRepository;
import com.proyecto.supply_core.compra.solicitud.repository.SolicitudCompraRepository;
import com.proyecto.supply_core.empresa.entity.PoliticaAprobacion;
import com.proyecto.supply_core.delegacion.repository.DelegacionRepository;
import com.proyecto.supply_core.delegacion.service.DelegacionService;
import com.proyecto.supply_core.empresa.repository.PoliticaAprobacionRepository;
import com.proyecto.supply_core.notificacion.enums.EventoNotificacion;
import com.proyecto.supply_core.notificacion.service.NotificacionService;
import com.proyecto.supply_core.security.context.SesionActual;
import com.proyecto.supply_core.security.context.UsuarioAutenticado;
import com.proyecto.supply_core.usuario.entity.Rol;
import com.proyecto.supply_core.usuario.repository.UsuarioRepository;

class AprobacionSolicitudServiceTest {

    private final SolicitudService solicitudService = mock(SolicitudService.class);
    private final SolicitudCompraRepository solicitudes = mock(SolicitudCompraRepository.class);
    private final AprobacionSolicitudRepository aprobaciones = mock(AprobacionSolicitudRepository.class);
    private final PoliticaAprobacionRepository politicas = mock(PoliticaAprobacionRepository.class);
    private final AuditoriaService auditoria = mock(AuditoriaService.class);
    private final NotificacionService notificaciones = mock(NotificacionService.class);
    private final DelegacionRepository delegacionRepo = mock(DelegacionRepository.class);
    private final DelegacionService delegaciones = new DelegacionService(delegacionRepo, mock(UsuarioRepository.class),
            mock(PoliticaAprobacionRepository.class), auditoria, notificaciones);
    private final AprobacionSolicitudService service = new AprobacionSolicitudService(solicitudService, solicitudes,
            aprobaciones, politicas, auditoria, notificaciones, delegaciones);

    private MockHttpServletRequest request;
    private SolicitudCompra s;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest();
        sesion(20L, "APROBADOR");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        s = new SolicitudCompra();
        s.setId(1L);
        s.setConsecutivo("SOL-000001");
        s.setUsuarioSolicitanteId(10L);
        s.setPoliticaAprobacionId(7L);
        s.setEstado(EstadoSolicitud.PENDIENTE_APROBACION);
        when(solicitudService.bloquear(1L)).thenReturn(s);
        when(solicitudService.respuesta(s)).thenAnswer(i -> SolicitudResponse.of(s, List.of()));
        Rol rol = new Rol();
        rol.setCodigo("APROBADOR");
        PoliticaAprobacion p = new PoliticaAprobacion();
        p.setRolAprobador(rol);
        when(politicas.findWithRolById(7L)).thenReturn(Optional.of(p));
    }

    @AfterEach
    void limpiar() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void aprobarGuardaDecisionAvisaAlSolicitanteYACompras() {
        var r = service.decidir(1L, new DecisionRequest(DecisionAprobacion.APROBADA, "  "));

        assertEquals(EstadoSolicitud.APROBADA, r.estado());
        ArgumentCaptor<AprobacionSolicitud> captor = ArgumentCaptor.forClass(AprobacionSolicitud.class);
        verify(aprobaciones).save(captor.capture());
        assertEquals(20L, captor.getValue().getUsuarioAprobadorId());
        assertEquals(7L, captor.getValue().getPoliticaAprobacionId());
        assertEquals(null, captor.getValue().getObservaciones());
        verify(auditoria).exito(eq(AccionAuditoria.SOLICITUD_DECIDIR), eq(EntidadAuditada.SOLICITUD_COMPRA), eq(1L),
                anyString());
        verify(notificaciones).notificar(eq(10L), eq(EventoNotificacion.SOLICITUD_DECIDIDA), anyString());
        verify(notificaciones).notificarRol(eq("COMPRADOR"), eq(EventoNotificacion.SOLICITUD_APROBADA), anyString());
    }

    @Test
    void devolverLaDejaEditableParaElSolicitante() {
        var r = service.decidir(1L, new DecisionRequest(DecisionAprobacion.DEVUELTA, "Falta cotizar marca"));

        assertEquals(EstadoSolicitud.DEVUELTA, r.estado());
        assertEquals(true, EstadoSolicitud.DEVUELTA.esEditable());
        verify(notificaciones, never()).notificarRol(any(), any(), any());
    }

    @Test
    void segregacionDeFunciones() {
        sesion(10L, "APROBADOR"); // el mismo solicitante
        assertThrows(AccesoDenegadoException.class,
                () -> service.decidir(1L, new DecisionRequest(DecisionAprobacion.APROBADA, null)));
        verify(aprobaciones, never()).save(any());
    }

    @Test
    void soloElRolDeLaPoliticaDecide() {
        sesion(20L, "COMPRADOR");
        assertThrows(AccesoDenegadoException.class,
                () -> service.decidir(1L, new DecisionRequest(DecisionAprobacion.APROBADA, null)));
    }

    @Test
    void sinElRolPeroConUnaDelegacionVigenteDecideYQuedaGuardadaLaDelegacion() {
        sesion(20L, "COMPRADOR");
        when(delegacionRepo.vigentesQueDanElRol(eq(20L), eq("APROBADOR"), any(), eq(10L))).thenReturn(List.of(5L));

        var r = service.decidir(1L, new DecisionRequest(DecisionAprobacion.APROBADA, null));

        assertEquals(EstadoSolicitud.APROBADA, r.estado());
        ArgumentCaptor<AprobacionSolicitud> captor = ArgumentCaptor.forClass(AprobacionSolicitud.class);
        verify(aprobaciones).save(captor.capture());
        assertEquals(20L, captor.getValue().getUsuarioAprobadorId());
        assertEquals(5L, captor.getValue().getDelegacionId());
    }

    @Test
    void conSuPropioRolLaDecisionNoLlevaDelegacion() {
        service.decidir(1L, new DecisionRequest(DecisionAprobacion.APROBADA, null));

        ArgumentCaptor<AprobacionSolicitud> captor = ArgumentCaptor.forClass(AprobacionSolicitud.class);
        verify(aprobaciones).save(captor.capture());
        assertEquals(null, captor.getValue().getDelegacionId());
    }

    @Test
    void laDelegacionDeQuienPidioLaSolicitudNoValeYElDelegadoSolicitanteTampoco() {
        sesion(20L, "COMPRADOR");
        // la consulta excluye al delegante que es el solicitante, así que no devuelve nada
        when(delegacionRepo.vigentesQueDanElRol(eq(20L), eq("APROBADOR"), any(), eq(10L))).thenReturn(List.of());
        assertThrows(AccesoDenegadoException.class,
                () -> service.decidir(1L, new DecisionRequest(DecisionAprobacion.APROBADA, null)));

        // el delegado no puede ser el propio solicitante aunque tenga una delegación
        sesion(10L, "COMPRADOR");
        when(delegacionRepo.vigentesQueDanElRol(eq(10L), eq("APROBADOR"), any(), any())).thenReturn(List.of(5L));
        assertThrows(AccesoDenegadoException.class,
                () -> service.decidir(1L, new DecisionRequest(DecisionAprobacion.APROBADA, null)));
        verify(aprobaciones, never()).save(any());
    }

    @Test
    void laBandejaIncluyeLoQueSeRecibePorDelegacion() {
        sesion(20L, "COMPRADOR");
        when(delegacionRepo.rolesDelegados(eq(20L), any())).thenReturn(Set.of("APROBADOR"));
        when(solicitudes.pendientesPara(any(), eq(20L), any())).thenReturn(org.springframework.data.domain.Page.empty());

        service.pendientes(org.springframework.data.domain.PageRequest.of(0, 10));

        ArgumentCaptor<java.util.Collection<String>> roles = ArgumentCaptor.forClass(java.util.Collection.class);
        verify(solicitudes).pendientesPara(roles.capture(), eq(20L), any());
        assertEquals(Set.of("COMPRADOR", "APROBADOR"), Set.copyOf(roles.getValue()));
    }

    @Test
    void laQuePidioQuienApruebaConEseRolLaDecideElDuenoYNoElRolDeLaPolitica() {
        s.setApruebaDueno(true); // la pidió el jefe de compras y la política la aprueba su propio rol
        // el rol de la política ya no alcanza
        assertThrows(AccesoDenegadoException.class,
                () -> service.decidir(1L, new DecisionRequest(DecisionAprobacion.APROBADA, null)));

        sesion(30L, "ADMIN_SISTEMA");
        var r = service.decidir(1L, new DecisionRequest(DecisionAprobacion.APROBADA, null));

        assertEquals(EstadoSolicitud.APROBADA, r.estado());
        assertEquals(true, r.apruebaDueno());
    }

    @Test
    void elDuenoNoDecideLasQueApruebaElRolDeLaPolitica() {
        sesion(30L, "ADMIN_SISTEMA");
        assertThrows(AccesoDenegadoException.class,
                () -> service.decidir(1L, new DecisionRequest(DecisionAprobacion.APROBADA, null)));
    }

    @Test
    void soloSeDecideLoPendiente() {
        s.setEstado(EstadoSolicitud.APROBADA);
        assertEquals("TRANSICION_INVALIDA", assertThrows(NegocioException.class,
                () -> service.decidir(1L, new DecisionRequest(DecisionAprobacion.RECHAZADA, "No"))).getCodigo());
    }

    @Test
    void reglasDeLosEnums() {
        assertEquals(EstadoSolicitud.RECHAZADA, DecisionAprobacion.RECHAZADA.estadoResultante());
        assertEquals(false, DecisionAprobacion.APROBADA.exigeObservaciones());
        assertEquals(true, EstadoSolicitud.APROBADA.esCancelable());
        assertEquals(false, EstadoSolicitud.EN_RFQ.esCancelable());
        assertEquals(false, EstadoSolicitud.PENDIENTE_APROBACION.esEditable());
    }

    private void sesion(Long id, String rol) {
        request.setAttribute(SesionActual.ATRIBUTO, new UsuarioAutenticado(id, "u@x.com", "Usuario", Set.of(rol), null, 1L));
    }
}
