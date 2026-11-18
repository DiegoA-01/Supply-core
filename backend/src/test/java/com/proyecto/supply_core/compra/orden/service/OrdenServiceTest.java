package com.proyecto.supply_core.compra.orden.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.proyecto.supply_core.auditoria.service.AuditoriaService;
import com.proyecto.supply_core.catalogo.entity.UnidadMedida;
import com.proyecto.supply_core.catalogo.service.UnidadMedidaService;
import com.proyecto.supply_core.common.exception.AccesoDenegadoException;
import com.proyecto.supply_core.common.exception.NegocioException;
import com.proyecto.supply_core.common.exception.NoEncontradoException;
import com.proyecto.supply_core.common.exception.ParametroInvalidoException;
import com.proyecto.supply_core.common.util.ConsecutivoGenerator;
import com.proyecto.supply_core.compra.adjudicacion.entity.Adjudicacion;
import com.proyecto.supply_core.compra.adjudicacion.enums.EstadoAdjudicacion;
import com.proyecto.supply_core.compra.adjudicacion.repository.AdjudicacionRepository;
import com.proyecto.supply_core.compra.cotizacion.entity.Cotizacion;
import com.proyecto.supply_core.compra.cotizacion.entity.CotizacionLinea;
import com.proyecto.supply_core.compra.orden.dto.EmitirOrdenRequest;
import com.proyecto.supply_core.compra.orden.dto.NuevaVersionRequest;
import com.proyecto.supply_core.compra.orden.dto.OrdenResponse;
import com.proyecto.supply_core.compra.orden.entity.OrdenCompra;
import com.proyecto.supply_core.compra.orden.enums.EstadoOrden;
import com.proyecto.supply_core.compra.orden.repository.OrdenCompraRepository;
import com.proyecto.supply_core.compra.orden.repository.OrdenEstadoHistorialRepository;
import com.proyecto.supply_core.compra.solicitud.entity.SolicitudCompra;
import com.proyecto.supply_core.compra.solicitud.enums.EstadoSolicitud;
import com.proyecto.supply_core.compra.solicitud.repository.SolicitudCompraRepository;
import com.proyecto.supply_core.compra.rfq.entity.RfqLinea;
import com.proyecto.supply_core.inventario.entity.Bodega;
import com.proyecto.supply_core.inventario.entity.Producto;
import com.proyecto.supply_core.inventario.repository.BodegaRepository;
import com.proyecto.supply_core.notificacion.enums.EventoNotificacion;
import com.proyecto.supply_core.notificacion.service.NotificacionService;
import com.proyecto.supply_core.pago.repository.PagoRepository;
import com.proyecto.supply_core.proveedor.entity.Proveedor;
import com.proyecto.supply_core.security.context.SesionActual;
import com.proyecto.supply_core.security.context.UsuarioAutenticado;
import com.proyecto.supply_core.usuario.entity.Usuario;
import com.proyecto.supply_core.usuario.repository.UsuarioRepository;

class OrdenServiceTest {

    private final OrdenCompraRepository ordenes = mock(OrdenCompraRepository.class);
    private final OrdenEstadoHistorialRepository historial = mock(OrdenEstadoHistorialRepository.class);
    private final AdjudicacionRepository adjudicaciones = mock(AdjudicacionRepository.class);
    private final BodegaRepository bodegas = mock(BodegaRepository.class);
    private final UnidadMedidaService unidades = mock(UnidadMedidaService.class);
    private final UsuarioRepository usuarios = mock(UsuarioRepository.class);
    private final ConsecutivoGenerator consecutivos = mock(ConsecutivoGenerator.class);
    private final NotificacionService notificaciones = mock(NotificacionService.class);
    private final AuditoriaService auditoria = mock(AuditoriaService.class);
    private final PagoRepository pagos = mock(PagoRepository.class);
    private final SolicitudCompraRepository solicitudes = mock(SolicitudCompraRepository.class);
    private final OrdenCompraService service = new OrdenCompraService(ordenes, historial, adjudicaciones, bodegas,
            unidades, usuarios, consecutivos, auditoria, notificaciones, pagos, solicitudes);
    private final OrdenProveedorService proveedorService = new OrdenProveedorService(service, ordenes, auditoria,
            notificaciones);

