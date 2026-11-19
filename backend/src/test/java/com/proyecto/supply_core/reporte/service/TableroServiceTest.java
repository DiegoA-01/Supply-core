package com.proyecto.supply_core.reporte.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.proyecto.supply_core.reorden.dto.ReabastecimientoResponse;
import com.proyecto.supply_core.reorden.service.ReabastecimientoService;
import com.proyecto.supply_core.reporte.dto.ClaseRotacion;
import com.proyecto.supply_core.reporte.dto.GastoMes;
import com.proyecto.supply_core.reporte.dto.GastoResponse;
import com.proyecto.supply_core.reporte.dto.InventarioResumen;
import com.proyecto.supply_core.reporte.dto.RotacionItem;
import com.proyecto.supply_core.reporte.dto.RotacionResponse;
import com.proyecto.supply_core.reporte.dto.RotacionResumen;
import com.proyecto.supply_core.reporte.dto.TableroResponse;

/** Tablero del dueño: meses completos, variación del mes y listas de más y menos movidos. */
class TableroServiceTest {

    private static final LocalDate HOY = LocalDate.of(2026, 11, 18);

    private static GastoMes mes(String periodo, String total) {
        return new GastoMes(periodo, 1, new BigDecimal(total), BigDecimal.ZERO, new BigDecimal(total));
    }

    private static RotacionItem item(long id, String salidas, ClaseRotacion clase, String valor) {
        return new RotacionItem(id, "P-" + id, "Producto " + id, "Cat", "UND", BigDecimal.TEN, BigDecimal.ZERO,
                new BigDecimal(salidas), BigDecimal.ZERO, BigDecimal.ZERO, null, null, null, null,
                valor == null ? null : new BigDecimal(valor), clase, false);
    }

    @Test
    void completaLosDoceMesesConCeros() {
        List<GastoMes> meses = TableroService.doceMeses(List.of(mes("2026-11", "300"), mes("2026-01", "50")),
                YearMonth.of(2026, 11));
        assertEquals(12, meses.size());
        assertEquals("2025-12", meses.get(0).periodo());
        assertEquals(0, BigDecimal.ZERO.compareTo(meses.get(0).total()));
        assertEquals("2026-11", meses.get(11).periodo());
        assertEquals(0, new BigDecimal("300").compareTo(meses.get(11).total()));
    }

    @Test
    void calculaVariacionPorPagarYListas() {
        ReporteService reportes = mock(ReporteService.class);
        RotacionService rotacion = mock(RotacionService.class);
        ReabastecimientoService plan = mock(ReabastecimientoService.class);
        when(reportes.gasto(any(), any(), isNull(), isNull(), isNull())).thenReturn(new GastoResponse(HOY.minusMonths(11), HOY, 4,
                new BigDecimal("1000"), BigDecimal.ZERO, new BigDecimal("1000"), new BigDecimal("400"), List.of(),
                List.of(), List.of(mes("2026-10", "200"), mes("2026-11", "300"))));
        when(reportes.inventario(null, false)).thenReturn(new InventarioResumen(3, 1, new BigDecimal("900"), List.of()));
        when(rotacion.rotacion(null, HOY, null, null)).thenReturn(new RotacionResponse(HOY.minusDays(89), HOY, 90,
                new RotacionResumen(3, 2, 1, 0, 0, new BigDecimal("900"), new BigDecimal("500"), 30, 90, 15),
                List.of(item(1, "5", ClaseRotacion.MEDIA, "100"), item(2, "50", ClaseRotacion.ALTA, "300"),
                        item(3, "0", ClaseRotacion.SIN_MOVIMIENTO, "500"))));
        when(plan.plan(null)).thenReturn(new ReabastecimientoResponse(HOY, 90, 5, 2, 30, 0, 0, 0, 3, 0, List.of()));

        TableroResponse t = new TableroService(reportes, rotacion, plan,
                Clock.fixed(HOY.atStartOfDay(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault())).tablero();

        assertEquals(0, new BigDecimal("600").compareTo(t.porPagarDoceMeses()));
        assertEquals(0, new BigDecimal("50.0").compareTo(t.variacionMesPct()));   // de 200 a 300
        assertEquals(2L, t.masMovidos().get(0).productoId());                      // el que más salió primero
        assertEquals(2, t.masMovidos().size());                                    // el quieto no está
        assertEquals(3L, t.menosMovidos().get(0).productoId());
        assertEquals(1L, t.productosPorClase().get(ClaseRotacion.SIN_MOVIMIENTO));
        assertEquals(0L, t.productosPorClase().get(ClaseRotacion.AGOTADO));
        assertEquals(1, t.bajoMinimo());
    }

    @Test
    void porSedePideTodoFiltradoYNoArmaLaComparacionEntreSedes() {
        ReporteService reportes = mock(ReporteService.class);
        RotacionService rotacion = mock(RotacionService.class);
        ReabastecimientoService plan = mock(ReabastecimientoService.class);
        when(reportes.gasto(any(), any(), isNull(), isNull(), org.mockito.ArgumentMatchers.eq(4L))).thenReturn(
                new GastoResponse(HOY.minusMonths(11), HOY, 0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                        BigDecimal.ZERO, List.of(), List.of(), List.of()));
        when(reportes.inventario(null, false)).thenReturn(new InventarioResumen(0, 0, BigDecimal.ZERO, List.of()));
        when(rotacion.rotacion(null, HOY, null, 4L)).thenReturn(new RotacionResponse(HOY.minusDays(89), HOY, 90,
                new RotacionResumen(0, 0, 0, 0, 0, BigDecimal.ZERO, BigDecimal.ZERO, 30, 90, 15), List.of()));
        when(plan.plan(4L)).thenReturn(new ReabastecimientoResponse(HOY, 90, 5, 2, 30, 0, 0, 0, 0, 0, List.of()));

        TableroResponse t = new TableroService(reportes, rotacion, plan,
                Clock.fixed(HOY.atStartOfDay(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault())).tablero(4L);

        assertEquals(4L, t.sedeId());
        assertEquals(List.of(), t.gastoPorSede());
        org.mockito.Mockito.verify(reportes, org.mockito.Mockito.never()).gastoPorSede(any(), any());
    }
}
