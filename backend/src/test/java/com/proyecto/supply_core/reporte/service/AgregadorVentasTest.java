package com.proyecto.supply_core.reporte.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

import com.proyecto.supply_core.reporte.dto.VentaReporte;
import com.proyecto.supply_core.reporte.enums.AgrupacionVenta;

/** Suma del reporte de ventas netas (spec 009): devoluciones, margen, agrupaciones y propiedades de los totales. */
class AgregadorVentasTest {

    private static final LocalDate LUNES = LocalDate.of(2026, 11, 16);

    /**
     * @param texto número
     * @return decimal
     */
    private static BigDecimal n(String texto) {
        return new BigDecimal(texto);
    }

    /**
     * @param venta      venta
     * @param fecha      día
     * @param producto   producto
     * @param cajero     cajero
     * @param vendedor   vendedor ({@code null} = venta directa)
     * @param unidades   unidades
     * @param total      con IVA
     * @param base       sin IVA
     * @param costo      costo ({@code null} = sin costo)
     * @param devolucion si resta
     * @return línea
     */
    private static AgregadorVentas.Linea linea(long venta, LocalDate fecha, long producto, long cajero, Long vendedor,
            String unidades, String total, String base, String costo, boolean devolucion) {
        return new AgregadorVentas.Linea(venta, fecha, 1L, "Caja 1", cajero, "Cajero " + cajero, vendedor,
                vendedor == null ? null : "Vendedor " + vendedor, 9L, "Cliente", producto, "P-" + producto, producto % 2,
                "Categoría " + (producto % 2), n(unidades), n(total), n(base), costo == null ? null : n(costo), devolucion);
    }

    @Test
    void laDevolucionRestaVentaBaseYCostoYElMargenSaleDeLaBaseNeta() {
        List<AgregadorVentas.Linea> lineas = List.of(
                linea(1, LUNES, 7, 1, null, "3", "35700", "30000", "18000", false),
                linea(1, LUNES, 7, 1, null, "1", "11900", "10000", "6000", true));
        VentaReporte r = AgregadorVentas.armar(LUNES, LUNES, AgrupacionVenta.PRODUCTO, lineas, List.of(), n("0"), false);
        VentaReporte.Fila f = r.filas().get(0);
        assertEquals(n("23800.00"), f.neto());
        assertEquals(n("2.0000"), f.unidades());
        assertEquals(n("20000.00"), f.baseNeta());
        assertEquals(n("12000.00"), f.costo());
        assertEquals(n("8000.00"), f.margen());
        assertEquals(n("40.00"), f.margenPct());
        assertEquals(1, f.ventas());
        assertEquals(n("23800.00"), r.totales().ticketPromedio());
    }

    @Test
    void sinCostoLaLineaNoEntraAlMargenPeroSiALaVenta() {
        List<AgregadorVentas.Linea> lineas = List.of(
                linea(1, LUNES, 7, 1, null, "1", "11900", "10000", "6000", false),
                linea(2, LUNES, 8, 1, null, "1", "5950", "5000", null, false));
        VentaReporte.Totales t = AgregadorVentas.armar(LUNES, LUNES, AgrupacionVenta.DIA, lineas, List.of(), n("0"), false)
                .totales();
        assertEquals(n("17850.00"), t.neto());
        assertEquals(n("4000.00"), t.margen());
        assertEquals(n("40.00"), t.margenPct());
        assertEquals(n("5000.00"), t.baseSinCosto());
        assertNull(AgregadorVentas.armar(LUNES, LUNES, AgrupacionVenta.DIA, List.of(), List.of(), null, false)
                .totales().ticketPromedio(), "sin ventas no hay ticket promedio");
    }

