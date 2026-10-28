package com.proyecto.supply_core.compra.adjudicacion.service;

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

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.proyecto.supply_core.auditoria.service.AuditoriaService;
import com.proyecto.supply_core.common.exception.AccesoDenegadoException;
import com.proyecto.supply_core.common.exception.NegocioException;
import com.proyecto.supply_core.common.exception.ParametroInvalidoException;
import com.proyecto.supply_core.compra.adjudicacion.dto.AdjudicarRequest;
import com.proyecto.supply_core.compra.adjudicacion.dto.DecisionAdjudicacionRequest;
import com.proyecto.supply_core.compra.adjudicacion.entity.Adjudicacion;
import com.proyecto.supply_core.compra.adjudicacion.enums.EstadoAdjudicacion;
import com.proyecto.supply_core.compra.adjudicacion.repository.AdjudicacionRepository;
import com.proyecto.supply_core.compra.analisis.entity.Analisis;
import com.proyecto.supply_core.compra.analisis.enums.EstadoAnalisis;
import com.proyecto.supply_core.compra.analisis.repository.AnalisisRepository;
import com.proyecto.supply_core.compra.cotizacion.entity.Cotizacion;
import com.proyecto.supply_core.compra.cotizacion.enums.EstadoCotizacion;
import com.proyecto.supply_core.compra.cotizacion.repository.CotizacionRepository;
import com.proyecto.supply_core.compra.rfq.entity.Rfq;
import com.proyecto.supply_core.compra.rfq.enums.EstadoRfq;
import com.proyecto.supply_core.compra.rfq.repository.RfqRepository;
import com.proyecto.supply_core.compra.solicitud.entity.SolicitudCompra;
import com.proyecto.supply_core.compra.solicitud.enums.EstadoSolicitud;
import com.proyecto.supply_core.compra.solicitud.repository.SolicitudCompraRepository;
import com.proyecto.supply_core.empresa.entity.PoliticaAprobacion;
import com.proyecto.supply_core.empresa.enums.TipoPolitica;
import com.proyecto.supply_core.delegacion.repository.DelegacionRepository;
import com.proyecto.supply_core.delegacion.service.DelegacionService;
import com.proyecto.supply_core.empresa.repository.PoliticaAprobacionRepository;
import com.proyecto.supply_core.empresa.service.PoliticaAprobacionService;
import com.proyecto.supply_core.notificacion.enums.EventoNotificacion;
import com.proyecto.supply_core.notificacion.service.NotificacionService;
import com.proyecto.supply_core.proveedor.entity.Proveedor;
import com.proyecto.supply_core.security.context.SesionActual;
import com.proyecto.supply_core.security.context.UsuarioAutenticado;
import com.proyecto.supply_core.usuario.entity.Rol;
import com.proyecto.supply_core.usuario.entity.Usuario;
import com.proyecto.supply_core.usuario.repository.UsuarioRepository;

class AdjudicacionServiceTest {

    private final AdjudicacionRepository adjudicaciones = mock(AdjudicacionRepository.class);
    private final AnalisisRepository analisisRepo = mock(AnalisisRepository.class);
    private final RfqRepository rfqs = mock(RfqRepository.class);
    private final SolicitudCompraRepository solicitudes = mock(SolicitudCompraRepository.class);
    private final CotizacionRepository cotizaciones = mock(CotizacionRepository.class);
    private final PoliticaAprobacionService politicas = mock(PoliticaAprobacionService.class);
    private final PoliticaAprobacionRepository politicaRepo = mock(PoliticaAprobacionRepository.class);
    private final UsuarioRepository usuarios = mock(UsuarioRepository.class);
    private final NotificacionService notificaciones = mock(NotificacionService.class);
    private final AdjudicacionService service = new AdjudicacionService(adjudicaciones, analisisRepo, rfqs, solicitudes,
            cotizaciones, politicas, politicaRepo, usuarios, mock(AuditoriaService.class), notificaciones,
            new DelegacionService(mock(DelegacionRepository.class), usuarios, politicaRepo,
                    mock(AuditoriaService.class), notificaciones));

