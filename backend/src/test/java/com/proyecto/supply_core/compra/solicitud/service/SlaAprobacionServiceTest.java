package com.proyecto.supply_core.compra.solicitud.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.proyecto.supply_core.auditoria.enums.AccionAuditoria;
import com.proyecto.supply_core.auditoria.enums.EntidadAuditada;
import com.proyecto.supply_core.auditoria.service.AuditoriaService;
import com.proyecto.supply_core.common.tenant.EmpresaContexto;
import com.proyecto.supply_core.compra.solicitud.entity.SolicitudCompra;
import com.proyecto.supply_core.compra.solicitud.enums.EstadoSolicitud;
import com.proyecto.supply_core.compra.solicitud.repository.SolicitudCompraRepository;
import com.proyecto.supply_core.delegacion.service.DelegacionService;
import com.proyecto.supply_core.empresa.entity.Empresa;
import com.proyecto.supply_core.empresa.entity.PoliticaAprobacion;
import com.proyecto.supply_core.empresa.enums.TipoPolitica;
import com.proyecto.supply_core.empresa.repository.EmpresaRepository;
import com.proyecto.supply_core.empresa.repository.PoliticaAprobacionRepository;
import com.proyecto.supply_core.empresa.service.PoliticaAprobacionService;
import com.proyecto.supply_core.notificacion.enums.EventoNotificacion;
import com.proyecto.supply_core.notificacion.service.NotificacionService;
import com.proyecto.supply_core.usuario.entity.Rol;

class SlaAprobacionServiceTest {

    private final SolicitudCompraRepository solicitudes = mock(SolicitudCompraRepository.class);
    private final EmpresaRepository empresas = mock(EmpresaRepository.class);
    private final PoliticaAprobacionService politicaService = mock(PoliticaAprobacionService.class);
    private final PoliticaAprobacionRepository politicaRepo = mock(PoliticaAprobacionRepository.class);
    private final DelegacionService delegaciones = mock(DelegacionService.class);
    private final NotificacionService notificaciones = mock(NotificacionService.class);
    private final AuditoriaService auditoria = mock(AuditoriaService.class);
    private final SlaAprobacionService service = new SlaAprobacionService(solicitudes, empresas, politicaService,
            politicaRepo, delegaciones, notificaciones, auditoria);

    private final LocalDateTime ahora = LocalDateTime.of(2026, 11, 10, 12, 0);
    private Empresa empresa;
    private SolicitudCompra s;
    private PoliticaAprobacion nivelBajo;
    private PoliticaAprobacion nivelAlto;

    @BeforeEach
    void setUp() {
        EmpresaContexto.fijar(1L);
        empresa = new Empresa();
        empresa.setSlaAprobacionHoras(10); // 80 % = 8 h
        when(empresas.findById(1L)).thenReturn(Optional.of(empresa));
        nivelBajo = politica(7L, "APROBADOR");
        nivelAlto = politica(8L, "ADMIN_COMPRAS");
        when(politicaRepo.findWithRolById(7L)).thenReturn(Optional.of(nivelBajo));
        when(politicaRepo.findWithRolById(8L)).thenReturn(Optional.of(nivelAlto));
        when(politicaService.deMayorTramo(TipoPolitica.SOLICITUD)).thenReturn(Optional.of(nivelAlto));
        when(delegaciones.delegadosDelRol(anyString())).thenReturn(Set.of());
        s = new SolicitudCompra();
        s.setId(1L);
        s.setConsecutivo("SOL-000001");
        s.setUsuarioSolicitanteId(10L);
        s.setPoliticaAprobacionId(7L);
        s.setEstado(EstadoSolicitud.PENDIENTE_APROBACION);
        when(solicitudes.bloquear(1L)).thenReturn(Optional.of(s));
        when(solicitudes.idsPendientesDesde(any())).thenReturn(List.of(1L));
    }

    @AfterEach
    void limpiar() {
        EmpresaContexto.limpiar();
    }

    @Test
    void antesDelOchentaPorCientoNoHaceNada() {
        s.setEnviadoEn(ahora.minusHours(7));

        var r = service.procesar(ahora);

        assertEquals(0, r.avisadas());
        assertEquals(0, r.escaladas());
        assertNull(s.getSlaAvisoEn());
        verify(notificaciones, never()).notificarRol(anyString(), any(), anyString());
    }

