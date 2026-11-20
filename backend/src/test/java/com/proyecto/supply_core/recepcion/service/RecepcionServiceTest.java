package com.proyecto.supply_core.recepcion.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
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
import com.proyecto.supply_core.common.exception.NegocioException;
import com.proyecto.supply_core.common.exception.NoEncontradoException;
import com.proyecto.supply_core.common.exception.ParametroInvalidoException;
import com.proyecto.supply_core.compra.orden.entity.OrdenCompra;
import com.proyecto.supply_core.compra.orden.entity.OrdenCompraLinea;
import com.proyecto.supply_core.compra.orden.enums.EstadoOrden;
import com.proyecto.supply_core.compra.orden.repository.OrdenCompraRepository;
import com.proyecto.supply_core.compra.orden.service.OrdenCompraService;
import com.proyecto.supply_core.inventario.entity.Bodega;
import com.proyecto.supply_core.inventario.entity.Producto;
import com.proyecto.supply_core.inventario.repository.BodegaRepository;
import com.proyecto.supply_core.inventario.service.MovimientoInventarioService;
import com.proyecto.supply_core.notificacion.enums.EventoNotificacion;
import com.proyecto.supply_core.notificacion.service.NotificacionService;
import com.proyecto.supply_core.proveedor.dto.IndicadoresEvaluacion;
import com.proyecto.supply_core.proveedor.entity.Proveedor;
import com.proyecto.supply_core.proveedor.service.EvaluacionProveedorService;
import com.proyecto.supply_core.recepcion.dto.LineaRecepcionRequest;
import com.proyecto.supply_core.recepcion.dto.RecepcionResponse;
import com.proyecto.supply_core.recepcion.dto.RegistrarRecepcionRequest;
import com.proyecto.supply_core.recepcion.entity.Recepcion;
import com.proyecto.supply_core.recepcion.entity.RecepcionLinea;
import com.proyecto.supply_core.recepcion.enums.DecisionExceso;
import com.proyecto.supply_core.recepcion.enums.EstadoRecepcionLinea;
import com.proyecto.supply_core.recepcion.enums.MotivoRechazo;
import com.proyecto.supply_core.recepcion.repository.RecepcionLineaRepository;
import com.proyecto.supply_core.recepcion.repository.RecepcionRepository;
import com.proyecto.supply_core.security.context.SesionActual;
import com.proyecto.supply_core.security.context.UsuarioAutenticado;
import com.proyecto.supply_core.usuario.repository.UsuarioRepository;

class RecepcionServiceTest {

    private final RecepcionRepository recepciones = mock(RecepcionRepository.class);
    private final RecepcionLineaRepository lineas = mock(RecepcionLineaRepository.class);
    private final OrdenCompraRepository ordenes = mock(OrdenCompraRepository.class);
    private final OrdenCompraService ordenService = mock(OrdenCompraService.class);
    private final BodegaRepository bodegas = mock(BodegaRepository.class);
    private final MovimientoInventarioService movimientos = mock(MovimientoInventarioService.class);
    private final EvaluacionProveedorService evaluacion = mock(EvaluacionProveedorService.class);
    private final UsuarioRepository usuarios = mock(UsuarioRepository.class);
    private final AuditoriaService auditoria = mock(AuditoriaService.class);
    private final NotificacionService notificaciones = mock(NotificacionService.class);
    private final com.proyecto.supply_core.inventario.service.CostoPromedioService costos =
            mock(com.proyecto.supply_core.inventario.service.CostoPromedioService.class);
    private RecepcionService service;

    private OrdenCompra orden;
    private Bodega bodega;
    private Producto cable;
    private Producto cinta;
    private OrdenCompraLinea lineaCable;
    private OrdenCompraLinea lineaCinta;

