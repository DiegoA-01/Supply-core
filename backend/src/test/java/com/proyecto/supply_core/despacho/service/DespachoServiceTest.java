package com.proyecto.supply_core.despacho.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
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
import org.mockito.InOrder;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.proyecto.supply_core.auditoria.enums.AccionAuditoria;
import com.proyecto.supply_core.auditoria.enums.EntidadAuditada;
import com.proyecto.supply_core.auditoria.service.AuditoriaService;
import com.proyecto.supply_core.catalogo.entity.UnidadMedida;
import com.proyecto.supply_core.catalogo.repository.UnidadMedidaRepository;
import com.proyecto.supply_core.catalogo.service.UnidadMedidaService;
import com.proyecto.supply_core.common.exception.NegocioException;
import com.proyecto.supply_core.common.util.ConsecutivoGenerator;
import com.proyecto.supply_core.despacho.dto.DespachoRequest;
import com.proyecto.supply_core.despacho.dto.LineaDespachoRequest;
import com.proyecto.supply_core.despacho.entity.Despacho;
import com.proyecto.supply_core.despacho.enums.EstadoDespacho;
import com.proyecto.supply_core.despacho.repository.DespachoRepository;
import com.proyecto.supply_core.inventario.dto.RegistroMovimientoResponse;
import com.proyecto.supply_core.inventario.entity.Bodega;
import com.proyecto.supply_core.inventario.entity.Producto;
import com.proyecto.supply_core.inventario.enums.TipoMovimiento;
import com.proyecto.supply_core.inventario.repository.BodegaRepository;
import com.proyecto.supply_core.inventario.repository.ProductoRepository;
import com.proyecto.supply_core.inventario.service.MovimientoInventarioService;
import com.proyecto.supply_core.security.context.SesionActual;
import com.proyecto.supply_core.security.context.UsuarioAutenticado;

class DespachoServiceTest {

    private final DespachoRepository despachos = mock(DespachoRepository.class);
    private final BodegaRepository bodegas = mock(BodegaRepository.class);
    private final ProductoRepository productos = mock(ProductoRepository.class);
    private final UnidadMedidaRepository unidades = mock(UnidadMedidaRepository.class);
    private final UnidadMedidaService unidadService = mock(UnidadMedidaService.class);
    private final MovimientoInventarioService movimientos = mock(MovimientoInventarioService.class);
    private final ConsecutivoGenerator consecutivos = mock(ConsecutivoGenerator.class);
    private final AuditoriaService auditoria = mock(AuditoriaService.class);
    private final DespachoService service = new DespachoService(despachos, bodegas, productos, unidades, unidadService,
            movimientos, consecutivos, auditoria);

    private Bodega principal;
    private Despacho guardado;