    @Test
    void alOchentaPorCientoAvisaUnaSolaVezAlRolYASusDelegados() {
        s.setEnviadoEn(ahora.minusHours(9));
        when(delegaciones.delegadosDelRol("APROBADOR")).thenReturn(Set.of(55L));

        var r = service.procesar(ahora);

        assertEquals(1, r.avisadas());
        assertEquals(0, r.escaladas());
        assertEquals(ahora, s.getSlaAvisoEn());
        assertNull(s.getEscaladaEn());
        assertEquals(7L, s.getPoliticaAprobacionId());
        verify(notificaciones).notificarRol(eq("APROBADOR"), eq(EventoNotificacion.SOLICITUD_SLA_POR_VENCER), anyString());
        verify(notificaciones).notificar(eq(55L), eq(EventoNotificacion.SOLICITUD_SLA_POR_VENCER), anyString());

        // una segunda pasada no vuelve a avisar
        var otra = service.procesar(ahora.plusMinutes(5));
        assertEquals(0, otra.avisadas());
        verify(notificaciones).notificarRol(eq("APROBADOR"), eq(EventoNotificacion.SOLICITUD_SLA_POR_VENCER), anyString());
    }

    @Test
    void alVencerEscalaALaPoliticaDeMayorTramoAvisaYAudita() {
        s.setEnviadoEn(ahora.minusHours(11));
        s.setSlaAvisoEn(ahora.minusHours(3));
        when(delegaciones.delegadosDelRol("ADMIN_COMPRAS")).thenReturn(Set.of(66L));

        var r = service.procesar(ahora);

        assertEquals(0, r.avisadas());
        assertEquals(1, r.escaladas());
        assertEquals(8L, s.getPoliticaAprobacionId());
        assertEquals(ahora, s.getEscaladaEn());
        assertEquals(EstadoSolicitud.PENDIENTE_APROBACION, s.getEstado());
        verify(notificaciones).notificarRol(eq("ADMIN_COMPRAS"), eq(EventoNotificacion.SOLICITUD_ESCALADA), anyString());
        verify(notificaciones).notificar(eq(66L), eq(EventoNotificacion.SOLICITUD_ESCALADA), anyString());
        verify(notificaciones).notificar(eq(10L), eq(EventoNotificacion.SOLICITUD_ESCALADA), anyString());
        verify(auditoria).exito(eq(AccionAuditoria.SOLICITUD_ESCALAR), eq(EntidadAuditada.SOLICITUD_COMPRA), eq(1L),
                anyString());
    }

    @Test
    void siNuncaSeAvisoYaVencidaEscalaDirectoYMarcaElAviso() {
        s.setEnviadoEn(ahora.minusHours(30));

        var r = service.procesar(ahora);

        assertEquals(1, r.escaladas());
        assertNotNull(s.getSlaAvisoEn());
    }

    @Test
    void siYaEstabaEnElNivelMasAltoSoloAvisaYTambienACompras() {
        s.setPoliticaAprobacionId(8L);
        s.setEnviadoEn(ahora.minusHours(11));

        var r = service.procesar(ahora);

        assertEquals(1, r.escaladas());
        assertEquals(8L, s.getPoliticaAprobacionId());
        verify(notificaciones).notificarRol(eq("ADMIN_COMPRAS"), eq(EventoNotificacion.SOLICITUD_ESCALADA), anyString());
    }

    @Test
    void siYaEstabaEnElNivelMasAltoDeOtroRolAvisaAlRolYACompras() {
        PoliticaAprobacion unica = politica(9L, "ADMINISTRATIVO");
        s.setPoliticaAprobacionId(9L);
        s.setEnviadoEn(ahora.minusHours(11));
        when(politicaRepo.findWithRolById(9L)).thenReturn(Optional.of(unica));
        when(politicaService.deMayorTramo(TipoPolitica.SOLICITUD)).thenReturn(Optional.of(unica));

        service.procesar(ahora);

        verify(notificaciones).notificarRol(eq("ADMINISTRATIVO"), eq(EventoNotificacion.SOLICITUD_ESCALADA), anyString());
        verify(notificaciones).notificarRol(eq("ADMIN_COMPRAS"), eq(EventoNotificacion.SOLICITUD_ESCALADA), anyString());
        assertEquals(9L, s.getPoliticaAprobacionId());
    }

    @Test
    void sinSlaOLoYaDecididoOEscaladoNoSeTocaNada() {
        empresa.setSlaAprobacionHoras(0);
        s.setEnviadoEn(ahora.minusDays(10));
        assertEquals(0, service.procesar(ahora).escaladas());
        verify(solicitudes, never()).idsPendientesDesde(any());

        empresa.setSlaAprobacionHoras(10);
        s.setEstado(EstadoSolicitud.APROBADA); // se decidió mientras tanto
        assertEquals(0, service.procesar(ahora).escaladas());

        s.setEstado(EstadoSolicitud.PENDIENTE_APROBACION);
        s.setEscaladaEn(ahora.minusHours(1)); // ya se escaló en este envío
        assertEquals(0, service.procesar(ahora).escaladas());
        verify(auditoria, never()).exito(eq(AccionAuditoria.SOLICITUD_ESCALAR), any(), any(), anyString());
    }

    private static PoliticaAprobacion politica(Long id, String rol) {
        Rol r = new Rol();
        r.setCodigo(rol);
        PoliticaAprobacion p = new PoliticaAprobacion();
        p.setId(id);
        p.setRolAprobador(r);
        return p;
    }
}