    @BeforeEach
    void setUp() {
        service = new RecepcionService(recepciones, lineas, ordenes, ordenService, bodegas, movimientos, evaluacion,
                usuarios, auditoria, notificaciones, costos);
        sesion(new UsuarioAutenticado(7L, "alm@x.com", "Bodeguero", Set.of("ALMACENISTA"), null, 1L));

        bodega = new Bodega();
        bodega.setId(3L);
        bodega.setNombre("Bodega central");
        Proveedor prov = new Proveedor();
        prov.setId(40L);
        prov.setRazonSocial("Eléctricos SAS");
        cable = producto(11L, "CAB-1", "Cable");
        cinta = producto(12L, "CIN-1", "Cinta");

        orden = new OrdenCompra();
        orden.setId(50L);
        orden.setConsecutivo("OC-000050");
        orden.setVersion(1);
        orden.setProveedor(prov);
        orden.setBodegaDestino(bodega);
        orden.setUsuarioId(30L);
        orden.setEstado(EstadoOrden.DESPACHADA);
        orden.setFechaEntregaEsperada(LocalDate.now());
        lineaCable = lineaOrden(501L, cable, "10", "100");
        lineaCinta = lineaOrden(502L, cinta, "4", "25");
        orden.agregarLinea(lineaCable);
        orden.agregarLinea(lineaCinta);

        when(ordenService.bloquear(50L)).thenReturn(orden);
        when(ordenes.findDetalleById(50L)).thenReturn(Optional.of(orden));
        when(lineas.agregadoPorLinea(50L)).thenReturn(List.of());
        when(usuarios.findByProveedorId(40L)).thenReturn(List.of());
        when(recepciones.saveAndFlush(any(Recepcion.class))).thenAnswer(inv -> {
            Recepcion r = inv.getArgument(0);
            r.setId(900L);
            long id = 1000;
            for (RecepcionLinea l : r.getLineas()) {
                l.setId(id++);
            }
            return r;
        });
    }

    @AfterEach
    void limpiar() {
        RequestContextHolder.resetRequestAttributes();
    }

    // ------------------------------------------------------------------ registrar

    @Test
    void recepcionCompletaSubeElStockActualizaElCostoYPasaALaOrdenATotal() {
        RecepcionResponse r = service.registrar(50L, pedido(linea(501L, "10", "0", null), linea(502L, "4", "0", null)));

        assertEquals(900L, r.id());
        verify(movimientos).registrarEntradaCompra(eq(11L), eq(3L), eq(new BigDecimal("10")), any(), eq("RECEPCION"), eq(900L));
        verify(movimientos).registrarEntradaCompra(eq(12L), eq(3L), eq(new BigDecimal("4")), any(), eq("RECEPCION"), eq(900L));
        assertEquals(0, new BigDecimal("100").compareTo(cable.getCostoReferencia()));
        assertEquals(0, new BigDecimal("25").compareTo(cinta.getCostoReferencia()));
        assertEquals(EstadoOrden.RECIBIDA_TOTAL, orden.getEstado());
        verify(ordenService).historial(eq(orden), eq(EstadoOrden.RECIBIDA_TOTAL), anyString());
        verify(auditoria).exito(eq(AccionAuditoria.RECEPCION_REGISTRAR), eq(EntidadAuditada.RECEPCION), eq(900L), anyString());
        verify(notificaciones).notificar(eq(30L), eq(EventoNotificacion.RECEPCION_REGISTRADA), anyString());
    }

    @Test
    void loRechazadoNoSubeElStockYQuedaConSuMotivo() {
        RecepcionResponse r = service.registrar(50L, pedido(
                linea(501L, "10", "4", MotivoRechazo.DANADO), linea(502L, "4", "0", null)));

        verify(movimientos).registrarEntradaCompra(eq(11L), eq(3L), eq(new BigDecimal("6")), any(), eq("RECEPCION"), eq(900L));
        var cableLinea = r.lineas().stream().filter(l -> l.ordenLineaId() == 501L).findFirst().orElseThrow();
        assertEquals(0, new BigDecimal("4").compareTo(cableLinea.cantidadRechazada()));
        assertEquals(MotivoRechazo.DANADO, cableLinea.motivoRechazo());
        assertEquals(0, new BigDecimal("4").compareTo(cableLinea.cantidadFaltante()));
        assertEquals(EstadoOrden.RECIBIDA_PARCIAL, orden.getEstado());
    }

