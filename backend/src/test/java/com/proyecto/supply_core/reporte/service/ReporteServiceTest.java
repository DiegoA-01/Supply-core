package com.proyecto.supply_core.reporte.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.proyecto.supply_core.common.exception.NoEncontradoException;
import com.proyecto.supply_core.common.exception.ParametroInvalidoException;
import com.proyecto.supply_core.compra.orden.enums.EstadoOrden;
import com.proyecto.supply_core.proveedor.entity.Proveedor;
import com.proyecto.supply_core.proveedor.repository.ProveedorRepository;
import com.proyecto.supply_core.reporte.dto.EstadoCuentaResponse;
import com.proyecto.supply_core.reporte.dto.GastoFila;
import com.proyecto.supply_core.reporte.dto.GastoMesFila;
import com.proyecto.supply_core.reporte.dto.GastoResponse;
import com.proyecto.supply_core.reporte.dto.InventarioFila;
import com.proyecto.supply_core.reporte.dto.InventarioResumen;
import com.proyecto.supply_core.reporte.dto.OrdenFila;
import com.proyecto.supply_core.reporte.dto.ProveedorRanking;
import com.proyecto.supply_core.reporte.dto.SumaConsecutivo;
import com.proyecto.supply_core.reporte.repository.ReporteRepository;

class ReporteServiceTest {

    private final ReporteRepository reporteRepository = mock(ReporteRepository.class);
    private final ProveedorRepository proveedorRepository = mock(ProveedorRepository.class);
    private final ReporteService service = new ReporteService(reporteRepository, proveedorRepository);

    private static BigDecimal d(String valor) {
        return new BigDecimal(valor);
    }

    private static Proveedor proveedor(Long id, String nombre, String score) {
        Proveedor p = new Proveedor();
        p.setId(id);
        p.setRazonSocial(nombre);
        p.setNit("800-" + id);
        p.setScoreActual(score == null ? null : d(score));
        return p;
    }

    @Test
    void sinDatosDevuelveCerosYListasVaciasNoError() {
        when(reporteRepository.pagado(any(), any(), any(), any())).thenReturn(null);
        GastoResponse g = service.gasto(null, null, null, null);

        assertEquals(0, g.ordenes());
        assertEquals(0, g.total().signum());
        assertEquals(0, g.pagado().signum());
        assertTrue(g.porProveedor().isEmpty() && g.porCategoria().isEmpty() && g.porMes().isEmpty());
        assertTrue(service.inventario(null, false).items().isEmpty());
        assertTrue(service.proveedores(null, null, null).isEmpty());
    }

