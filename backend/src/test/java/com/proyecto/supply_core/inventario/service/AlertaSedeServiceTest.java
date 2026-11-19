package com.proyecto.supply_core.inventario.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.proyecto.supply_core.empresa.entity.Sede;
import com.proyecto.supply_core.inventario.dto.StockSede;
import com.proyecto.supply_core.inventario.entity.Bodega;
import com.proyecto.supply_core.inventario.entity.Producto;
import com.proyecto.supply_core.inventario.entity.ProductoSede;
import com.proyecto.supply_core.inventario.repository.ProductoSedeRepository;
import com.proyecto.supply_core.inventario.repository.StockProductoBodegaRepository;
import com.proyecto.supply_core.notificacion.enums.EventoNotificacion;
import com.proyecto.supply_core.notificacion.service.NotificacionService;

/** Aviso inmediato por sede: al bajar del mínimo propio o al quedarse en cero, una sola vez. */
class AlertaSedeServiceTest {

    private StockProductoBodegaRepository stock;
    private ProductoSedeRepository limites;
    private NotificacionService notificaciones;
    private AlertaSedeService service;
    private Producto producto;
    private Bodega centro;
    private Bodega norte;

    private static Sede sede(long id, String nombre) {
        Sede s = new Sede();
        s.setId(id);
        s.setNombre(nombre);
        return s;
    }

    private static Bodega bodega(Sede sede) {
        Bodega b = new Bodega();
        b.setSede(sede);
        return b;
    }

    @BeforeEach
    void setUp() {
        stock = mock(StockProductoBodegaRepository.class);
        limites = mock(ProductoSedeRepository.class);
        notificaciones = mock(NotificacionService.class);
        service = new AlertaSedeService(stock, limites, notificaciones);
        producto = new Producto();
        producto.setId(7L);
        producto.setCodigo("CAB-012");
        producto.setNombre("Cable");
        centro = bodega(sede(1L, "Sede Centro"));
        norte = bodega(sede(2L, "Sede Norte"));
        when(stock.stockPorSede(7L)).thenReturn(List.of(new StockSede(1L, "Sede Centro", BigDecimal.ZERO),
                new StockSede(2L, "Sede Norte", new BigDecimal("40"))));
    }

    @Test
    void avisaCuandoLaSedeSeQuedaEnCeroYDiceDondeHay() {
        when(stock.totalDeProductoEnSede(7L, 1L)).thenReturn(BigDecimal.ZERO);
        when(limites.deProductoEnSede(7L, 1L)).thenReturn(Optional.empty());

        assertTrue(service.revisar(producto, centro, null, new BigDecimal("5")));
        verify(notificaciones).notificarRol(eq("COMPRADOR"), eq(EventoNotificacion.STOCK_BAJO_SEDE),
                argThat(m -> m.startsWith("Sede Centro: CAB-012") && m.contains("Sede Norte tiene 40")));
        verify(notificaciones).notificarRol(eq("ALMACENISTA"), eq(EventoNotificacion.STOCK_BAJO_SEDE), anyString());
    }

    @Test
    void conMinimoPropioAvisaSoloAlCruzarlo() {
        ProductoSede ps = new ProductoSede();
        ps.setStockMinimo(new BigDecimal("20"));
        when(limites.deProductoEnSede(7L, 1L)).thenReturn(Optional.of(ps));
        when(stock.totalDeProductoEnSede(7L, 1L)).thenReturn(new BigDecimal("15"));

        assertTrue(service.revisar(producto, centro, null, new BigDecimal("10")));   // de 25 a 15: cruzó
        assertFalse(service.revisar(producto, centro, null, new BigDecimal("2")));   // de 17 a 15: ya estaba bajo
    }

    @Test
    void unTrasladoDentroDeLaMismaSedeNoAvisa() {
        assertFalse(service.revisar(producto, centro, bodega(centro.getSede()), new BigDecimal("5")));
        verify(notificaciones, never()).notificarRol(any(), any(), any());
    }

    @Test
    void sinSedeNoHayNadaQueRevisar() {
        assertFalse(service.revisar(producto, new Bodega(), null, BigDecimal.ONE));
        assertFalse(service.revisar(producto, null, norte, BigDecimal.ONE));
    }
}
