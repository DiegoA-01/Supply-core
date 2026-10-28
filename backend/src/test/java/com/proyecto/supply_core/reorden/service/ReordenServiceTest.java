package com.proyecto.supply_core.reorden.service;

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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.proyecto.supply_core.auditoria.enums.AccionAuditoria;
import com.proyecto.supply_core.auditoria.enums.EntidadAuditada;
import com.proyecto.supply_core.auditoria.service.AuditoriaService;
import com.proyecto.supply_core.catalogo.entity.UnidadMedida;
import com.proyecto.supply_core.catalogo.service.UnidadMedidaService;
import com.proyecto.supply_core.common.exception.NegocioException;
import com.proyecto.supply_core.compra.solicitud.dto.SolicitudRequest;
import com.proyecto.supply_core.compra.solicitud.dto.SolicitudResponse;
import com.proyecto.supply_core.compra.solicitud.entity.SolicitudCompra;
import com.proyecto.supply_core.compra.solicitud.entity.SolicitudCompraLinea;
import com.proyecto.supply_core.compra.solicitud.service.SolicitudService;
import com.proyecto.supply_core.contrato.entity.Contrato;
import com.proyecto.supply_core.contrato.entity.ContratoPrecio;
import com.proyecto.supply_core.contrato.repository.ContratoRepository;
import com.proyecto.supply_core.inventario.entity.Producto;
import com.proyecto.supply_core.inventario.repository.ProductoRepository;
import com.proyecto.supply_core.inventario.repository.StockProductoBodegaRepository;
import com.proyecto.supply_core.inventario.repository.StockProductoBodegaRepository.TotalPorProducto;
import com.proyecto.supply_core.proveedor.entity.Proveedor;
import com.proyecto.supply_core.reorden.dto.BorradorReordenRequest;
import com.proyecto.supply_core.reorden.dto.CantidadPorProducto;
import com.proyecto.supply_core.reorden.repository.ReordenRepository;

class ReordenServiceTest {

    private final ProductoRepository productos = mock(ProductoRepository.class);
    private final StockProductoBodegaRepository stock = mock(StockProductoBodegaRepository.class);
    private final ReordenRepository reorden = mock(ReordenRepository.class);
    private final ContratoRepository contratos = mock(ContratoRepository.class);
    private final UnidadMedidaService unidades = mock(UnidadMedidaService.class);
    private final SolicitudService solicitudService = mock(SolicitudService.class);
    private final AuditoriaService auditoria = mock(AuditoriaService.class);
    private final ReordenService service = new ReordenService(productos, stock, reorden, contratos, unidades,
            solicitudService, auditoria);

    private UnidadMedida und;
    private UnidadMedida caja;

    @BeforeEach
    void setUp() {
        und = unidad(1L, "UND");
        caja = unidad(2L, "CAJA24");
        when(unidades.convertir(any(), eq(1L), eq(1L))).thenAnswer(i -> i.getArgument(0));
        when(unidades.convertir(any(), eq(2L), eq(1L)))
                .thenAnswer(i -> i.<BigDecimal>getArgument(0).multiply(BigDecimal.valueOf(24)));
        when(stock.totalesDe(any())).thenReturn(List.of());
        when(reorden.pedidoEnOrdenes(any(), any())).thenReturn(List.of());
        when(reorden.aceptadoEnOrdenes(any(), any())).thenReturn(List.of());
        when(reorden.lineasDeSolicitudes(any(), any())).thenReturn(List.of());
        when(contratos.vigentesConProductos(any(), any())).thenReturn(List.of());
    }

    @Test
    void sinProductosBajoElMinimoNoHaySugerencias() {
        when(productos.bajoStockMinimo()).thenReturn(List.of());

        assertTrue(service.sugerencias().isEmpty());
        verify(stock, never()).totalesDe(any());
    }

    @Test
    void laCantidadEsElMaximoMenosElStockMenosLoQueVieneEnCamino() {
        Producto cable = producto(100L, "CAB-1", "Cable", "10", "100");
        when(productos.bajoStockMinimo()).thenReturn(List.of(cable));
        when(stock.totalesDe(any())).thenReturn(List.of(total(100L, "20")));
        when(reorden.pedidoEnOrdenes(any(), any())).thenReturn(List.of(new CantidadPorProducto(100L, new BigDecimal("50"))));
        when(reorden.aceptadoEnOrdenes(any(), any())).thenReturn(List.of(new CantidadPorProducto(100L, new BigDecimal("10"))));
        // 1 caja de 24 unidades en una solicitud en curso
        when(reorden.lineasDeSolicitudes(any(), any())).thenReturn(List.of(linea(cable, caja, "1")));

        var s = service.sugerencias().get(0);

        assertEquals(new BigDecimal("20"), s.stockActual());
        assertEquals(new BigDecimal("40"), s.enOrdenes()); // 50 pedidas - 10 aceptadas
        assertEquals(new BigDecimal("24"), s.enSolicitudes()); // 1 caja x 24
        assertEquals(new BigDecimal("16.0000"), s.cantidadSugerida()); // 100 - 20 - 40 - 24
        assertFalse(s.yaCubierto());
        assertFalse(s.sinMaximo());
        assertEquals("UND", s.unidad());
    }