    @Test
    void gastoRedondeaYSumaLosTotalesEnJava() {
        when(reporteRepository.gastoPorProveedor(any(), any(), any(), any(), any(), any())).thenReturn(List.of(
                new GastoFila(1L, "Tech", 2L, d("1000.00"), d("190.0000")),
                new GastoFila(2L, "Cables", 1L, d("500.005"), d("0"))));
        when(reporteRepository.gastoPorMes(any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of(new GastoMesFila(2026, 3, 3L, d("1500.005"), d("190"))));
        when(reporteRepository.pagado(any(), any(), any(), any())).thenReturn(d("400"));

        GastoResponse g = service.gasto(null, null, null, null);

        assertEquals(3, g.ordenes());
        assertEquals(d("1500.01"), g.subtotal());
        assertEquals(d("190.00"), g.impuestos());
        assertEquals(d("1690.01"), g.total());
        assertEquals(d("400.00"), g.pagado());
        assertEquals("2026-03", g.porMes().get(0).periodo());
        assertEquals(d("1190.00"), g.porProveedor().get(0).total());
    }

    @Test
    void elFiltroHastaIncluyeTodoEseDiaYSeExcluyenLasOrdenesNoVigentes() {
        LocalDate desde = LocalDate.of(2026, 10, 1);
        LocalDate hasta = LocalDate.of(2026, 10, 31);

        service.gasto(desde, hasta, 5L, 7L);

        verify(reporteRepository).gastoPorProveedor(eq(ReporteService.NO_VIGENTES), eq(LocalDateTime.of(2026, 10, 1, 0, 0)),
                eq(LocalDateTime.of(2026, 11, 1, 0, 0)), eq(5L), eq(7L), isNull());
        assertTrue(ReporteService.NO_VIGENTES.containsAll(
                List.of(EstadoOrden.REEMPLAZADA, EstadoOrden.CANCELADA, EstadoOrden.RECHAZADA_PROVEEDOR)));
    }

    @Test
    void conFiltroDeCategoriaNoSeInformaLoPagadoPorqueUnPagoNoTieneCategoria() {
        GastoResponse g = service.gasto(null, null, 5L, null);

        assertNull(g.pagado());
        verify(reporteRepository, org.mockito.Mockito.never()).pagado(any(), any(), any(), any());
    }

    @Test
    void rangoInvertidoSeRechaza() {
        LocalDate hoy = LocalDate.now();
        assertThrows(ParametroInvalidoException.class, () -> service.gasto(hoy, hoy.minusDays(1), null, null));
        assertThrows(ParametroInvalidoException.class, () -> service.proveedores(hoy, hoy.minusDays(1), null));
        assertThrows(ParametroInvalidoException.class, () -> service.estadoCuenta(1L, hoy, hoy.minusDays(1)));
    }

    @Test
    void inventarioCalculaValorYAlertaBajoMinimo() {
        when(reporteRepository.inventario(isNull(), isNull())).thenReturn(List.of(
                new InventarioFila(1L, "P-1", "Cable", "Eléctricos", "UND", d("5"), d("10"), null, d("100")),
                new InventarioFila(2L, "P-2", "Tubo", "Eléctricos", "UND", d("20"), d("10"), d("50"), null),
                new InventarioFila(3L, "P-3", "Nuevo", "Eléctricos", "UND", d("0"), d("0"), null, d("7"))));

        InventarioResumen todo = service.inventario(null, false);
        assertEquals(3, todo.productos());
        assertEquals(1, todo.bajoMinimo());
        assertEquals(d("500.00"), todo.items().get(0).valor());
        assertNull(todo.items().get(1).valor());
        assertEquals(d("500.00"), todo.valorTotal());

        InventarioResumen soloBajos = service.inventario(null, true);
        assertEquals(1, soloBajos.productos());
        assertEquals("Cable", soloBajos.items().get(0).nombre());
    }

    @Test
    void rankingPorScoreConLosSinScoreAlFinal() {
        when(reporteRepository.gastoPorProveedor(any(), any(), any(), any(), any(), any())).thenReturn(List.of(
                new GastoFila(1L, "Sin score", 1L, d("9000"), d("0")),
                new GastoFila(2L, "Regular", 1L, d("100"), d("0")),
                new GastoFila(3L, "Mejor", 4L, d("50"), d("0"))));
        when(proveedorRepository.findAllById(any())).thenReturn(List.of(proveedor(1L, "Sin score", null),
                proveedor(2L, "Regular", "70.00"), proveedor(3L, "Mejor", "95.50")));

        List<ProveedorRanking> ranking = service.proveedores(null, null, null);

        assertEquals(List.of("Mejor", "Regular", "Sin score"), ranking.stream().map(ProveedorRanking::razonSocial).toList());
        assertEquals(List.of(1, 2, 3), ranking.stream().map(ProveedorRanking::posicion).toList());
        assertNull(ranking.get(2).score());
        assertEquals(4, ranking.get(0).ordenes());
    }

    @Test
    void estadoCuentaUsaLaFacturaSiExisteYSinoElTotalDeLaOrden() {
        when(proveedorRepository.findById(9L)).thenReturn(Optional.of(proveedor(9L, "Tech", "80")));
        when(reporteRepository.ordenesDelProveedor(eq(9L), any(), any(), any())).thenReturn(List.of(
                new OrdenFila(12L, "OC-000002", LocalDateTime.of(2026, 10, 5, 9, 0), EstadoOrden.DESPACHADA, d("2000")),
                new OrdenFila(11L, "OC-000001", LocalDateTime.of(2026, 10, 1, 9, 0), EstadoOrden.CERRADA, d("1000"))));
        when(reporteRepository.pagadoPorOrden(9L)).thenReturn(List.of(new SumaConsecutivo("OC-000001", d("1190")),
                new SumaConsecutivo("OC-000002", d("800"))));
        when(reporteRepository.facturadoPorOrden(9L)).thenReturn(List.of(new SumaConsecutivo("OC-000001", d("1190"))));

        EstadoCuentaResponse e = service.estadoCuenta(9L, null, null);

        assertEquals(d("0.00"), e.ordenes().get(1).saldo());   // OC-000001: factura 1190 − pagado 1190
        assertEquals(d("1200.00"), e.ordenes().get(0).saldo()); // OC-000002: sin factura, 2000 − 800
        assertEquals(d("3000.00"), e.totalOrdenado());
        assertEquals(d("1190.00"), e.totalFacturado());
        assertEquals(d("1990.00"), e.totalPagado());
        assertEquals(d("1200.00"), e.saldo());
    }

    @Test
    void estadoCuentaDeProveedorAjenoEsNoEncontrado() {
        when(proveedorRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(NoEncontradoException.class, () -> service.estadoCuenta(99L, null, null));
    }
}
