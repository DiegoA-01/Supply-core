package com.proyecto.supply_core.compra.solicitud.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.proyecto.supply_core.auditoria.enums.AccionAuditoria;
import com.proyecto.supply_core.auditoria.enums.EntidadAuditada;
import com.proyecto.supply_core.auditoria.service.AuditoriaService;
import com.proyecto.supply_core.catalogo.entity.UnidadMedida;
import com.proyecto.supply_core.catalogo.repository.UnidadMedidaRepository;
import com.proyecto.supply_core.catalogo.service.UnidadMedidaService;
import com.proyecto.supply_core.common.exception.AccesoDenegadoException;
import com.proyecto.supply_core.common.exception.NegocioException;
import com.proyecto.supply_core.common.exception.NoEncontradoException;
import com.proyecto.supply_core.common.util.ConsecutivoGenerator;
import com.proyecto.supply_core.compra.solicitud.dto.LineaSolicitudRequest;
import com.proyecto.supply_core.compra.solicitud.dto.SolicitudRequest;
import com.proyecto.supply_core.compra.solicitud.entity.SolicitudCompra;
import com.proyecto.supply_core.compra.solicitud.entity.SolicitudCompraLinea;
import com.proyecto.supply_core.compra.solicitud.enums.EstadoSolicitud;
import com.proyecto.supply_core.compra.solicitud.repository.AprobacionSolicitudRepository;
import com.proyecto.supply_core.compra.solicitud.repository.SolicitudCompraRepository;
import com.proyecto.supply_core.empresa.entity.PoliticaAprobacion;
import com.proyecto.supply_core.empresa.enums.TipoPolitica;
import com.proyecto.supply_core.empresa.repository.CentroCostoRepository;
import com.proyecto.supply_core.empresa.service.PoliticaAprobacionService;
import com.proyecto.supply_core.inventario.entity.Producto;
import com.proyecto.supply_core.inventario.repository.ProductoRepository;
import com.proyecto.supply_core.notificacion.enums.EventoNotificacion;
import com.proyecto.supply_core.notificacion.service.NotificacionService;
import com.proyecto.supply_core.presupuesto.dto.EvaluacionPresupuesto;
import com.proyecto.supply_core.presupuesto.service.PresupuestoService;
import com.proyecto.supply_core.security.context.SesionActual;
import com.proyecto.supply_core.security.context.UsuarioAutenticado;
import com.proyecto.supply_core.usuario.entity.Rol;
import com.proyecto.supply_core.usuario.entity.Usuario;
import com.proyecto.supply_core.usuario.repository.UsuarioRepository;

class SolicitudServiceTest {

    private final SolicitudCompraRepository solicitudes = mock(SolicitudCompraRepository.class);
    private final AprobacionSolicitudRepository aprobaciones = mock(AprobacionSolicitudRepository.class);
    private final ProductoRepository productos = mock(ProductoRepository.class);
    private final UnidadMedidaRepository unidades = mock(UnidadMedidaRepository.class);
    private final UnidadMedidaService unidadService = mock(UnidadMedidaService.class);
    private final UsuarioRepository usuarios = mock(UsuarioRepository.class);
    private final CentroCostoRepository centros = mock(CentroCostoRepository.class);
    private final PoliticaAprobacionService politicas = mock(PoliticaAprobacionService.class);
    private final ConsecutivoGenerator consecutivos = mock(ConsecutivoGenerator.class);
    private final AuditoriaService auditoria = mock(AuditoriaService.class);
    private final NotificacionService notificaciones = mock(NotificacionService.class);
    private final PresupuestoService presupuestos = mock(PresupuestoService.class);
    private final SolicitudService service = new SolicitudService(solicitudes, aprobaciones, productos, unidades,
            unidadService, usuarios, centros, politicas, consecutivos, auditoria, notificaciones, presupuestos);