    @Test
    void elExcedenteNoSubeElStockQuedaEnRevisionYAvisaAlComprador() {
        RecepcionResponse r = service.registrar(50L, pedido(linea(501L, "13", "0", null), linea(502L, "4", "0", null)));

        verify(movimientos).registrarEntradaCompra(eq(11L), eq(3L), eq(new BigDecimal("10")), any(), eq("RECEPCION"), eq(900L));
        var cableLinea = r.lineas().stream().filter(l -> l.ordenLineaId() == 501L).findFirst().orElseThrow();
        assertEquals(EstadoRecepcionLinea.EN_REVISION, cableLinea.estado());
        assertEquals(0, new BigDecimal("3").compareTo(cableLinea.cantidadExcedente()));
        assertEquals(EstadoOrden.RECIBIDA_TOTAL, orden.getEstado());
        verify(notificaciones).notificarRol(eq("COMPRADOR"), eq(EventoNotificacion.RECEPCION_EXCEDENTE), anyString());
    }

    @Test
    void unaSegundaRecepcionDescuentaLoYaAceptado() {
        var previo = mock(RecepcionLineaRepository.AgregadoLinea.class);
        when(previo.getOrdenLineaId()).thenReturn(501L);
        when(previo.getAceptada()).thenReturn(new BigDecimal("6"));
        when(lineas.agregadoPorLinea(50L)).thenReturn(List.of(previo));
        orden.setEstado(EstadoOrden.RECIBIDA_PARCIAL);

        service.registrar(50L, pedido(linea(501L, "4", "0", null)));

        verify(movimientos).registrarEntradaCompra(eq(11L), eq(3L), eq(new BigDecimal("4")), any(), eq("RECEPCION"), eq(900L));
        // la cinta nunca llegó: la orden sigue parcial
        assertEquals(EstadoOrden.RECIBIDA_PARCIAL, orden.getEstado());
    }

    @Test
    void soloSeRecibeUnaOrdenDespachadaORecibidaParcialmente() {
        orden.setEstado(EstadoOrden.EN_PREPARACION);

        NegocioException ex = assertThrows(NegocioException.class,
                () -> service.registrar(50L, pedido(linea(501L, "1", "0", null))));

        assertEquals("ORDEN_NO_RECIBIBLE", ex.getCodigo());
        verify(movimientos, never()).registrarEntradaCompra(anyLong(), anyLong(), any(), any(), anyString(), anyLong());
    }

    @Test
    void unaLineaQueNoEsDeLaOrdenRespondeNoEncontrado() {
        assertThrows(NoEncontradoException.class, () -> service.registrar(50L, pedido(linea(999L, "1", "0", null))));
        verify(recepciones, never()).saveAndFlush(any());
    }

    @Test
    void unaBodegaInactivaSeRechaza() {
        Bodega otra = new Bodega();
        otra.setId(8L);
        otra.setNombre("Vieja");
        otra.setActivo(false);
        when(bodegas.findById(8L)).thenReturn(Optional.of(otra));

        NegocioException ex = assertThrows(NegocioException.class, () -> service.registrar(50L,
                new RegistrarRecepcionRequest(8L, null, List.of(linea(501L, "1", "0", null)))));

        assertEquals("BODEGA_INACTIVA", ex.getCodigo());
    }

    // ------------------------------------------------------------------ cerrar

    @Test
    void cerrarUnaOrdenCompletaLaPasaACerradaYEvaluaAlProveedor() {
        orden.setEstado(EstadoOrden.RECIBIDA_TOTAL);
        var cableTot = agregado(501L, "10", "10", "0", "0");
        var cintaTot = agregado(502L, "4", "4", "0", "0");
        when(lineas.agregadoPorLinea(50L)).thenReturn(List.of(cableTot, cintaTot));
        when(lineas.hayEnRevision(50L)).thenReturn(false);
        when(recepciones.findByOrden_IdOrderByRecibidoEnAscIdAsc(50L)).thenReturn(List.of());

        service.cerrar(50L, null);

        assertEquals(EstadoOrden.CERRADA, orden.getEstado());
        ArgumentCaptor<IndicadoresEvaluacion> ind = ArgumentCaptor.forClass(IndicadoresEvaluacion.class);
        verify(evaluacion).registrar(eq(40L), eq(50L), ind.capture());
        assertEquals(0, new BigDecimal("100").compareTo(ind.getValue().cumplimientoCantidad()));
        assertEquals(0, new BigDecimal("100").compareTo(ind.getValue().calidad()));
        verify(auditoria).exito(eq(AccionAuditoria.RECEPCION_CERRAR), eq(EntidadAuditada.ORDEN_COMPRA), eq(50L), anyString());
        verify(notificaciones).notificar(eq(30L), eq(EventoNotificacion.RECEPCION_CERRADA), anyString());
    }