    private MockHttpServletRequest request;
    private Adjudicacion adj;
    private final List<OrdenCompra> guardadas = new ArrayList<>();

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest();
        sesion(new UsuarioAutenticado(30L, "c@x.com", "C", Set.of("COMPRADOR"), null, 1L));
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        UnidadMedida caja = unidad(2L, "CAJA24");
        UnidadMedida und = unidad(1L, "UND");
        Producto lapiz = new Producto();
        lapiz.setId(100L);
        lapiz.setCodigo("LAP");
        lapiz.setUnidadMedida(und);
        RfqLinea rl = new RfqLinea();
        rl.setId(500L);
        rl.setUnidadMedida(caja); // la RFQ pidió cajas
        CotizacionLinea cl = new CotizacionLinea();
        cl.setId(600L);
        cl.setRfqLinea(rl);
        cl.setProducto(lapiz);
        cl.setCantidadOfertada(new BigDecimal("2"));     // 2 paquetes
        cl.setFactorConversion(BigDecimal.ONE);           // cada paquete = 1 caja
        cl.setSubtotal(new BigDecimal("21600.00"));
        cl.setImpuestoPct(new BigDecimal("19"));
        Proveedor tech = new Proveedor();
        tech.setId(40L);
        tech.setRazonSocial("Tech SAS");
        Cotizacion c = new Cotizacion();
        c.setProveedor(tech);
        c.agregarLinea(cl);
        adj = new Adjudicacion();
        adj.setId(50L);
        adj.setEstado(EstadoAdjudicacion.APROBADA);
        adj.setCotizacion(c);
        when(adjudicaciones.bloquear(50L)).thenReturn(Optional.of(adj));
        when(adjudicaciones.findDetalleById(50L)).thenReturn(Optional.of(adj));
        // 2 cajas x24 = 48 unidades
        when(unidades.convertir(new BigDecimal("2"), 2L, 1L)).thenReturn(new BigDecimal("48.0000"));
        Bodega b = new Bodega();
        b.setId(1L);
        b.setNombre("Principal");
        when(bodegas.findById(1L)).thenReturn(Optional.of(b));
        when(consecutivos.siguiente(ConsecutivoGenerator.ORDEN_COMPRA)).thenReturn("OC-000001");
        when(pagos.pagadoPorConsecutivo("OC-000001")).thenReturn(BigDecimal.ZERO);
        Usuario portal = new Usuario();
        portal.setId(77L);
        when(usuarios.findByProveedorId(40L)).thenReturn(List.of(portal));
        AtomicLong ids = new AtomicLong(10);
        AtomicLong lineas = new AtomicLong(800);
        when(ordenes.save(any())).thenAnswer(i -> {
            OrdenCompra o = i.getArgument(0);
            o.setId(ids.incrementAndGet());
            o.getLineas().forEach(l -> l.setId(lineas.incrementAndGet()));
            when(ordenes.bloquear(o.getId())).thenReturn(Optional.of(o));
            when(ordenes.findDetalleById(o.getId())).thenReturn(Optional.of(o));
            guardadas.add(o);
            return o;
        });
    }

    @AfterEach
    void limpiar() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void emitirConvierteALaUnidadDelProductoYConservaLosMontos() {
        var r = emitir();

        assertEquals("OC-000001", r.consecutivo());
        assertEquals(1, r.version());
        assertEquals(EstadoOrden.EMITIDA, r.estado());
        assertEquals(new BigDecimal("48.0000"), r.lineas().get(0).cantidad());
        assertEquals(new BigDecimal("450.0000"), r.lineas().get(0).precioUnitario());
        assertEquals(new BigDecimal("21600.00"), r.subtotal());
        assertEquals(new BigDecimal("4104.00"), r.impuestos());
        assertEquals(new BigDecimal("25704.00"), r.total());
        verify(notificaciones).notificar(eq(77L), eq(EventoNotificacion.ORDEN_EMITIDA), anyString());
    }

    @Test
    void soloDeAdjudicacionAprobadaYUnaVigente() {
        when(ordenes.existsByAdjudicacionIdAndEstadoNotIn(eq(50L), any())).thenReturn(true);
        assertEquals("ORDEN_EXISTENTE", assertThrows(NegocioException.class, this::emitir).getCodigo());

        adj.setEstado(EstadoAdjudicacion.PENDIENTE_APROBACION);
        assertEquals("ADJUDICACION_NO_APROBADA", assertThrows(NegocioException.class, this::emitir).getCodigo());
    }

    @Test
    void nuevaVersionReemplazaLaAnteriorYRecalcula() {
        var v1 = emitir();
        Long linea = v1.lineas().get(0).id();

        var v2 = service.nuevaVersion(v1.id(), new NuevaVersionRequest("El cliente redujo el pedido", null, null,
                LocalDate.now().plusDays(10), List.of(new NuevaVersionRequest.CambioCantidad(linea, new BigDecimal("24")))));

        assertEquals(2, v2.version());
        assertEquals(v1.id(), v2.ordenPadreId());
        assertEquals("OC-000001", v2.consecutivo());
        assertEquals(EstadoOrden.REEMPLAZADA, guardadas.get(0).getEstado());
        assertEquals(new BigDecimal("10800.00"), v2.subtotal()); // 24 x 450
        assertEquals(new BigDecimal("12852.00"), v2.total());
        verify(notificaciones).notificar(eq(77L), eq(EventoNotificacion.ORDEN_ACTUALIZADA), anyString());

        assertThrows(ParametroInvalidoException.class, () -> service.nuevaVersion(v2.id(), new NuevaVersionRequest(
                "otro cambio", null, null, null, List.of(new NuevaVersionRequest.CambioCantidad(9999L, BigDecimal.ONE)))));
        assertEquals("ORDEN_SIN_LINEAS", assertThrows(NegocioException.class, () -> service.nuevaVersion(v2.id(),
                new NuevaVersionRequest("quitar todo", null, null, null, List.of(new NuevaVersionRequest.CambioCantidad(
                        v2.lineas().get(0).id(), BigDecimal.ZERO))))).getCodigo());
    }

    @Test
    void nuevaVersionNoQuedaPorDebajoDeLoPagado() {
        var v1 = emitir();
        when(pagos.pagadoPorConsecutivo("OC-000001")).thenReturn(new BigDecimal("20000.00"));

        NegocioException e = assertThrows(NegocioException.class, () -> service.nuevaVersion(v1.id(),
                new NuevaVersionRequest("Reducir a la mitad", null, null, null, List.of(
                        new NuevaVersionRequest.CambioCantidad(v1.lineas().get(0).id(), new BigDecimal("24"))))));
        assertEquals("ORDEN_TOTAL_MENOR_PAGADO", e.getCodigo()); // 12852 < 20000
        assertEquals(EstadoOrden.EMITIDA, guardadas.get(0).getEstado());
    }

    @Test
    void laVersionDeUnaOrdenDeContratoSoloBajaLaCantidadYConservaElOrigen() {
        var v1 = emitir();
        OrdenCompra o = guardadas.get(0);
        o.setAdjudicacionId(null);
        o.setContratoId(7L);
        o.setSolicitudId(9L);
        o.getLineas().get(0).setContratoPrecioId(300L);
        Long linea = v1.lineas().get(0).id();

        assertEquals("CONTRATO_TOPE_EXCEDIDO", assertThrows(NegocioException.class, () -> service.nuevaVersion(v1.id(),
                new NuevaVersionRequest("Pedir más unidades", null, null, null,
                        List.of(new NuevaVersionRequest.CambioCantidad(linea, new BigDecimal("60")))))).getCodigo());

        var v2 = service.nuevaVersion(v1.id(), new NuevaVersionRequest("Reducir el pedido", null, null, null,
                List.of(new NuevaVersionRequest.CambioCantidad(linea, new BigDecimal("24")))));
        OrdenCompra nueva = guardadas.get(1);
        assertEquals(2, v2.version());
        assertEquals(7L, nueva.getContratoId());
        assertEquals(9L, nueva.getSolicitudId());
        assertEquals(300L, nueva.getLineas().get(0).getContratoPrecioId());
    }

    @Test
    void cancelarUnaOrdenDirectaDevuelveLaSolicitudAAprobada() {
        var v1 = emitir();
        OrdenCompra o = guardadas.get(0);
        o.setAdjudicacionId(null);
        o.setContratoId(7L);
        o.setSolicitudId(9L);
        SolicitudCompra s = new SolicitudCompra();
        s.setId(9L);
        s.setEstado(EstadoSolicitud.ADJUDICADA);
        when(solicitudes.findById(9L)).thenReturn(Optional.of(s));

        service.cancelar(v1.id(), "El proveedor no puede entregar");

        assertEquals(EstadoOrden.CANCELADA, o.getEstado());
        assertEquals(EstadoSolicitud.APROBADA, s.getEstado());
    }

    @Test
    void cancelarUnaOrdenConAdjudicacionNoTocaLaSolicitud() {
        var v1 = emitir();

        service.cancelar(v1.id(), "Cambio de planes");

        verify(solicitudes, org.mockito.Mockito.never()).findById(any());
    }

    @Test
    void flujoDelProveedorHastaDespachar() {
        var v1 = emitir();
        sesion(new UsuarioAutenticado(77L, "p@x.com", "P", Set.of("PROVEEDOR"), 40L, 1L));

        assertThrows(NegocioException.class, () -> proveedorService.despachar(v1.id(), null)); // aún no aceptada
        assertEquals(EstadoOrden.ACEPTADA_PROVEEDOR, proveedorService.aceptar(v1.id()).estado());
        assertEquals(EstadoOrden.EN_PREPARACION, proveedorService.preparar(v1.id()).estado());
        assertEquals(EstadoOrden.DESPACHADA, proveedorService.despachar(v1.id(), "Guía 123").estado());

        verify(notificaciones).notificarRol(eq("ALMACENISTA"), eq(EventoNotificacion.ORDEN_DESPACHADA), anyString());
        verify(notificaciones, times(3)).notificar(eq(30L),
                eq(EventoNotificacion.ORDEN_RESPUESTA_PROVEEDOR), anyString());

        sesion(new UsuarioAutenticado(30L, "c@x.com", "C", Set.of("COMPRADOR"), null, 1L));
        assertEquals("TRANSICION_INVALIDA",
                assertThrows(NegocioException.class, () -> service.cancelar(v1.id(), "tarde")).getCodigo());
    }

    @Test
    void proveedorAjenoYRechazoSinMotivo() {
        var v1 = emitir();
        sesion(new UsuarioAutenticado(78L, "o@x.com", "O", Set.of("PROVEEDOR"), 41L, 1L));
        assertThrows(NoEncontradoException.class, () -> proveedorService.aceptar(v1.id()));
        assertThrows(NoEncontradoException.class, () -> proveedorService.obtener(v1.id()));

        sesion(new UsuarioAutenticado(77L, "p@x.com", "P", Set.of("PROVEEDOR"), 40L, 1L));
        assertThrows(ParametroInvalidoException.class, () -> proveedorService.rechazar(v1.id(), " "));
        assertEquals(EstadoOrden.RECHAZADA_PROVEEDOR, proveedorService.rechazar(v1.id(), "Sin stock").estado());

        sesion(new UsuarioAutenticado(30L, "c@x.com", "C", Set.of("COMPRADOR"), null, 1L));
        assertEquals(EstadoOrden.CANCELADA, service.cancelar(v1.id(), "Se compra a otro").estado());
    }

    @Test
    void reglaDeDocumentos() {
        var v1 = emitir();
        OrdenReglaDocumentos regla = new OrdenReglaDocumentos(ordenes);
        UsuarioAutenticado dueno = new UsuarioAutenticado(77L, "p@x.com", "P", Set.of("PROVEEDOR"), 40L, 1L);
        UsuarioAutenticado bodega = new UsuarioAutenticado(5L, "b@x.com", "B", Set.of("ALMACENISTA"), null, 1L);

        assertThrows(AccesoDenegadoException.class, () -> regla.validarSubida(v1.id(), dueno)); // EMITIDA
        guardadas.get(0).setEstado(EstadoOrden.EN_PREPARACION);
        assertDoesNotThrow(() -> regla.validarSubida(v1.id(), dueno));
        assertDoesNotThrow(() -> regla.validarLectura(v1.id(), bodega));
        assertThrows(AccesoDenegadoException.class, () -> regla.validarSubida(v1.id(), bodega));
        assertTrue(EstadoOrden.DESPACHADA.puedePasarA(EstadoOrden.RECIBIDA_PARCIAL));
        assertTrue(EstadoOrden.RECIBIDA_PARCIAL.puedePasarA(EstadoOrden.CERRADA)); // cierre con faltante
    }

    private OrdenResponse emitir() {
        return service.emitir(new EmitirOrdenRequest(50L, 1L, "Bodega principal, calle 10", LocalDate.now().plusDays(5)));
    }

    private static UnidadMedida unidad(Long id, String codigo) {
        UnidadMedida u = new UnidadMedida();
        u.setId(id);
        u.setCodigo(codigo);
        return u;
    }

    private void sesion(UsuarioAutenticado u) {
        request.setAttribute(SesionActual.ATRIBUTO, u);
    }
}
