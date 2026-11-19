package com.proyecto.supply_core.venta.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.proyecto.supply_core.venta.dto.ListaPrecioResponse;
import com.proyecto.supply_core.venta.dto.PrecioFila;
import com.proyecto.supply_core.venta.dto.PreciosResponse;

/** Margen calculado en Java, con números que se pueden comprobar a mano. */
class CalculoMargenTest {

    private static void igual(String esperado, BigDecimal real) {
        assertEquals(0, new BigDecimal(esperado).compareTo(real), "esperado " + esperado + " y fue " + real);
    }

    @Test
    void conIvaIncluidoSeparaLaBaseYMideElMargenSobreElla() {
        // $11.900 con IVA 19 % incluido: base 10.000, IVA 1.900; costo 7.000 => margen 3.000 = 30 %
        CalculoMargen.Resultado r = CalculoMargen.calcular(new BigDecimal("11900"), true, new BigDecimal("19"),
                new BigDecimal("7000"));
        igual("10000.00", r.base());
        igual("1900.00", r.impuesto());
        igual("3000.00", r.margen());
        igual("30.0", r.margenPct());
    }

    @Test
    void sinIvaIncluidoElPrecioEsLaBase() {
        CalculoMargen.Resultado r = CalculoMargen.calcular(new BigDecimal("10000"), false, new BigDecimal("19"),
                new BigDecimal("8000"));
        igual("10000.00", r.base());
        igual("1900.00", r.impuesto());
        igual("20.0", r.margenPct());
    }

    @Test
    void venderBajoElCostoDaMargenNegativoYSinCostoNoHayMargen() {
        igual("-25.0", CalculoMargen.calcular(new BigDecimal("8000"), false, BigDecimal.ZERO, new BigDecimal("10000"))
                .margenPct());
        CalculoMargen.Resultado sinCosto = CalculoMargen.calcular(new BigDecimal("5000"), true, null, null);
        igual("5000.00", sinCosto.base());          // sin impuesto definido se toma 0 %
        assertNull(sinCosto.margen());
    }

    private static PrecioFila fila(String base, String margen, String pct, String stock) {
        return new PrecioFila(1L, "X", "X", "C", "UND", true, null, 1L, "IVA", BigDecimal.ZERO, true, BigDecimal.ONE,
                new BigDecimal(base), null, null, new BigDecimal(base), BigDecimal.ZERO, new BigDecimal(margen),
                new BigDecimal(pct), new BigDecimal(stock), List.of(), List.of());
    }

    @Test
    void elMargenGlobalSePonderaPorElInventario() {
        // A: base 100, margen 50 (50 %), 1 unidad; B: base 100, margen 10 (10 %), 9 unidades
        // promedio simple 30 %; ponderado (50 + 90) / (100 + 900) = 14 %
        PreciosResponse r = PreciosService.resumen(new ListaPrecioResponse(1L, "General", true, true),
                List.of(fila("100", "50", "50", "1"), fila("100", "10", "10", "9")), true);
        igual("30.0", r.margenPromedioPct());
        igual("14.0", r.margenPonderadoPct());
        igual("140.00", r.margenInventario());
        assertEquals(2, r.conPrecio());
    }
}
