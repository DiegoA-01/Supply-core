package com.proyecto.supply_core.compra.orden.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.proyecto.supply_core.auditoria.enums.AccionAuditoria;
import com.proyecto.supply_core.auditoria.enums.EntidadAuditada;
import com.proyecto.supply_core.auditoria.service.AuditoriaService;
import com.proyecto.supply_core.catalogo.entity.UnidadMedida;
import com.proyecto.supply_core.catalogo.service.UnidadMedidaService;
import com.proyecto.supply_core.common.exception.AccesoDenegadoException;
import com.proyecto.supply_core.common.exception.NegocioException;
import com.proyecto.supply_core.common.exception.NoEncontradoException;
import com.proyecto.supply_core.common.util.ConsecutivoGenerator;
import com.proyecto.supply_core.compra.orden.dto.EmitirDirectaRequest;
import com.proyecto.supply_core.compra.orden.dto.OrdenResponse;
import com.proyecto.supply_core.compra.orden.entity.OrdenCompra;
import com.proyecto.supply_core.compra.orden.enums.EstadoOrden;
import com.proyecto.supply_core.compra.orden.repository.OrdenCompraRepository;
import com.proyecto.supply_core.compra.solicitud.entity.SolicitudCompra;
import com.proyecto.supply_core.compra.solicitud.entity.SolicitudCompraLinea;
import com.proyecto.supply_core.compra.solicitud.enums.EstadoSolicitud;
import com.proyecto.supply_core.compra.solicitud.repository.SolicitudCompraRepository;
import com.proyecto.supply_core.contrato.dto.ConsumoPrecio;
import com.proyecto.supply_core.contrato.entity.Contrato;
import com.proyecto.supply_core.contrato.entity.ContratoPrecio;
import com.proyecto.supply_core.contrato.enums.EstadoContrato;
import com.proyecto.supply_core.contrato.repository.ContratoRepository;
import com.proyecto.supply_core.contrato.service.ContratoService;
import com.proyecto.supply_core.inventario.entity.Bodega;
import com.proyecto.supply_core.inventario.entity.Producto;
import com.proyecto.supply_core.inventario.repository.BodegaRepository;
import com.proyecto.supply_core.notificacion.enums.EventoNotificacion;
import com.proyecto.supply_core.proveedor.entity.Proveedor;
import com.proyecto.supply_core.proveedor.enums.EstadoProveedor;
import com.proyecto.supply_core.proveedor.repository.ProveedorRepository;
import com.proyecto.supply_core.inventario.repository.ProductoRepository;
import com.proyecto.supply_core.security.context.SesionActual;
import com.proyecto.supply_core.security.context.UsuarioAutenticado;

class OrdenDirectaServiceTest {

    private final SolicitudCompraRepository solicitudes = mock(SolicitudCompraRepository.class);
    private final ContratoRepository contratos = mock(ContratoRepository.class);
    private final UnidadMedidaService unidades = mock(UnidadMedidaService.class);
    private final ContratoService contratoService = new ContratoService(contratos, mock(ProveedorRepository.class),
            mock(ProductoRepository.class), solicitudes, unidades, mock(AuditoriaService.class));
    private final OrdenCompraRepository ordenes = mock(OrdenCompraRepository.class);
    private final OrdenCompraService ordenService = mock(OrdenCompraService.class);
    private final BodegaRepository bodegas = mock(BodegaRepository.class);
    private final ConsecutivoGenerator consecutivos = mock(ConsecutivoGenerator.class);
    private final AuditoriaService auditoria = mock(AuditoriaService.class);
    private final OrdenDirectaService service = new OrdenDirectaService(solicitudes, contratos, contratoService,
            ordenes, ordenService, bodegas, consecutivos, auditoria);

    private final LocalDate hoy = LocalDate.now();
    private MockHttpServletRequest request;
    private Proveedor tech;
    private Producto cable;
    private Producto breaker;
    private SolicitudCompra solicitud;
    private Contrato contrato;
    private Bodega bodega;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest();
        sesion(30L);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        UnidadMedida und = new UnidadMedida();
        und.setId(1L);
        und.setCodigo("UND");
        tech = new Proveedor();
        tech.setId(40L);
        tech.setRazonSocial("Tech SAS");
        tech.setEstado(EstadoProveedor.HABILITADO);
        cable = producto(100L, "Cable THHN", und);
        breaker = producto(101L, "Breaker 20 A", und);
        when(unidades.convertir(any(), any(), any())).thenAnswer(i -> i.getArgument(0));