    private MockHttpServletRequest request;
    private UnidadMedida und;
    private UnidadMedida caja;
    private Producto lapiz;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest();
        sesion(10L, "SOLICITANTE");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        und = unidad(1L, "UND");
        caja = unidad(2L, "CAJA24");
        lapiz = new Producto();
        lapiz.setId(100L);
        lapiz.setCodigo("LAP-01");
        lapiz.setNombre("Lápiz");
        lapiz.setUnidadMedida(und);
        lapiz.setCostoReferencia(new BigDecimal("500"));
        when(productos.findWithRelacionesById(100L)).thenReturn(Optional.of(lapiz));
        // UND → UND = igual; CAJA24 → UND = x24
        when(unidadService.convertir(any(), eq(1L), eq(1L))).thenAnswer(i -> i.getArgument(0));
        when(unidadService.convertir(any(), eq(2L), eq(1L)))
                .thenAnswer(i -> i.<BigDecimal>getArgument(0).multiply(BigDecimal.valueOf(24)));

        Usuario yo = new Usuario();
        yo.setId(10L);
        yo.setCentroCostoId(5L);
        when(usuarios.findById(10L)).thenReturn(Optional.of(yo));
        when(centros.existsByIdAndActivoTrue(5L)).thenReturn(true);
        when(consecutivos.siguiente(ConsecutivoGenerator.SOLICITUD)).thenReturn("SOL-000001");
        when(presupuestos.evaluar(any(), any(), any(), any())).thenReturn(EvaluacionPresupuesto.SIN_PRESUPUESTO);
        when(solicitudes.save(any())).thenAnswer(i -> {
            SolicitudCompra s = i.getArgument(0);
            s.setId(1L);
            return s;
        });
    }

    @AfterEach
    void limpiar() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void crearQuedaEnBorradorConElCentroDeCostoDelSolicitante() {
        var r = service.crear(req(linea(100L, 1L, "10")));

        assertEquals(EstadoSolicitud.BORRADOR, r.estado());
        assertEquals("SOL-000001", r.consecutivo());
        assertEquals(5L, r.centroCostoId());
        assertEquals(10L, r.usuarioSolicitanteId());
        assertEquals("LAP-01", r.lineas().get(0).productoCodigo());
        assertNull(r.montoEstimado()); // se calcula al enviar
        verify(auditoria).exito(eq(AccionAuditoria.SOLICITUD_CREAR), eq(EntidadAuditada.SOLICITUD_COMPRA), eq(1L),
                anyString());
    }

    @Test
    void sinCentroDeCostoOInactivoNoSePuedeSolicitar() {
        when(centros.existsByIdAndActivoTrue(5L)).thenReturn(false);
        assertEquals("CENTRO_COSTO_INACTIVO",
                assertThrows(NegocioException.class, () -> service.crear(req(linea(100L, 1L, "1")))).getCodigo());

        when(usuarios.findById(10L)).thenReturn(Optional.of(new Usuario()));
        assertEquals("SIN_CENTRO_COSTO",
                assertThrows(NegocioException.class, () -> service.crear(req(linea(100L, 1L, "1")))).getCodigo());
        verify(solicitudes, never()).save(any());
    }

    @Test
    void unidadQueNoMideLoMismoSeRechaza() {
        UnidadMedida kg = unidad(3L, "KG");
        when(unidadService.convertir(any(), eq(3L), eq(1L)))
                .thenThrow(new NegocioException("UNIDADES_INCOMPATIBLES", "KG no se convierte a UND"));

        assertEquals("UNIDADES_INCOMPATIBLES", assertThrows(NegocioException.class,
                () -> service.crear(req(linea(100L, kg.getId(), "1")))).getCodigo());
    }

    @Test
    void enviarConPoliticaQuedaPendienteYAvisaAlRolAprobador() {
        SolicitudCompra s = existente(EstadoSolicitud.BORRADOR, 10L, linea(100L, 2L, "2")); // 2 cajas = 48 und
        PoliticaAprobacion p = politica(7L, "APROBADOR");
        when(politicas.aplicable(TipoPolitica.SOLICITUD, new BigDecimal("24000.00"))).thenReturn(Optional.of(p));

        var r = service.enviar(1L);

        assertEquals(EstadoSolicitud.PENDIENTE_APROBACION, r.estado());
        assertEquals(new BigDecimal("24000.00"), r.montoEstimado()); // 48 x 500
        assertFalse(r.montoIncompleto());
        assertEquals(7L, s.getPoliticaAprobacionId());
        verify(notificaciones).notificarRol(eq("APROBADOR"), eq(EventoNotificacion.SOLICITUD_PENDIENTE_APROBACION),
                anyString());
    }

    @Test
    void siQuienPideApruebaConElRolDeLaPoliticaLaDecideElDueno() {
        request.setAttribute(SesionActual.ATRIBUTO, new UsuarioAutenticado(10L, "jefe@x.com", "Jefe de compras",
                Set.of("SOLICITANTE", "ADMIN_COMPRAS"), null, 1L));
        SolicitudCompra s = existente(EstadoSolicitud.BORRADOR, 10L, linea(100L, 2L, "2"));
        when(politicas.aplicable(TipoPolitica.SOLICITUD, new BigDecimal("24000.00")))
                .thenReturn(Optional.of(politica(7L, "ADMIN_COMPRAS")));

        var r = service.enviar(1L);

        assertTrue(s.isApruebaDueno());
        assertTrue(r.apruebaDueno());
        verify(notificaciones).notificarRol(eq("ADMIN_SISTEMA"), eq(EventoNotificacion.SOLICITUD_PENDIENTE_APROBACION),
                anyString());
        verify(notificaciones, never()).notificarRol(eq("ADMIN_COMPRAS"), any(), anyString());
    }

    @Test
    void siQuienPideEsElDuenoLaDecideOtraPersonaConElRolDeLaPolitica() {
        // el dueño también tiene el rol aprobador: subirla al dueño la dejaría trancada (nadie aprueba lo suyo)
        request.setAttribute(SesionActual.ATRIBUTO, new UsuarioAutenticado(10L, "dueno@x.com", "Dueño",
                Set.of("SOLICITANTE", "APROBADOR", "ADMIN_SISTEMA"), null, 1L));
        SolicitudCompra s = existente(EstadoSolicitud.BORRADOR, 10L, linea(100L, 2L, "2"));
        when(politicas.aplicable(TipoPolitica.SOLICITUD, new BigDecimal("24000.00")))
                .thenReturn(Optional.of(politica(7L, "APROBADOR")));

        var r = service.enviar(1L);

        assertFalse(s.isApruebaDueno());
        assertFalse(r.apruebaDueno());
        verify(notificaciones).notificarRol(eq("APROBADOR"), eq(EventoNotificacion.SOLICITUD_PENDIENTE_APROBACION),
                anyString());
    }

    @Test
    void productoSinCostoExigeLaPoliticaDeMayorTramo() {
        lapiz.setCostoReferencia(null);
        existente(EstadoSolicitud.BORRADOR, 10L, linea(100L, 1L, "3"));
        when(politicas.deMayorTramo(TipoPolitica.SOLICITUD)).thenReturn(Optional.of(politica(9L, "ADMIN_COMPRAS")));

        var r = service.enviar(1L);

        assertTrue(r.montoIncompleto());
        assertEquals(new BigDecimal("0.00"), r.montoEstimado());
        assertEquals(9L, r.politicaAprobacionId());
        verify(politicas, never()).aplicable(any(), any());
    }

    @Test
    void sinPoliticaAplicableQuedaAprobadaYAvisaACompras() {
        existente(EstadoSolicitud.DEVUELTA, 10L, linea(100L, 1L, "1"));
        when(politicas.aplicable(any(), any())).thenReturn(Optional.empty());

        var r = service.enviar(1L);

        assertEquals(EstadoSolicitud.APROBADA, r.estado());
        assertNull(r.politicaAprobacionId());
        verify(notificaciones).notificarRol(eq("COMPRADOR"), eq(EventoNotificacion.SOLICITUD_APROBADA), anyString());
    }

    @Test
    void excederElPresupuestoSinJustificacionSeRechazaYNoCambiaLaSolicitud() {
        SolicitudCompra s = existente(EstadoSolicitud.BORRADOR, 10L, linea(100L, 1L, "10")); // 10 x 500 = 5.000
        when(presupuestos.evaluar(5L, s.getFechaRequerida(), new BigDecimal("5000.00"), 1L))
                .thenReturn(excedido());

        var ex = assertThrows(NegocioException.class, () -> service.enviar(1L, "   "));

        assertEquals("PRESUPUESTO_EXCEDIDO", ex.getCodigo());
        assertTrue(ex.getMessage().contains("$ 8000.00"));
        assertEquals(EstadoSolicitud.BORRADOR, s.getEstado());
        assertFalse(s.isExcedePresupuesto());
        verify(notificaciones, never()).notificarRol(anyString(), any(), anyString());
    }

    @Test
    void excederConJustificacionPasaPorLaPoliticaDeMayorTramoYQuedaAuditado() {
        SolicitudCompra s = existente(EstadoSolicitud.BORRADOR, 10L, linea(100L, 1L, "10"));
        when(presupuestos.evaluar(any(), any(), any(), any())).thenReturn(excedido());
        when(politicas.deMayorTramo(TipoPolitica.SOLICITUD)).thenReturn(Optional.of(politica(9L, "ADMIN_COMPRAS")));

        var r = service.enviar(1L, "  Obra urgente del cliente  ");

        assertEquals(EstadoSolicitud.PENDIENTE_APROBACION, r.estado());
        assertEquals(9L, r.politicaAprobacionId());
        assertTrue(r.excedePresupuesto());
        assertEquals("Obra urgente del cliente", s.getJustificacionExcepcion());
        verify(politicas, never()).aplicable(any(), any());
        verify(auditoria).exito(eq(AccionAuditoria.SOLICITUD_EXCEPCION), eq(EntidadAuditada.SOLICITUD_COMPRA), eq(1L),
                anyString());
    }

    @Test
    void excederSinPoliticaQueLaAutoriceNoSePuedeEnviar() {
        existente(EstadoSolicitud.BORRADOR, 10L, linea(100L, 1L, "10"));
        when(presupuestos.evaluar(any(), any(), any(), any())).thenReturn(excedido());
        when(politicas.deMayorTramo(TipoPolitica.SOLICITUD)).thenReturn(Optional.empty());

        var ex = assertThrows(NegocioException.class, () -> service.enviar(1L, "Urgente"));

        assertEquals("PRESUPUESTO_EXCEDIDO", ex.getCodigo());
        assertTrue(ex.getMessage().contains("política de aprobación"));
    }

    @Test
    void unaSolicitudDuplicadaSinJustificacionSeRechaza() {
        SolicitudCompra s = existente(EstadoSolicitud.BORRADOR, 10L, linea(100L, 1L, "1"));
        when(solicitudes.consecutivosConProductos(eq(5L), any(), any(), eq(1L))).thenReturn(List.of("SOL-000007"));

        var ex = assertThrows(NegocioException.class, () -> service.enviar(1L));

        assertEquals("SOLICITUD_DUPLICADA", ex.getCodigo());
        assertTrue(ex.getMessage().contains("SOL-000007"));
        assertEquals(EstadoSolicitud.BORRADOR, s.getEstado());
    }

    @Test
    void unaDuplicadaConJustificacionSeEnviaPorLaPoliticaNormalYQuedaAuditada() {
        SolicitudCompra s = existente(EstadoSolicitud.BORRADOR, 10L, linea(100L, 1L, "1"));
        when(solicitudes.consecutivosConProductos(eq(5L), any(), any(), eq(1L))).thenReturn(List.of("SOL-000007"));
        when(politicas.aplicable(any(), any())).thenReturn(Optional.empty());

        var r = service.enviar(1L, "Es para otra sede");

        assertEquals(EstadoSolicitud.APROBADA, r.estado());
        assertFalse(r.excedePresupuesto());
        assertEquals("Es para otra sede", s.getJustificacionExcepcion());
        verify(auditoria).exito(eq(AccionAuditoria.SOLICITUD_EXCEPCION), eq(EntidadAuditada.SOLICITUD_COMPRA), eq(1L),
                anyString());
    }

    @Test
    void unaJustificacionQueNoHaceFaltaNoSeGuardaNiSeAudita() {
        SolicitudCompra s = existente(EstadoSolicitud.BORRADOR, 10L, linea(100L, 1L, "1"));
        when(politicas.aplicable(any(), any())).thenReturn(Optional.empty());

        service.enviar(1L, "Por si acaso");

        assertNull(s.getJustificacionExcepcion());
        verify(auditoria, never()).exito(eq(AccionAuditoria.SOLICITUD_EXCEPCION), any(), any(), anyString());
    }

    @Test
    void soloElSolicitanteEditaYSoloEnBorradorODevuelta() {
        existente(EstadoSolicitud.BORRADOR, 99L, linea(100L, 1L, "1"));
        assertThrows(AccesoDenegadoException.class, () -> service.enviar(1L));

        existente(EstadoSolicitud.PENDIENTE_APROBACION, 10L, linea(100L, 1L, "1"));
        assertEquals("SOLICITUD_NO_EDITABLE", assertThrows(NegocioException.class,
                () -> service.actualizar(1L, req(linea(100L, 1L, "2")))).getCodigo());
    }

    @Test
    void cancelarPorDuenoOComprasYSoloAntesDeRfq() {
        existente(EstadoSolicitud.APROBADA, 99L, linea(100L, 1L, "1"));
        assertThrows(AccesoDenegadoException.class, () -> service.cancelar(1L));

        sesion(20L, "ADMIN_COMPRAS");
        assertEquals(EstadoSolicitud.CANCELADA, service.cancelar(1L).estado());

        existente(EstadoSolicitud.EN_RFQ, 99L, linea(100L, 1L, "1"));
        assertEquals("TRANSICION_INVALIDA",
                assertThrows(NegocioException.class, () -> service.cancelar(1L)).getCodigo());
    }

    @Test
    void elSolicitanteSoloVeLasSuyas() {
        existente(EstadoSolicitud.BORRADOR, 99L, linea(100L, 1L, "1"));
        assertThrows(NoEncontradoException.class, () -> service.obtener(1L));

        when(solicitudes.buscar(any(), any(), any(), any(), any())).thenReturn(Page.empty());
        service.buscar(null, null, 99L, null, PageRequest.of(0, 20));
        ArgumentCaptor<Long> solicitante = ArgumentCaptor.forClass(Long.class);
        verify(solicitudes).buscar(any(), any(), solicitante.capture(), any(), any());
        assertEquals(10L, solicitante.getValue()); // se ignora el 99 pedido

        sesion(30L, "AUDITOR");
        assertEquals(1L, service.obtener(1L).id());
    }

    // ----------------------------------------------------------------- apoyo

    private SolicitudCompra existente(EstadoSolicitud estado, Long solicitanteId, LineaSolicitudRequest l) {
        SolicitudCompra s = new SolicitudCompra();
        s.setId(1L);
        s.setConsecutivo("SOL-000001");
        s.setCentroCostoId(5L);
        s.setUsuarioSolicitanteId(solicitanteId);
        s.setEstado(estado);
        s.setFechaRequerida(LocalDate.now());
        var linea = new SolicitudCompraLinea();
        linea.setProducto(lapiz);
        linea.setUnidadMedida(l.unidadMedidaId() == 2L ? caja : und);
        linea.setCantidad(l.cantidad());
        s.agregarLinea(linea);
        when(solicitudes.bloquear(1L)).thenReturn(Optional.of(s));
        when(solicitudes.findWithLineasById(1L)).thenReturn(Optional.of(s));
        when(aprobaciones.findBySolicitudCompraIdOrderByDecididoEnAscIdAsc(1L)).thenReturn(List.of());
        return s;
    }

    /** Presupuesto de $ 10.000 con $ 8.000 comprometidos: una solicitud de $ 5.000 lo excede. */
    private static EvaluacionPresupuesto excedido() {
        return new EvaluacionPresupuesto(true, new BigDecimal("10000.00"), new BigDecimal("8000.00"),
                new BigDecimal("2000.00"), true);
    }

    private UnidadMedida unidad(Long id, String codigo) {
        UnidadMedida u = new UnidadMedida();
        u.setId(id);
        u.setCodigo(codigo);
        when(unidades.findById(id)).thenReturn(Optional.of(u));
        return u;
    }

    private static PoliticaAprobacion politica(Long id, String rol) {
        Rol r = new Rol();
        r.setCodigo(rol);
        PoliticaAprobacion p = new PoliticaAprobacion();
        p.setId(id);
        p.setRolAprobador(r);
        return p;
    }

    private static LineaSolicitudRequest linea(Long productoId, Long unidadId, String cantidad) {
        return new LineaSolicitudRequest(productoId, unidadId, new BigDecimal(cantidad));
    }

    private static SolicitudRequest req(LineaSolicitudRequest... lineas) {
        return new SolicitudRequest("Reposición de papelería", LocalDate.now().plusDays(5), List.of(lineas));
    }

    private void sesion(Long id, String rol) {
        request.setAttribute(SesionActual.ATRIBUTO, new UsuarioAutenticado(id, "u@x.com", "Usuario", Set.of(rol), null, 1L));
    }
}
