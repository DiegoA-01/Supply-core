package com.proyecto.supply_core.inventario.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
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
import com.proyecto.supply_core.common.exception.NegocioException;
import com.proyecto.supply_core.common.exception.NoEncontradoException;
import com.proyecto.supply_core.inventario.dto.MovimientoRequest;
import com.proyecto.supply_core.inventario.entity.Bodega;
import com.proyecto.supply_core.inventario.entity.MovimientoInventario;
import com.proyecto.supply_core.inventario.entity.Producto;
import com.proyecto.supply_core.inventario.entity.StockProductoBodega;
import com.proyecto.supply_core.inventario.enums.TipoMovimiento;
import com.proyecto.supply_core.inventario.repository.BodegaRepository;
import com.proyecto.supply_core.inventario.repository.MovimientoInventarioRepository;
import com.proyecto.supply_core.inventario.repository.ProductoRepository;
import com.proyecto.supply_core.inventario.repository.StockProductoBodegaRepository;
import com.proyecto.supply_core.notificacion.enums.EventoNotificacion;
import com.proyecto.supply_core.notificacion.service.NotificacionService;
import com.proyecto.supply_core.security.context.Roles;
import com.proyecto.supply_core.security.context.SesionActual;
import com.proyecto.supply_core.security.context.UsuarioAutenticado;

class MovimientoInventarioServiceTest {

    private final StockProductoBodegaRepository stockRepo = mock(StockProductoBodegaRepository.class);
    private final MovimientoInventarioRepository movRepo = mock(MovimientoInventarioRepository.class);
    private final ProductoRepository productoRepo = mock(ProductoRepository.class);
    private final BodegaRepository bodegaRepo = mock(BodegaRepository.class);
    private final AuditoriaService auditoria = mock(AuditoriaService.class);
    private final NotificacionService notificaciones = mock(NotificacionService.class);
    private final MovimientoInventarioService service =
            new MovimientoInventarioService(stockRepo, movRepo, productoRepo, bodegaRepo, auditoria, notificaciones);

    private Producto producto;
    private Bodega principal; // id 1
    private Bodega norte;     // id 2
    /** Stock simulado por id de bodega. */
    private final Map<Long, StockProductoBodega> stock = new HashMap<>();