        solicitud = new SolicitudCompra();
        solicitud.setId(7L);
        solicitud.setConsecutivo("SOL-000007");
        solicitud.setUsuarioSolicitanteId(10L);
        solicitud.setEstado(EstadoSolicitud.APROBADA);
        solicitud.setMontoEstimado(new BigDecimal("20000.00"));
        solicitud.agregarLinea(linea(cable, "10"));
        solicitud.agregarLinea(linea(breaker, "5"));
        when(solicitudes.bloquear(7L)).thenReturn(Optional.of(solicitud));
        when(solicitudes.findWithLineasById(7L)).thenReturn(Optional.of(solicitud));

        contrato = new Contrato();
        contrato.setId(5L);
        contrato.setProveedor(tech);
        contrato.setNumero("CT-5");
        contrato.setEstado(EstadoContrato.ACTIVO);
        contrato.setVigenteDesde(hoy.minusDays(5));
        contrato.setVigenteHasta(hoy.plusDays(60));
        contrato.agregarPrecio(precio(500L, cable, "1000", "19", null));
        contrato.agregarPrecio(precio(501L, breaker, "200", "0", null));
        when(contratos.bloquear(5L)).thenReturn(Optional.of(contrato));
        when(contratos.findDetalleById(5L)).thenReturn(Optional.of(contrato));