    @Test
    void loRechazadoNoSeAceptoYSigueContandoComoPendiente() {
        Producto cable = producto(100L, "CAB-1", "Cable", "10", "100");
        when(productos.bajoStockMinimo()).thenReturn(List.of(cable));
        when(reorden.pedidoEnOrdenes(any(), any())).thenReturn(List.of(new CantidadPorProducto(100L, new BigDecimal("50"))));
        when(reorden.aceptadoEnOrdenes(any(), any())).thenReturn(List.of()); // llegó todo pero se rechazó

        var s = service.sugerencias().get(0);

        assertEquals(new BigDecimal("50"), s.enOrdenes());
        assertEquals(new BigDecimal("50.0000"), s.cantidadSugerida());
    }

    @Test
    void siSeAceptoMasDeLoPedidoNoQuedaNadaPorRecibirNiNegativo() {
        Producto cable = producto(100L, "CAB-1", "Cable", "10", "100");
        when(productos.bajoStockMinimo()).thenReturn(List.of(cable));
        when(reorden.pedidoEnOrdenes(any(), any())).thenReturn(List.of(new CantidadPorProducto(100L, new BigDecimal("50"))));
        when(reorden.aceptadoEnOrdenes(any(), any())).thenReturn(List.of(new CantidadPorProducto(100L, new BigDecimal("55"))));

        assertEquals(new BigDecimal("0"), service.sugerencias().get(0).enOrdenes());
    }

    @Test
    void siLoQueVieneEnCaminoYaAlcanzaElMaximoLaCantidadEsCeroYQuedaCubierto() {
        Producto cable = producto(100L, "CAB-1", "Cable", "10", "100");
        when(productos.bajoStockMinimo()).thenReturn(List.of(cable));
        when(stock.totalesDe(any())).thenReturn(List.of(total(100L, "5")));
        when(reorden.pedidoEnOrdenes(any(), any())).thenReturn(List.of(new CantidadPorProducto(100L, new BigDecimal("120"))));

        var s = service.sugerencias().get(0);

        assertEquals(0, s.cantidadSugerida().signum());
        assertTrue(s.yaCubierto());
    }

    @Test
    void sinStockMaximoNoSePuedeCalcularLaCantidad() {
        Producto sinMax = producto(100L, "CAB-1", "Cable", "10", null);
        when(productos.bajoStockMinimo()).thenReturn(List.of(sinMax));

        var s = service.sugerencias().get(0);

        assertNull(s.cantidadSugerida());
        assertTrue(s.sinMaximo());
        assertFalse(s.yaCubierto());
    }

    @Test
    void laPistaDeContratoEsElDeMenorPrecioVigente() {
        Producto cable = producto(100L, "CAB-1", "Cable", "10", "100");
        when(productos.bajoStockMinimo()).thenReturn(List.of(cable));
        when(contratos.vigentesConProductos(any(), any())).thenReturn(List.of(
                contrato(1L, "CT-1", 40L, "Caro SAS", precio(cable, "1500"), precio(producto(999L, "OTRO", "Otro", "1", "2"), "1")),
                contrato(2L, "CT-2", 41L, "Barato SAS", precio(cable, "1200"))));

        var pista = service.sugerencias().get(0).contratoSugerido();

        assertEquals(2L, pista.contratoId());
        assertEquals("Barato SAS", pista.proveedorRazonSocial());
        assertEquals(new BigDecimal("1200"), pista.precioUnitario());
    }

    @Test
    void elBorradorLlevaLasCantidadesSugeridasEnLaUnidadDelProductoYQuedaAuditado() {
        Producto cable = producto(100L, "CAB-1", "Cable", "10", "100");
        Producto tubo = producto(101L, "TUB-1", "Tubo", "10", "50");
        when(productos.bajoStockMinimo()).thenReturn(List.of(cable, tubo));
        when(stock.totalesDe(any())).thenReturn(List.of(total(100L, "20"), total(101L, "10")));
        when(solicitudService.crear(any())).thenAnswer(i -> borrador());

        var r = service.crearBorrador(new BorradorReordenRequest(LocalDate.now().plusDays(5), null));

        ArgumentCaptor<SolicitudRequest> captor = ArgumentCaptor.forClass(SolicitudRequest.class);
        verify(solicitudService).crear(captor.capture());
        SolicitudRequest pedido = captor.getValue();
        assertEquals(ReordenService.MOTIVO_BORRADOR, pedido.motivo());
        assertEquals(LocalDate.now().plusDays(5), pedido.fechaRequerida());
        assertEquals(List.of(100L, 101L), pedido.lineas().stream().map(l -> l.productoId()).toList());
        assertEquals(List.of(1L, 1L), pedido.lineas().stream().map(l -> l.unidadMedidaId()).toList());
        assertEquals(new BigDecimal("80.0000"), pedido.lineas().get(0).cantidad());
        assertEquals(new BigDecimal("40.0000"), pedido.lineas().get(1).cantidad());
        assertEquals("SOL-000001", r.consecutivo());
        verify(auditoria).exito(eq(AccionAuditoria.REORDEN_GENERAR), eq(EntidadAuditada.SOLICITUD_COMPRA), eq(7L),
                anyString());
    }

