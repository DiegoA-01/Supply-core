package com.proyecto.supply_core.reporte.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.proyecto.supply_core.common.exception.ParametroInvalidoException;
import com.proyecto.supply_core.inventario.enums.TipoMovimiento;
import com.proyecto.supply_core.reporte.dto.ClaseRotacion;
import com.proyecto.supply_core.reporte.dto.InventarioFila;
import com.proyecto.supply_core.reporte.dto.RotacionItem;
import com.proyecto.supply_core.reporte.dto.RotacionMovimiento;
import com.proyecto.supply_core.reporte.dto.RotacionResponse;
import com.proyecto.supply_core.reporte.dto.RotacionUltima;
import com.proyecto.supply_core.reporte.repository.ReporteRepository;

class RotacionServiceTest {

    private static final ZoneId ZONA = ZoneId.of("America/Bogota");
    /** Hoy es 2026-11-17 al mediodía. */
    private final Clock reloj = Clock.fixed(Instant.parse("2026-11-17T17:00:00Z"), ZONA);
    private final ReporteRepository repo = mock(ReporteRepository.class);
    private final RotacionService service = new RotacionService(repo, reloj);

    private static BigDecimal d(String v) {
        return new BigDecimal(v);
    }

    private static InventarioFila producto(long id, String nombre, String stock, String costo) {
        return new InventarioFila(id, "P" + id, nombre, "Eléctrico", "und", d(stock), d("5"), null,
                costo == null ? null : d(costo));
    }

    private static RotacionMovimiento mov(long id, TipoMovimiento tipo, String cantidad) {
        return new RotacionMovimiento(id, tipo, d(cantidad));
    }

    private RotacionItem item(RotacionResponse r, long id) {
        return r.items().stream().filter(i -> i.productoId() == id).findFirst().orElseThrow();
    }

    private void dado(List<InventarioFila> productos, List<RotacionMovimiento> movimientos, List<RotacionUltima> ultimas) {
        when(repo.inventario(any(), any())).thenReturn(productos);
        when(repo.movimientosPorProducto(any(), any(), any(), any())).thenReturn(movimientos);
        when(repo.ultimaSalida(any(), any())).thenReturn(ultimas);
    }

    @Test
    void porDefectoMiraLosUltimos90DiasHastaHoy() {
        dado(List.of(), List.of(), List.of());

        RotacionResponse r = service.rotacion(null, null, null);

        assertEquals(LocalDate.of(2026, 11, 17), r.hasta());
        assertEquals(LocalDate.of(2026, 8, 20), r.desde());
        assertEquals(90, r.dias());
        assertTrue(r.items().isEmpty());
        assertEquals(0, r.resumen().productos());
    }

    @Test
    void sinSalidasNiStockNoHayNadaQueAnalizar() {
        dado(List.of(producto(1, "Vacío", "0", null)), List.of(), List.of());

        RotacionItem i = item(service.rotacion(null, null, null), 1);

        assertEquals(ClaseRotacion.SIN_ACTIVIDAD, i.clase());
        assertNull(i.diasCobertura());
        assertNull(i.diasSinSalida());
        assertFalse(i.porAgotarse());
        assertNull(i.fechaEstimadaAgotamiento());
    }

    @Test
    void conStockYSinSalidasEsDineroParado() {
        dado(List.of(producto(1, "Parado", "10", "1000.50")), List.of(),
                List.of(new RotacionUltima(1L, LocalDateTime.of(2026, 5, 10, 9, 0))));

        RotacionResponse r = service.rotacion(null, null, null);
        RotacionItem i = item(r, 1);

        assertEquals(ClaseRotacion.SIN_MOVIMIENTO, i.clase());
        assertEquals(191L, i.diasSinSalida());                  // del 10 de mayo al 17 de noviembre
        assertEquals(d("10005.00"), i.valorStock());
        assertEquals(1, r.resumen().sinMovimiento());
        assertEquals(d("10005.00"), r.resumen().valorSinMovimiento());
    }

    @Test
    void lasSalidasSeSumanYLosDespachosAnuladosSeRestan() {
        dado(List.of(producto(1, "Cable", "100", null)),
                List.of(mov(1, TipoMovimiento.SALIDA_CONSUMO, "30"), mov(1, TipoMovimiento.SALIDA_VENTA, "20"),
                        mov(1, TipoMovimiento.ANULACION_DESPACHO, "5"), mov(1, TipoMovimiento.ENTRADA_COMPRA, "60"),
                        mov(1, TipoMovimiento.DEVOLUCION_PROVEEDOR, "10")),
                List.of());

        RotacionItem i = item(service.rotacion(LocalDate.of(2026, 10, 19), LocalDate.of(2026, 11, 17), null), 1);

        assertEquals(0, d("45").compareTo(i.salidas()));
        assertEquals(0, d("50").compareTo(i.comprado()));
        assertEquals(d("1.5000"), i.consumoDiario());           // 45 en 30 días
    }

    @Test
    void losDiasQueAlcanzaElStockSalenDelRitmoDelPeriodo() {
        // 30 días, salen 60: 2 al día. Con 40 de stock alcanza 20 días.
        dado(List.of(producto(1, "Tornillo", "40", null)), List.of(mov(1, TipoMovimiento.SALIDA_CONSUMO, "60")), List.of());

        RotacionItem i = item(service.rotacion(LocalDate.of(2026, 10, 19), LocalDate.of(2026, 11, 17), null), 1);

        assertEquals(d("20.0"), i.diasCobertura());
        assertEquals(ClaseRotacion.ALTA, i.clase());
        assertFalse(i.porAgotarse());                            // 20 días no es "por agotarse" (límite 15)
    }