    @Test
    void semanaYMesAgrupanPorCalendarioYLaVentaDirectaTieneNombre() {
        List<AgregadorVentas.Linea> lineas = List.of(
                linea(1, LUNES.plusDays(6), 7, 1, null, "1", "100", "84", "50", false),
                linea(2, LUNES.plusDays(7), 7, 1, 5L, "1", "100", "84", "50", false));
        List<VentaReporte.Fila> semanas = AgregadorVentas.armar(LUNES, LUNES.plusDays(7), AgrupacionVenta.SEMANA, lineas,
                List.of(), n("0"), false).filas();
        assertEquals(List.of("2026-11-16", "2026-11-23"), semanas.stream().map(VentaReporte.Fila::clave).toList());
        assertEquals(1, AgregadorVentas.armar(LUNES, LUNES.plusDays(7), AgrupacionVenta.MES, lineas, List.of(), n("0"), false)
                .filas().size());
        List<VentaReporte.Fila> vendedores = AgregadorVentas.armar(LUNES, LUNES.plusDays(7), AgrupacionVenta.VENDEDOR, lineas,
                List.of(), n("0"), false).filas();
        assertEquals(2, vendedores.size());
        assertEquals(true, vendedores.stream().anyMatch(f -> AgregadorVentas.VENTA_DIRECTA.equals(f.nombre())));
    }

    @Test
    void porMedioDePagoRestaLoReembolsadoYSumaLoCobrado() {
        List<AgregadorVentas.Cobro> cobros = List.of(
                new AgregadorVentas.Cobro(1, LUNES, "EFECTIVO", "Efectivo", n("20000"), false),
                new AgregadorVentas.Cobro(2, LUNES, "TARJETA_CREDITO", "Tarjeta crédito", n("30000"), false),
                new AgregadorVentas.Cobro(2, LUNES, "TARJETA_CREDITO", "Tarjeta crédito", n("10000"), true));
        List<VentaReporte.Fila> filas = AgregadorVentas.armar(LUNES, LUNES, AgrupacionVenta.MEDIO_PAGO, List.of(), cobros,
                n("0"), false).filas();
        assertEquals(n("20000.00"), filas.stream().filter(f -> f.clave().equals("TARJETA_CREDITO")).findFirst().orElseThrow().neto());
        assertEquals(n("40000.00"), filas.stream().map(VentaReporte.Fila::neto).reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    /** Propiedad: con datos al azar, la suma de los renglones es la misma en cualquier agrupación. */
    @Test
    void cualquierAgrupacionSumaLoMismo() {
        Random azar = new Random(9);
        for (int caso = 0; caso < 200; caso++) {
            List<AgregadorVentas.Linea> lineas = new ArrayList<>();
            int n = 1 + azar.nextInt(15);
            for (int i = 0; i < n; i++) {
                long base = 100 + azar.nextInt(100_000);
                lineas.add(linea(1 + azar.nextInt(6), LUNES.plusDays(azar.nextInt(40)), 1 + azar.nextInt(5),
                        1 + azar.nextInt(3), azar.nextBoolean() ? null : (long) (1 + azar.nextInt(2)),
                        String.valueOf(1 + azar.nextInt(9)), BigDecimal.valueOf(base * 119, 2).toPlainString(),
                        BigDecimal.valueOf(base, 0).toPlainString(), azar.nextInt(4) == 0 ? null : String.valueOf(base / 2),
                        azar.nextInt(5) == 0));
            }
            BigDecimal neto = null;
            for (AgrupacionVenta a : AgrupacionVenta.values()) {
                if (a == AgrupacionVenta.MEDIO_PAGO) {
                    continue;
                }
                VentaReporte r = AgregadorVentas.armar(LUNES, LUNES.plusDays(40), a, lineas, List.of(), n("20"), false);
                BigDecimal suma = r.filas().stream().map(VentaReporte.Fila::neto).reduce(BigDecimal.ZERO, BigDecimal::add);
                BigDecimal margen = r.filas().stream().map(VentaReporte.Fila::margen).reduce(BigDecimal.ZERO, BigDecimal::add);
                assertEquals(0, suma.add(n("20")).compareTo(r.totales().neto()), a + " suma el neto (más el redondeo)");
                assertEquals(0, margen.compareTo(r.totales().margen()), a + " suma el margen");
                if (neto == null) {
                    neto = r.totales().neto();
                }
                assertEquals(0, neto.compareTo(r.totales().neto()), "el total no depende de la agrupación");
            }
        }
    }
}