    @BeforeEach
    void setUp() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setAttribute(SesionActual.ATRIBUTO,
                new UsuarioAutenticado(7L, "alm@x.com", "Bodeguero", Set.of("ALMACENISTA"), null, 1L));
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(req));

        principal = new Bodega();
        principal.setId(1L);
        principal.setNombre("Principal");
        when(bodegas.findById(1L)).thenReturn(Optional.of(principal));

        UnidadMedida rollo = unidad(10L, "ROLLO");
        unidad(11L, "M");
        UnidadMedida bulto = unidad(12L, "BULTO");
        producto(200L, "CEM-50", bulto);
        producto(100L, "CAB-12", rollo);
        // 1 rollo = 100 m; el resto se queda igual
        when(unidadService.convertir(any(), anyLong(), anyLong())).thenAnswer(i -> i.getArgument(0));
        when(unidadService.convertir(any(), eq(11L), eq(10L)))
                .thenAnswer(i -> i.<BigDecimal>getArgument(0).divide(BigDecimal.valueOf(100)));

        when(consecutivos.siguiente(ConsecutivoGenerator.DESPACHO)).thenReturn("DES-000001");
        when(despachos.save(any())).thenAnswer(i -> {
            guardado = i.getArgument(0);
            guardado.setId(5L);
            when(despachos.bloquear(5L)).thenReturn(Optional.of(guardado));
            when(despachos.findWithDetalleById(5L)).thenReturn(Optional.of(guardado));
            return guardado;
        });
        when(movimientos.registrarSalidaVenta(anyLong(), anyLong(), any(), anyString(), anyLong()))
                .thenReturn(new RegistroMovimientoResponse(null, BigDecimal.TEN, false));
    }

    @AfterEach
    void limpiar() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void crearQuedaEnBorradorYNoTocaElStock() {
        var r = service.crear(req(linea(200L, 12L, "5")));

        assertEquals(EstadoDespacho.BORRADOR, r.estado());
        assertEquals("DES-000001", r.consecutivo());
        assertEquals("Ferretería El Tornillo", r.cliente());
        verify(movimientos, never()).registrarSalidaVenta(anyLong(), anyLong(), any(), anyString(), anyLong());
        verify(auditoria).exito(eq(AccionAuditoria.DESPACHO_CREAR), eq(EntidadAuditada.DESPACHO), eq(5L), anyString());
    }

    @Test
    void confirmarConvierteADescontarEnLaUnidadDelProductoYEnOrdenDeProducto() {
        service.crear(req(linea(200L, 12L, "5"), linea(100L, 11L, "250"))); // 250 m de cable = 2.5 rollos
        when(movimientos.registrarSalidaVenta(eq(200L), anyLong(), any(), anyString(), anyLong()))
                .thenReturn(new RegistroMovimientoResponse(null, BigDecimal.ONE, true));

        var r = service.confirmar(5L);

        assertEquals(EstadoDespacho.CONFIRMADO, r.estado());
        assertNotNull(r.confirmadoEn());
        assertEquals(List.of("CEM-50"), r.productosBajoMinimo());
        InOrder orden = inOrder(movimientos);
        orden.verify(movimientos).registrarSalidaVenta(100L, 1L, new BigDecimal("2.5"), DespachoService.REFERENCIA, 5L);
        orden.verify(movimientos).registrarSalidaVenta(200L, 1L, new BigDecimal("5"), DespachoService.REFERENCIA, 5L);
        assertThrows(NegocioException.class, () -> service.confirmar(5L)); // ya no está en BORRADOR
    }

    @Test
    void sinStockSuficienteNoSeConfirma() {
        service.crear(req(linea(200L, 12L, "500")));
        when(movimientos.registrarSalidaVenta(eq(200L), anyLong(), any(), anyString(), anyLong()))
                .thenThrow(new NegocioException("STOCK_INSUFICIENTE", "Stock insuficiente de CEM-50"));

        assertEquals("STOCK_INSUFICIENTE",
                assertThrows(NegocioException.class, () -> service.confirmar(5L)).getCodigo());
        assertEquals(EstadoDespacho.BORRADOR, guardado.getEstado()); // la transacción se revierte completa
    }

    @Test
    void anularConfirmadoDevuelveElStockConMotivo() {
        service.crear(req(linea(200L, 12L, "5")));
        service.confirmar(5L);

        var r = service.anular(5L, "  El cliente devolvió la mercancía ");

        assertEquals(EstadoDespacho.ANULADO, r.estado());
        assertEquals("El cliente devolvió la mercancía", r.motivoAnulacion());
        assertEquals(7L, guardado.getAnuladoPorUsuarioId());
        verify(movimientos).registrarAnulacionDespacho(eq(200L), eq(1L), eq(new BigDecimal("5")),
                eq("Anulación DES-000001: El cliente devolvió la mercancía"), eq(DespachoService.REFERENCIA), eq(5L));
        assertThrows(NegocioException.class, () -> service.anular(5L, "otra vez"));
    }

    @Test
    void anularBorradorNoTocaElStock() {
        service.crear(req(linea(200L, 12L, "5")));

        assertEquals(EstadoDespacho.ANULADO, service.anular(5L, "Se armó por error").estado());
        verify(movimientos, never()).registrarAnulacionDespacho(anyLong(), anyLong(), any(), anyString(), anyString(),
                anyLong());
    }

    @Test
    void bodegaInactivaOUnidadIncompatibleSeRechazan() {
        when(unidadService.convertir(any(), eq(12L), eq(10L)))
                .thenThrow(new NegocioException("UNIDADES_INCOMPATIBLES", "BULTO no se convierte a ROLLO"));
        assertEquals("UNIDADES_INCOMPATIBLES",
                assertThrows(NegocioException.class, () -> service.crear(req(linea(100L, 12L, "1")))).getCodigo());

        principal.setActivo(false);
        assertEquals("BODEGA_INACTIVA",
                assertThrows(NegocioException.class, () -> service.crear(req(linea(200L, 12L, "1")))).getCodigo());
    }

    @Test
    void losTiposDelDespachoNoSePuedenRegistrarPorLaApiDeMovimientos() {
        assertFalse(TipoMovimiento.SALIDA_VENTA.esManual());
        assertFalse(TipoMovimiento.ANULACION_DESPACHO.esManual());
        assertTrue(TipoMovimiento.SALIDA_VENTA.usaOrigen());
        assertTrue(TipoMovimiento.ANULACION_DESPACHO.usaDestino());
        assertTrue(TipoMovimiento.ANULACION_DESPACHO.exigeMotivo());
    }

    // ----------------------------------------------------------------- apoyo

    private UnidadMedida unidad(Long id, String codigo) {
        UnidadMedida u = new UnidadMedida();
        u.setId(id);
        u.setCodigo(codigo);
        when(unidades.findById(id)).thenReturn(Optional.of(u));
        return u;
    }

    private void producto(Long id, String codigo, UnidadMedida unidad) {
        Producto p = new Producto();
        p.setId(id);
        p.setCodigo(codigo);
        p.setUnidadMedida(unidad);
        when(productos.findWithRelacionesById(id)).thenReturn(Optional.of(p));
    }

    private static LineaDespachoRequest linea(Long productoId, Long unidadId, String cantidad) {
        return new LineaDespachoRequest(productoId, unidadId, new BigDecimal(cantidad));
    }

    private static DespachoRequest req(LineaDespachoRequest... lineas) {
        return new DespachoRequest(1L, "Ferretería El Tornillo", "FAC-1234", null, List.of(lineas));
    }
}