    @Test
    void conPocosDiasDeStockSeMarcaPorAgotarse() {
        dado(List.of(producto(1, "Cemento", "10", null)), List.of(mov(1, TipoMovimiento.SALIDA_VENTA, "30")), List.of());

        RotacionItem i = item(service.rotacion(LocalDate.of(2026, 10, 19), LocalDate.of(2026, 11, 17), null), 1);

        assertEquals(d("10.0"), i.diasCobertura());
        assertTrue(i.porAgotarse());
        assertEquals(LocalDate.of(2026, 11, 27), i.fechaEstimadaAgotamiento());   // hoy + 10 días
    }

    @Test
    void conMuchoStockFrenteAlConsumoRotaLento() {
        dado(List.of(producto(1, "Pintura", "400", null)), List.of(mov(1, TipoMovimiento.SALIDA_CONSUMO, "30")), List.of());

        RotacionItem i = item(service.rotacion(LocalDate.of(2026, 10, 19), LocalDate.of(2026, 11, 17), null), 1);

        assertEquals(d("400.0"), i.diasCobertura());
        assertEquals(ClaseRotacion.BAJA, i.clase());
    }

    @Test
    void entre30Y90DiasEsRotacionMedia() {
        dado(List.of(producto(1, "Guantes", "60", null)), List.of(mov(1, TipoMovimiento.SALIDA_CONSUMO, "30")), List.of());

        assertEquals(ClaseRotacion.MEDIA,
                item(service.rotacion(LocalDate.of(2026, 10, 19), LocalDate.of(2026, 11, 17), null), 1).clase());
    }

    @Test
    void siSeVendioYYaNoQuedaEsAgotado() {
        dado(List.of(producto(1, "Breaker", "0", null)), List.of(mov(1, TipoMovimiento.SALIDA_VENTA, "12")), List.of());

        RotacionResponse r = service.rotacion(null, null, null);
        RotacionItem i = item(r, 1);

        assertEquals(ClaseRotacion.AGOTADO, i.clase());
        assertEquals(d("0.0"), i.diasCobertura());
        assertFalse(i.porAgotarse());
        assertEquals(1, r.resumen().agotados());
    }

    @Test
    void siSeAnulaTodoLoDespachadoNoCuentaComoSalida() {
        dado(List.of(producto(1, "Lámpara", "8", null)),
                List.of(mov(1, TipoMovimiento.SALIDA_VENTA, "3"), mov(1, TipoMovimiento.ANULACION_DESPACHO, "5")), List.of());

        RotacionItem i = item(service.rotacion(null, null, null), 1);

        assertEquals(0, BigDecimal.ZERO.compareTo(i.salidas()));
        assertEquals(ClaseRotacion.SIN_MOVIMIENTO, i.clase());
    }

    @Test
    void lasVentasAnuladasYLoQueDevuelveElClienteNoCuentanComoSalida() {
        dado(List.of(producto(1, "Cable", "50", null)),
                List.of(mov(1, TipoMovimiento.SALIDA_VENTA, "10"), mov(1, TipoMovimiento.ANULACION_VENTA, "4"),
                        mov(1, TipoMovimiento.DEVOLUCION_CLIENTE, "1")),
                List.of());

        RotacionItem i = item(service.rotacion(LocalDate.of(2026, 10, 19), LocalDate.of(2026, 11, 17), null), 1);

        assertEquals(0, d("5").compareTo(i.salidas()));
    }

    @Test
    void unaSalidaMinimaNoDividePorCero() {
        dado(List.of(producto(1, "Arandela", "5", null)), List.of(mov(1, TipoMovimiento.SALIDA_CONSUMO, "0.0001")), List.of());

        RotacionItem i = item(service.rotacion(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 11, 17), null), 1);

        assertEquals(ClaseRotacion.BAJA, i.clase());
        assertTrue(i.diasCobertura().compareTo(d("1000")) > 0);
    }

    @Test
    void losProductosSalenDeMasAMenosSalidasYElResumenCuenta() {
        dado(List.of(producto(1, "Beta", "100", "10"), producto(2, "Alfa", "100", "10"), producto(3, "Gama", "100", "10")),
                List.of(mov(2, TipoMovimiento.SALIDA_CONSUMO, "50"), mov(3, TipoMovimiento.SALIDA_CONSUMO, "50"),
                        mov(1, TipoMovimiento.SALIDA_CONSUMO, "5")),
                List.of());

        RotacionResponse r = service.rotacion(null, null, null);

        assertEquals(List.of("Alfa", "Gama", "Beta"), r.items().stream().map(RotacionItem::nombre).toList()); // empate por nombre
        assertEquals(3, r.resumen().conSalidas());
        assertEquals(d("3000.00"), r.resumen().valorInventario());
        assertEquals(30, r.resumen().diasRotacionAlta());
        assertEquals(90, r.resumen().diasRotacionBaja());
        assertEquals(15, r.resumen().diasPorAgotarse());
    }

    @Test
    void elRangoInvertidoOMuyLargoSeRechaza() {
        assertThrows(ParametroInvalidoException.class,
                () -> service.rotacion(LocalDate.of(2026, 11, 17), LocalDate.of(2026, 11, 1), null));
        assertThrows(ParametroInvalidoException.class,
                () -> service.rotacion(LocalDate.of(2025, 1, 1), LocalDate.of(2026, 11, 17), null));
    }
}