    @Test
    void elBorradorPuedeLimitarseAAlgunosProductos() {
        Producto cable = producto(100L, "CAB-1", "Cable", "10", "100");
        Producto tubo = producto(101L, "TUB-1", "Tubo", "10", "50");
        when(productos.bajoStockMinimo()).thenReturn(List.of(cable, tubo));
        when(solicitudService.crear(any())).thenAnswer(i -> borrador());

        service.crearBorrador(new BorradorReordenRequest(LocalDate.now(), List.of(101L)));

        ArgumentCaptor<SolicitudRequest> captor = ArgumentCaptor.forClass(SolicitudRequest.class);
        verify(solicitudService).crear(captor.capture());
        assertEquals(List.of(101L), captor.getValue().lineas().stream().map(l -> l.productoId()).toList());
    }

    @Test
    void noSeCreaBorradorSiNoHayNadaQuePedirOUnProductoPedidoNoTieneSugerencia() {
        when(productos.bajoStockMinimo()).thenReturn(List.of());
        assertEquals("SIN_SUGERENCIAS", assertThrows(NegocioException.class,
                () -> service.crearBorrador(new BorradorReordenRequest(LocalDate.now(), null))).getCodigo());

        Producto cubierto = producto(100L, "CAB-1", "Cable", "10", "100");
        Producto sinMax = producto(101L, "TUB-1", "Tubo", "10", null);
        when(productos.bajoStockMinimo()).thenReturn(List.of(cubierto, sinMax));
        when(stock.totalesDe(any())).thenReturn(List.of(total(100L, "100")));
        assertEquals("SIN_SUGERENCIAS", assertThrows(NegocioException.class,
                () -> service.crearBorrador(new BorradorReordenRequest(LocalDate.now(), null))).getCodigo());
        var ex = assertThrows(NegocioException.class,
                () -> service.crearBorrador(new BorradorReordenRequest(LocalDate.now(), List.of(100L, 101L, 555L))));
        assertEquals("PRODUCTO_SIN_SUGERENCIA", ex.getCodigo());
        assertTrue(ex.getMessage().contains("555"));
        verify(solicitudService, never()).crear(any());
    }

    // ----------------------------------------------------------------- apoyo

    private static UnidadMedida unidad(Long id, String codigo) {
        UnidadMedida u = new UnidadMedida();
        u.setId(id);
        u.setCodigo(codigo);
        return u;
    }

    private Producto producto(Long id, String codigo, String nombre, String minimo, String maximo) {
        Producto p = new Producto();
        p.setId(id);
        p.setCodigo(codigo);
        p.setNombre(nombre);
        p.setUnidadMedida(und);
        p.setStockMinimo(new BigDecimal(minimo));
        p.setStockMaximo(maximo == null ? null : new BigDecimal(maximo));
        return p;
    }

    private static TotalPorProducto total(Long productoId, String cantidad) {
        return new TotalPorProducto() {
            @Override
            public Long getProductoId() {
                return productoId;
            }

            @Override
            public BigDecimal getTotal() {
                return new BigDecimal(cantidad);
            }
        };
    }

    private static SolicitudCompraLinea linea(Producto producto, UnidadMedida unidad, String cantidad) {
        SolicitudCompraLinea l = new SolicitudCompraLinea();
        l.setProducto(producto);
        l.setUnidadMedida(unidad);
        l.setCantidad(new BigDecimal(cantidad));
        return l;
    }

    private static ContratoPrecio precio(Producto producto, String valor) {
        ContratoPrecio p = new ContratoPrecio();
        p.setProducto(producto);
        p.setPrecioUnitario(new BigDecimal(valor));
        return p;
    }

    private static Contrato contrato(Long id, String numero, Long proveedorId, String razon, ContratoPrecio... precios) {
        Proveedor prov = new Proveedor();
        prov.setId(proveedorId);
        prov.setRazonSocial(razon);
        Contrato c = new Contrato();
        c.setId(id);
        c.setNumero(numero);
        c.setProveedor(prov);
        for (ContratoPrecio p : precios) {
            c.agregarPrecio(p);
        }
        return c;
    }

    private static SolicitudResponse borrador() {
        SolicitudCompra s = new SolicitudCompra();
        s.setId(7L);
        s.setConsecutivo("SOL-000001");
        s.setFechaRequerida(LocalDate.now());
        return SolicitudResponse.of(s, List.of());
    }
}