    @Test
    void cerrarConFaltanteExigeMotivo() {
        orden.setEstado(EstadoOrden.RECIBIDA_PARCIAL);
        when(lineas.hayEnRevision(50L)).thenReturn(false);

        assertThrows(ParametroInvalidoException.class, () -> service.cerrar(50L, "  "));
        verify(evaluacion, never()).registrar(anyLong(), anyLong(), any());

        service.cerrar(50L, "El proveedor no tiene más cinta");
        assertEquals(EstadoOrden.CERRADA, orden.getEstado());
        verify(ordenService).historial(eq(orden), eq(EstadoOrden.CERRADA), eq("Cierre con faltante: El proveedor no tiene más cinta"));
    }

    @Test
    void noSeCierraConExcedenteSinResolverNiUnaOrdenSinRecepciones() {
        orden.setEstado(EstadoOrden.RECIBIDA_TOTAL);
        when(lineas.hayEnRevision(50L)).thenReturn(true);

        NegocioException ex = assertThrows(NegocioException.class, () -> service.cerrar(50L, null));
        assertEquals("EXCEDENTE_SIN_RESOLVER", ex.getCodigo());

        orden.setEstado(EstadoOrden.DESPACHADA);
        NegocioException otra = assertThrows(NegocioException.class, () -> service.cerrar(50L, null));
        assertEquals("ORDEN_NO_CERRABLE", otra.getCodigo());
    }

    // ------------------------------------------------------------------ resolver excedente

    @Test
    void aceptarElExcedenteLoSubeAlStock() {
        RecepcionLinea l = lineaEnRevision();
        when(lineas.findDetalleById(700L)).thenReturn(Optional.of(l));
        orden.setEstado(EstadoOrden.RECIBIDA_TOTAL);

        var r = service.resolverExceso(700L, DecisionExceso.ACEPTAR, "Nos sirve para la obra");

        verify(movimientos).registrarEntradaCompra(eq(11L), eq(3L), eq(new BigDecimal("3")), any(), eq("RECEPCION"), eq(900L));
        assertEquals(EstadoRecepcionLinea.ACEPTADA, r.estado());
        assertEquals(0, new BigDecimal("13").compareTo(r.cantidadAceptada()));
        assertNotNull(r.resueltoEn());
        assertEquals(7L, r.resueltoPorUsuarioId());
    }

    @Test
    void devolverElExcedenteNoTocaElStock() {
        RecepcionLinea l = lineaEnRevision();
        when(lineas.findDetalleById(700L)).thenReturn(Optional.of(l));
        orden.setEstado(EstadoOrden.RECIBIDA_TOTAL);

        var r = service.resolverExceso(700L, DecisionExceso.DEVOLVER, "No lo necesitamos");

        assertEquals(EstadoRecepcionLinea.EXCEDENTE_DEVUELTO, r.estado());
        verify(movimientos, never()).registrarEntradaCompra(anyLong(), anyLong(), any(), any(), anyString(), anyLong());
    }

    @Test
    void soloSeResuelveUnaLineaEnRevisionDeUnaOrdenAbierta() {
        RecepcionLinea l = lineaEnRevision();
        l.setEstado(EstadoRecepcionLinea.ACEPTADA);
        when(lineas.findDetalleById(700L)).thenReturn(Optional.of(l));
        orden.setEstado(EstadoOrden.RECIBIDA_TOTAL);
        assertEquals("LINEA_NO_EN_REVISION", assertThrows(NegocioException.class,
                () -> service.resolverExceso(700L, DecisionExceso.ACEPTAR, "motivo")).getCodigo());

        l.setEstado(EstadoRecepcionLinea.EN_REVISION);
        orden.setEstado(EstadoOrden.CERRADA);
        assertEquals("ORDEN_CERRADA", assertThrows(NegocioException.class,
                () -> service.resolverExceso(700L, DecisionExceso.ACEPTAR, "motivo")).getCodigo());

        when(lineas.findDetalleById(701L)).thenReturn(Optional.empty());
        assertThrows(NoEncontradoException.class, () -> service.resolverExceso(701L, DecisionExceso.ACEPTAR, "motivo"));
    }

    // ------------------------------------------------------------------ consulta