    @BeforeEach
    void setUp() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setAttribute(SesionActual.ATRIBUTO,
                new UsuarioAutenticado(7L, "alm@x.com", "Almacén", Set.of("ALMACENISTA"), null));
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(req));

        UnidadMedida und = new UnidadMedida();
        und.setCodigo("UND");
        producto = new Producto();
        producto.setId(10L);
        producto.setCodigo("PAP-001");
        producto.setUnidadMedida(und);
        producto.setStockMinimo(new BigDecimal("5"));
        when(productoRepo.findWithRelacionesById(10L)).thenReturn(Optional.of(producto));

        principal = bodega(1L, "Principal");
        norte = bodega(2L, "Norte");
        fijarStock(principal, "10");
        fijarStock(norte, "0");

        when(stockRepo.bloquear(eq(10L), anyLong())).thenAnswer(inv -> Optional.of(stock.get(inv.getArgument(1, Long.class))));
        when(stockRepo.totalDeProducto(10L)).thenAnswer(inv -> stock.values().stream()
                .map(StockProductoBodega::getCantidad).reduce(BigDecimal.ZERO, BigDecimal::add));
        when(movRepo.save(any())).thenAnswer(inv -> {
            MovimientoInventario m = inv.getArgument(0);
            m.setId(100L);
            return m;
        });
    }

    @AfterEach
    void limpiar() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void salidaRestaYGuardaElSaldoResultante() {
        var r = service.registrar(req(TipoMovimiento.SALIDA_CONSUMO, 1L, null, "3", null));

        assertEquals(new BigDecimal("7.0000"), stock.get(1L).getCantidad());
        assertEquals(new BigDecimal("7.0000"), r.movimiento().stockResultante());
        assertEquals(7L, r.movimiento().usuarioId());
        assertFalse(r.bajoStockMinimo());
        verify(stockRepo).crearSiNoExiste(10L, 1L);
        verifyNoInteractions(auditoria); // las salidas no son ajustes
    }

    @Test
    void stockNuncaQuedaNegativo() {
        var ex = assertThrows(NegocioException.class,
                () -> service.registrar(req(TipoMovimiento.SALIDA_CONSUMO, 1L, null, "10.0001", null)));

        assertEquals("STOCK_INSUFICIENTE", ex.getCodigo());
        assertTrue(ex.getMessage().contains("disponible 10"));
        assertEquals(new BigDecimal("10.0000"), stock.get(1L).getCantidad());
        verify(movRepo, never()).save(any());
    }

    @Test
    void trasladoMueveEntreBodegasYGuardaAmbosSaldos() {
        var r = service.registrar(req(TipoMovimiento.TRASLADO, 1L, 2L, "4", null));

        assertEquals(new BigDecimal("6.0000"), stock.get(1L).getCantidad());
        assertEquals(new BigDecimal("4.0000"), stock.get(2L).getCantidad());
        assertEquals(new BigDecimal("6.0000"), r.movimiento().stockResultante());
        assertEquals(new BigDecimal("4.0000"), r.movimiento().stockResultanteDestino());
        assertEquals(new BigDecimal("10.0000"), r.stockTotalProducto()); // el total no cambia
    }

    @Test
    void trasladoBloqueaSiempreEnOrdenDeIdParaEvitarInterbloqueos() {
        fijarStock(norte, "5");
        service.registrar(req(TipoMovimiento.TRASLADO, 2L, 1L, "1", null)); // Norte(2) → Principal(1)

        InOrder orden = inOrder(stockRepo);
        orden.verify(stockRepo).bloquear(10L, 1L); // primero la de menor id aunque sea el destino
        orden.verify(stockRepo).bloquear(10L, 2L);
    }

    @Test
    void ajustePositivoSumaYSeAudita() {
        var r = service.registrar(req(TipoMovimiento.AJUSTE_POSITIVO, null, 2L, "2.5", "Conteo físico"));

        assertEquals(new BigDecimal("2.5000"), stock.get(2L).getCantidad());
        assertNull(r.movimiento().stockResultanteDestino());
        verify(auditoria).exito(eq(AccionAuditoria.INVENTARIO_AJUSTE), eq(EntidadAuditada.MOVIMIENTO_INVENTARIO),
                eq(100L), anyString());
    }

    @Test
    void avisaCuandoQuedaBajoElMinimo() {
        var r = service.registrar(req(TipoMovimiento.SALIDA_CONSUMO, 1L, null, "6", null)); // 10 → 4 < 5
        assertTrue(r.bajoStockMinimo());
        assertEquals(new BigDecimal("4.0000"), r.stockTotalProducto());
        verify(notificaciones).notificarRol(eq(Roles.COMPRADOR), eq(EventoNotificacion.STOCK_BAJO_MINIMO),
                anyString());

        // ya estaba bajo el mínimo: no se vuelve a avisar (4 → 3)
        service.registrar(req(TipoMovimiento.SALIDA_CONSUMO, 1L, null, "1", null));
        verify(notificaciones).notificarRol(any(), any(), anyString());
    }

    @Test
    void cruzoMinimoSoloAlPasarElUmbral() {
        BigDecimal min = new BigDecimal("5");
        assertTrue(MovimientoInventarioService.cruzoMinimo(TipoMovimiento.SALIDA_CONSUMO, new BigDecimal("1"),
                new BigDecimal("4"), min)); // 5 → 4
        assertFalse(MovimientoInventarioService.cruzoMinimo(TipoMovimiento.SALIDA_CONSUMO, new BigDecimal("1"),
                new BigDecimal("3"), min)); // 4 → 3, ya estaba bajo
        assertFalse(MovimientoInventarioService.cruzoMinimo(TipoMovimiento.TRASLADO, new BigDecimal("2"),
                new BigDecimal("3"), min)); // el total no cambia
        assertFalse(MovimientoInventarioService.cruzoMinimo(TipoMovimiento.AJUSTE_POSITIVO, new BigDecimal("1"),
                new BigDecimal("4"), min)); // 3 → 4, subió
    }

    @Test
    void productoOBodegaInactivosOInexistentesSeRechazan() {
        producto.setActivo(false);
        assertEquals("PRODUCTO_INACTIVO", assertThrows(NegocioException.class,
                () -> service.registrar(req(TipoMovimiento.SALIDA_CONSUMO, 1L, null, "1", null))).getCodigo());

        producto.setActivo(true);
        norte.setActivo(false);
        assertEquals("BODEGA_INACTIVA", assertThrows(NegocioException.class,
                () -> service.registrar(req(TipoMovimiento.TRASLADO, 1L, 2L, "1", null))).getCodigo());

        assertThrows(NoEncontradoException.class, () -> service.registrar(
                new MovimientoRequest(99L, TipoMovimiento.SALIDA_CONSUMO, 1L, null, BigDecimal.ONE, null)));
        verify(movRepo, never()).save(any());
    }

    @Test
    void entradaDeCompraYDevolucionSoloPorSusMetodos() {
        assertEquals("TIPO_NO_MANUAL", assertThrows(NegocioException.class,
                () -> service.registrar(req(TipoMovimiento.ENTRADA_COMPRA, null, 1L, "1", null))).getCodigo());

        var entrada = service.registrarEntradaCompra(10L, 2L, new BigDecimal("8"), "RECEPCION", 55L);
        assertEquals(TipoMovimiento.ENTRADA_COMPRA, entrada.movimiento().tipo());
        assertEquals("RECEPCION", entrada.movimiento().referenciaTipo());
        assertEquals(new BigDecimal("8.0000"), stock.get(2L).getCantidad());

        assertEquals("MOTIVO_OBLIGATORIO", assertThrows(NegocioException.class,
                () -> service.registrarDevolucionProveedor(10L, 2L, BigDecimal.ONE, " ", "RECEPCION", 55L)).getCodigo());
        service.registrarDevolucionProveedor(10L, 2L, new BigDecimal("3"), "Producto defectuoso", "RECEPCION", 55L);
        assertEquals(new BigDecimal("5.0000"), stock.get(2L).getCantidad());
    }

    private MovimientoRequest req(TipoMovimiento tipo, Long origen, Long destino, String cantidad, String motivo) {
        return new MovimientoRequest(10L, tipo, origen, destino, new BigDecimal(cantidad), motivo);
    }

    private Bodega bodega(Long id, String nombre) {
        Bodega b = new Bodega();
        b.setId(id);
        b.setNombre(nombre);
        when(bodegaRepo.findById(id)).thenReturn(Optional.of(b));
        return b;
    }

    private void fijarStock(Bodega b, String cantidad) {
        StockProductoBodega s = new StockProductoBodega();
        s.setProducto(producto);
        s.setBodega(b);
        s.setCantidad(new BigDecimal(cantidad).setScale(4));
        stock.put(b.getId(), s);
    }
}
