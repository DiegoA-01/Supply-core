package com.proyecto.supply_core.factura.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.proyecto.supply_core.factura.enums.EstadoFactura;
import com.proyecto.supply_core.factura.service.CalculadoraFactura.LineaCruce;

class CalculadoraFacturaTest {

    private static BigDecimal n(String v) {
        return new BigDecimal(v);
    }

    @Test
    void esperadoEsLoAceptadoPorElPrecioMasElImpuestoDeCadaLinea() {
        var esperado = CalculadoraFactura.esperado(List.of(
                new LineaCruce(n("5"), n("100"), n("19")),       // neto 500, IVA 95
                new LineaCruce(n("2.5"), n("40.3333"), n("0")))); // neto 100.83, sin IVA

        assertEquals(0, n("600.83").compareTo(esperado.subtotal()));
        assertEquals(0, n("95.00").compareTo(esperado.impuestos()));
        assertEquals(0, n("695.83").compareTo(esperado.total()));
    }

    @Test
    void loNoAceptadoNoSeFactura() {
        var esperado = CalculadoraFactura.esperado(List.of(new LineaCruce(BigDecimal.ZERO, n("100"), n("19"))));

        assertEquals(0, BigDecimal.ZERO.compareTo(esperado.total()));
    }

    @Test
    void cuadraDentroDelPorcentajeDeTolerancia() {
        var justo = CalculadoraFactura.evaluar(n("1010.00"), n("1000.00"), n("1"), n("0"));
        var pasado = CalculadoraFactura.evaluar(n("1010.01"), n("1000.00"), n("1"), n("0"));
        var pordebajo = CalculadoraFactura.evaluar(n("990.00"), n("1000.00"), n("1"), n("0"));

        assertEquals(EstadoFactura.CUADRADA, justo.estado());
        assertEquals(EstadoFactura.EN_REVISION, pasado.estado());
        assertEquals(EstadoFactura.CUADRADA, pordebajo.estado());
        assertEquals(0, n("10.00").compareTo(justo.diferencia()));
        assertEquals(0, n("-10.00").compareTo(pordebajo.diferencia()));
    }

    @Test
    void tambienCuadraDentroDelMontoFijoAunqueElPorcentajeNoAlcance() {
        // 0,1 % de $ 10.000 son $ 10, pero la empresa acepta hasta $ 500 de diferencia
        var conMonto = CalculadoraFactura.evaluar(n("10400.00"), n("10000.00"), n("0.1"), n("500"));
        var sinMonto = CalculadoraFactura.evaluar(n("10400.00"), n("10000.00"), n("0.1"), n("0"));

        assertEquals(EstadoFactura.CUADRADA, conMonto.estado());
        assertEquals(EstadoFactura.EN_REVISION, sinMonto.estado());
    }

    @Test
    void sinToleranciaSoloCuadraLoExacto() {
        assertEquals(EstadoFactura.CUADRADA, CalculadoraFactura.evaluar(n("100.00"), n("100.00"), n("0"), n("0")).estado());
        assertEquals(EstadoFactura.EN_REVISION, CalculadoraFactura.evaluar(n("100.01"), n("100.00"), n("0"), n("0")).estado());
    }

    @Test
    void sinNadaEsperadoNuncaCuadra() {
        assertEquals(EstadoFactura.EN_REVISION, CalculadoraFactura.evaluar(n("100.00"), BigDecimal.ZERO, n("100"), n("1000000")).estado());
    }
}