    @Test
    void elResumenMuestraPedidoAceptadoRechazadoYFaltante() {
        var cableTot = agregado(501L, "6", "10", "4", "0");
        when(lineas.agregadoPorLinea(50L)).thenReturn(List.of(cableTot));
        when(recepciones.findByOrden_IdOrderByRecibidoEnAscIdAsc(50L)).thenReturn(List.of());

        var resumen = service.deOrden(50L);

        var avance = resumen.lineas().stream().filter(a -> a.ordenLineaId() == 501L).findFirst().orElseThrow();
        assertEquals(0, new BigDecimal("6").compareTo(avance.aceptado()));
        assertEquals(0, new BigDecimal("4").compareTo(avance.rechazado()));
        assertEquals(0, new BigDecimal("4").compareTo(avance.faltante()));
        var sinRecibir = resumen.lineas().stream().filter(a -> a.ordenLineaId() == 502L).findFirst().orElseThrow();
        assertEquals(0, new BigDecimal("4").compareTo(sinRecibir.faltante()));
    }

    @Test
    void elProveedorSoloVeLasRecepcionesDeSusOrdenes() {
        sesion(new UsuarioAutenticado(9L, "p@x.com", "Prov", Set.of("PROVEEDOR"), 41L, 1L));
        assertThrows(NoEncontradoException.class, () -> service.deOrdenDelProveedor(50L));

        sesion(new UsuarioAutenticado(9L, "p@x.com", "Prov", Set.of("PROVEEDOR"), 40L, 1L));
        when(recepciones.findByOrden_IdOrderByRecibidoEnAscIdAsc(50L)).thenReturn(List.of());
        assertTrue(service.deOrdenDelProveedor(50L).recepciones().isEmpty());
    }

    // ------------------------------------------------------------------ apoyo

    private static Producto producto(Long id, String codigo, String nombre) {
        Producto p = new Producto();
        p.setId(id);
        p.setCodigo(codigo);
        p.setNombre(nombre);
        return p;
    }

    private static OrdenCompraLinea lineaOrden(Long id, Producto p, String cantidad, String precio) {
        OrdenCompraLinea l = new OrdenCompraLinea();
        l.setId(id);
        l.setProducto(p);
        l.setCantidad(new BigDecimal(cantidad));
        l.setPrecioUnitario(new BigDecimal(precio));
        return l;
    }

    private static LineaRecepcionRequest linea(Long ordenLineaId, String recibida, String rechazada, MotivoRechazo motivo) {
        return new LineaRecepcionRequest(ordenLineaId, new BigDecimal(recibida), new BigDecimal(rechazada), motivo, null);
    }

    private static RegistrarRecepcionRequest pedido(LineaRecepcionRequest... l) {
        return new RegistrarRecepcionRequest(null, null, new ArrayList<>(List.of(l)));
    }

    private RecepcionLinea lineaEnRevision() {
        Recepcion rec = new Recepcion();
        rec.setId(900L);
        rec.setOrden(orden);
        rec.setBodega(bodega);
        RecepcionLinea l = new RecepcionLinea();
        l.setId(700L);
        l.setRecepcion(rec);
        l.setOrdenLinea(lineaCable);
        l.setCantidadRecibida(new BigDecimal("13"));
        l.setCantidadRechazada(BigDecimal.ZERO);
        l.setCantidadAceptada(new BigDecimal("10"));
        l.setCantidadExcedente(new BigDecimal("3"));
        l.setCantidadFaltante(BigDecimal.ZERO);
        l.setEstado(EstadoRecepcionLinea.EN_REVISION);
        return l;
    }

    private static RecepcionLineaRepository.AgregadoLinea agregado(Long ordenLineaId, String aceptada, String recibida,
            String rechazada, String excedente) {
        var a = mock(RecepcionLineaRepository.AgregadoLinea.class);
        when(a.getOrdenLineaId()).thenReturn(ordenLineaId);
        when(a.getAceptada()).thenReturn(new BigDecimal(aceptada));
        when(a.getRecibida()).thenReturn(new BigDecimal(recibida));
        when(a.getRechazada()).thenReturn(new BigDecimal(rechazada));
        when(a.getExcedente()).thenReturn(new BigDecimal(excedente));
        return a;
    }

    private static void sesion(UsuarioAutenticado u) {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setAttribute(SesionActual.ATRIBUTO, u);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(req));
    }
}