        bodega = new Bodega();
        bodega.setId(1L);
        bodega.setNombre("Principal");
        bodega.setActivo(true);
        when(bodegas.findById(1L)).thenReturn(Optional.of(bodega));
        when(consecutivos.siguiente(ConsecutivoGenerator.ORDEN_COMPRA)).thenReturn("OC-000007");
        when(ordenes.save(any())).thenAnswer(i -> {
            OrdenCompra o = i.getArgument(0);
            o.setId(99L);
            return o;
        });
        when(ordenService.respuesta(any())).thenAnswer(i -> OrdenResponse.of(i.getArgument(0), List.of()));
    }

    @AfterEach
    void limpiar() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void emiteLaOrdenAlPrecioPactadoGuardaElOrigenYPasaLaSolicitudAAdjudicada() {
        var r = emitir();

        ArgumentCaptor<OrdenCompra> guardada = ArgumentCaptor.forClass(OrdenCompra.class);
        verify(ordenes).save(guardada.capture());
        OrdenCompra o = guardada.getValue();
        assertNull(o.getAdjudicacionId());
        assertEquals(5L, o.getContratoId());
        assertEquals(7L, o.getSolicitudId());
        assertEquals(30L, o.getUsuarioId());
        assertEquals(new BigDecimal("11000.00"), o.getSubtotal()); // 10 x 1000 + 5 x 200
        assertEquals(new BigDecimal("1900.00"), o.getImpuestos()); // 19 % solo del cable
        assertEquals(new BigDecimal("12900.00"), o.getTotal());
        assertEquals(List.of(500L, 501L), o.getLineas().stream().map(l -> l.getContratoPrecioId()).toList());
        assertNull(o.getLineas().get(0).getCotizacionLineaId());
        assertEquals(EstadoSolicitud.ADJUDICADA, solicitud.getEstado());
        assertEquals("OC-000007", r.consecutivo());
        assertEquals(EstadoOrden.EMITIDA, r.estado());
        verify(ordenService).historial(o, EstadoOrden.EMITIDA, "Orden directa del contrato CT-5");
        verify(ordenService).avisarProveedor(eq(o), eq(EventoNotificacion.ORDEN_EMITIDA), anyString());
        verify(auditoria).exito(eq(AccionAuditoria.ORDEN_EMITIR_DIRECTA), eq(EntidadAuditada.ORDEN_COMPRA), eq(99L),
                anyString());
    }

    @Test
    void elSolicitanteNoPuedeEmitirLaOrdenDeSuPropiaSolicitud() {
        sesion(10L);

        assertThrows(AccesoDenegadoException.class, this::emitir);
        verify(ordenes, never()).save(any());
    }

    @Test
    void soloDeUnaSolicitudAprobadaYExistente() {
        solicitud.setEstado(EstadoSolicitud.EN_RFQ);
        assertEquals("SOLICITUD_NO_APROBADA", codigo());

        when(solicitudes.bloquear(7L)).thenReturn(Optional.empty());
        assertThrows(NoEncontradoException.class, this::emitir);
        when(solicitudes.bloquear(7L)).thenReturn(Optional.of(solicitud));
        when(contratos.bloquear(5L)).thenReturn(Optional.empty());
        solicitud.setEstado(EstadoSolicitud.APROBADA);
        assertThrows(NoEncontradoException.class, this::emitir);
    }

    @Test
    void unContratoVencidoAnuladoOConProveedorSuspendidoNoSeAplica() {
        contrato.setVigenteHasta(hoy.minusDays(1));
        assertEquals("CONTRATO_NO_VIGENTE", codigo());

        contrato.setVigenteHasta(hoy.plusDays(60));
        contrato.setEstado(EstadoContrato.ANULADO);
        assertEquals("CONTRATO_NO_VIGENTE", codigo());

        contrato.setEstado(EstadoContrato.ACTIVO);
        tech.setEstado(EstadoProveedor.SUSPENDIDO);
        assertEquals("PROVEEDOR_NO_HABILITADO", codigo());
        verify(ordenes, never()).save(any());
        assertEquals(EstadoSolicitud.APROBADA, solicitud.getEstado());
    }

    @Test
    void elContratoDebeCubrirTodaLaSolicitud() {
        contrato.getPrecios().remove(1);

        var ex = assertThrows(NegocioException.class, this::emitir);

        assertEquals("CONTRATO_NO_CUBRE", ex.getCodigo());
        assertTrue(ex.getMessage().contains("Breaker 20 A"));
    }

    @Test
    void laCantidadNoPuedePasarDelTopeQueQuedaDelContrato() {
        contrato.getPrecios().get(0).setCantidadMaxima(new BigDecimal("12.0000"));
        when(contratos.consumo(any(), any())).thenReturn(List.of(new ConsumoPrecio(500L, new BigDecimal("5"))));

        var ex = assertThrows(NegocioException.class, this::emitir);

        assertEquals("CONTRATO_TOPE_EXCEDIDO", ex.getCodigo());
        assertTrue(ex.getMessage().contains("quedan 7"));
        verify(ordenes, never()).save(any());
    }

    @Test
    void noSeSaltaLaAprobacionSiElPrecioPactadoSuperaLoAprobadoOElMontoEraIncompleto() {
        solicitud.setMontoEstimado(new BigDecimal("10999.99"));
        var ex = assertThrows(NegocioException.class, this::emitir);
        assertEquals("ORDEN_SUPERA_APROBADO", ex.getCodigo());
        assertTrue(ex.getMessage().contains("11000.00"));

        solicitud.setMontoEstimado(new BigDecimal("11000.00")); // justo lo aprobado sí se puede
        solicitud.setMontoIncompleto(true);
        assertEquals("MONTO_INCOMPLETO", codigo());
        verify(ordenes, never()).save(any());
        assertEquals(EstadoSolicitud.APROBADA, solicitud.getEstado());

        solicitud.setMontoIncompleto(false);
        assertEquals("OC-000007", emitir().consecutivo());
    }

    @Test
    void laBodegaDebeEstarActiva() {
        bodega.setActivo(false);

        assertEquals("BODEGA_INACTIVA", codigo());
        verify(ordenes, never()).save(any());
    }

    // ----------------------------------------------------------------- apoyo

    private OrdenResponse emitir() {
        return service.emitir(new EmitirDirectaRequest(7L, 5L, 1L, "Bodega principal, calle 10", hoy.plusDays(5)));
    }

    private String codigo() {
        return assertThrows(NegocioException.class, this::emitir).getCodigo();
    }

    private void sesion(Long id) {
        request.setAttribute(SesionActual.ATRIBUTO,
                new UsuarioAutenticado(id, "u@x.com", "Usuario", Set.of("COMPRADOR"), null, 1L));
    }

    private static Producto producto(Long id, String nombre, UnidadMedida unidad) {
        Producto p = new Producto();
        p.setId(id);
        p.setCodigo("P-" + id);
        p.setNombre(nombre);
        p.setUnidadMedida(unidad);
        p.setActivo(true);
        return p;
    }

    private static ContratoPrecio precio(Long id, Producto producto, String precio, String impuesto, String tope) {
        ContratoPrecio p = new ContratoPrecio();
        p.setId(id);
        p.setProducto(producto);
        p.setPrecioUnitario(new BigDecimal(precio));
        p.setImpuestoPct(new BigDecimal(impuesto));
        p.setCantidadMaxima(tope == null ? null : new BigDecimal(tope));
        return p;
    }

    private static SolicitudCompraLinea linea(Producto producto, String cantidad) {
        SolicitudCompraLinea l = new SolicitudCompraLinea();
        l.setProducto(producto);
        l.setUnidadMedida(producto.getUnidadMedida());
        l.setCantidad(new BigDecimal(cantidad));
        return l;
    }
}