    private MockHttpServletRequest request;
    private Rfq rfq;
    private SolicitudCompra solicitud;
    private Cotizacion primera;
    private Cotizacion segunda;
    private Adjudicacion guardada;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest();
        sesion(30L, "COMPRADOR");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        solicitud = new SolicitudCompra();
        solicitud.setId(5L);
        solicitud.setEstado(EstadoSolicitud.EN_RFQ);
        rfq = new Rfq();
        rfq.setId(9L);
        rfq.setConsecutivo("RFQ-000009");
        rfq.setEstado(EstadoRfq.CERRADA);
        rfq.setSolicitud(solicitud);
        primera = cotizacion(1L, "25704.00");
        segunda = cotizacion(2L, "29702.40");
        Analisis a = new Analisis();
        a.setId(70L);
        a.getResultados().add(resultado(primera, 1));
        a.getResultados().add(resultado(segunda, 2));

        when(rfqs.bloquear(9L)).thenReturn(Optional.of(rfq));
        when(rfqs.findWithDetalleById(9L)).thenReturn(Optional.of(rfq));
        when(analisisRepo.findFirstByRfq_IdAndEstado(9L, EstadoAnalisis.PUBLICADO)).thenReturn(Optional.of(a));
        when(solicitudes.bloquear(5L)).thenReturn(Optional.of(solicitud));
        when(cotizaciones.findByRfq_IdAndEstadoNotOrderByEnviadoEnAsc(9L, EstadoCotizacion.BORRADOR))
                .thenReturn(List.of(primera, segunda));
        Usuario portal = new Usuario();
        portal.setId(77L);
        when(usuarios.findByProveedorId(41L)).thenReturn(List.of(portal));
        when(adjudicaciones.save(any())).thenAnswer(i -> {
            guardada = i.getArgument(0);
            guardada.setId(50L);
            when(adjudicaciones.bloquear(50L)).thenReturn(Optional.of(guardada));
            when(adjudicaciones.findDetalleById(50L)).thenReturn(Optional.of(guardada));
            return guardada;
        });
    }

    @AfterEach
    void limpiar() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void sinPoliticaNaceAprobadaYAplicaEfectos() {
        var r = service.adjudicar(9L, new AdjudicarRequest(1L, null));

        assertEquals(EstadoAdjudicacion.APROBADA, r.estado());
        assertTrue(r.sigueRecomendacion());
        assertEquals(new BigDecimal("25704.00"), r.montoTotal());
        assertEquals(EstadoCotizacion.SELECCIONADA, primera.getEstado());
        assertEquals(EstadoCotizacion.NO_SELECCIONADA, segunda.getEstado());
        assertEquals(EstadoRfq.ADJUDICADA, rfq.getEstado());
        assertEquals(EstadoSolicitud.ADJUDICADA, solicitud.getEstado());
        verify(notificaciones).notificar(eq(77L), eq(EventoNotificacion.COTIZACION_SELECCIONADA), anyString());
    }

    @Test
    void noSeguirLaRecomendacionExigeJustificacion() {
        assertThrows(ParametroInvalidoException.class, () -> service.adjudicar(9L, new AdjudicarRequest(2L, "corta")));

        var r = service.adjudicar(9L, new AdjudicarRequest(2L, "Entrega en 1 día; la obra no puede esperar"));
        assertFalse(r.sigueRecomendacion());
        assertEquals(2L, r.cotizacionId());
    }

    @Test
    void conPoliticaQuedaPendienteYSoloDecideElRolSinSegregacion() {
        Rol rol = new Rol();
        rol.setCodigo("APROBADOR");
        PoliticaAprobacion p = new PoliticaAprobacion();
        p.setId(8L);
        p.setRolAprobador(rol);
        when(politicas.aplicable(eq(TipoPolitica.ADJUDICACION), any())).thenReturn(Optional.of(p));
        when(politicaRepo.findWithRolById(8L)).thenReturn(Optional.of(p));

        var r = service.adjudicar(9L, new AdjudicarRequest(1L, null));
        assertEquals(EstadoAdjudicacion.PENDIENTE_APROBACION, r.estado());
        assertEquals(EstadoRfq.CERRADA, rfq.getEstado()); // aún no se aplica
        verify(notificaciones).notificarRol(eq("APROBADOR"), eq(EventoNotificacion.ADJUDICACION_PENDIENTE_APROBACION),
                anyString());

        sesion(30L, "APROBADOR"); // el mismo que adjudicó
        assertThrows(AccesoDenegadoException.class, () -> service.decidir(50L, new DecisionAdjudicacionRequest(true, null)));
        sesion(31L, "COMPRADOR"); // rol equivocado
        assertThrows(AccesoDenegadoException.class, () -> service.decidir(50L, new DecisionAdjudicacionRequest(true, null)));

        sesion(31L, "APROBADOR");
        assertEquals(EstadoAdjudicacion.APROBADA, service.decidir(50L, new DecisionAdjudicacionRequest(true, null)).estado());
        assertEquals(EstadoRfq.ADJUDICADA, rfq.getEstado());
        assertEquals(31L, guardada.getAprobadoPorUsuarioId());
        assertEquals("TRANSICION_INVALIDA", assertThrows(NegocioException.class,
                () -> service.decidir(50L, new DecisionAdjudicacionRequest(true, null))).getCodigo());
    }

    @Test
    void rechazarDejaLaRfqCerradaParaAdjudicarDeNuevo() {
        Rol rol = new Rol();
        rol.setCodigo("APROBADOR");
        PoliticaAprobacion p = new PoliticaAprobacion();
        p.setId(8L);
        p.setRolAprobador(rol);
        when(politicas.aplicable(eq(TipoPolitica.ADJUDICACION), any())).thenReturn(Optional.of(p));
        when(politicaRepo.findWithRolById(8L)).thenReturn(Optional.of(p));
        service.adjudicar(9L, new AdjudicarRequest(1L, null));

        sesion(31L, "APROBADOR");
        var r = service.decidir(50L, new DecisionAdjudicacionRequest(false, "Pedir mejor precio"));

        assertEquals(EstadoAdjudicacion.RECHAZADA, r.estado());
        assertEquals(EstadoRfq.CERRADA, rfq.getEstado());
        assertEquals(EstadoCotizacion.EN_ANALISIS, primera.getEstado());
        verify(notificaciones).notificar(eq(30L), eq(EventoNotificacion.ADJUDICACION_DECIDIDA), anyString());
    }

    @Test
    void requisitosPrevios() {
        when(adjudicaciones.existsByRfq_IdAndEstadoIn(eq(9L), any())).thenReturn(true);
        assertEquals("ADJUDICACION_EXISTENTE", assertThrows(NegocioException.class,
                () -> service.adjudicar(9L, new AdjudicarRequest(1L, null))).getCodigo());

        when(adjudicaciones.existsByRfq_IdAndEstadoIn(eq(9L), any())).thenReturn(false);
        assertEquals("COTIZACION_FUERA_DEL_ANALISIS", assertThrows(NegocioException.class,
                () -> service.adjudicar(9L, new AdjudicarRequest(99L, null))).getCodigo());

        when(analisisRepo.findFirstByRfq_IdAndEstado(9L, EstadoAnalisis.PUBLICADO)).thenReturn(Optional.empty());
        assertEquals("SIN_ANALISIS_PUBLICADO", assertThrows(NegocioException.class,
                () -> service.adjudicar(9L, new AdjudicarRequest(1L, null))).getCodigo());
        verify(adjudicaciones, never()).save(any());
    }

    private Cotizacion cotizacion(Long id, String total) {
        Proveedor p = new Proveedor();
        p.setId(40L + id);
        p.setRazonSocial("Proveedor " + id);
        Cotizacion c = new Cotizacion();
        c.setId(id);
        c.setRfq(rfq);
        c.setProveedor(p);
        c.setTotal(new BigDecimal(total));
        c.setEstado(EstadoCotizacion.EN_ANALISIS);
        return c;
    }

    private static Analisis.Resultado resultado(Cotizacion c, int posicion) {
        Analisis.Resultado r = new Analisis.Resultado();
        r.setCotizacion(c);
        r.setPosicionRanking((short) posicion);
        return r;
    }

    private void sesion(Long id, String rol) {
        request.setAttribute(SesionActual.ATRIBUTO, new UsuarioAutenticado(id, "u@x.com", "U", Set.of(rol), null, 1L));
    }
}
