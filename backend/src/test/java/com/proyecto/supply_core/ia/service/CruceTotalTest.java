package com.proyecto.supply_core.ia.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

class CruceTotalTest {

    @Test
    void sinTotalImpresoNoHayNadaQueCruzarYSeConsideraQueCuadra() {
        for (String vacio : new String[] { null, "", "   ", "sin total", "0", "-5" }) {
            var r = CruceTotal.evaluar(new BigDecimal("1000"), vacio);

            assertFalse(r.hayTotal(), "no debería haber total en: " + vacio);
            assertTrue(r.cuadra());
            assertNull(r.impreso());
        }
    }

    @Test
    void iguales() {
        var r = CruceTotal.evaluar(new BigDecimal("5236000.00"), "$ 5.236.000");

        assertTrue(r.hayTotal());
        assertTrue(r.cuadra());
        assertEquals(new BigDecimal("0.00"), r.diferenciaPct());
    }

    @Test
    void laToleranciaEsElUnoPorCientoDelTotalImpresoEnAmbosSentidos() {
        assertTrue(CruceTotal.evaluar(new BigDecimal("990"), "1000").cuadra());   // justo 1,00 %
        assertTrue(CruceTotal.evaluar(new BigDecimal("1010"), "1000").cuadra());
        assertFalse(CruceTotal.evaluar(new BigDecimal("989"), "1000").cuadra());  // 1,10 %
        assertFalse(CruceTotal.evaluar(new BigDecimal("1011"), "1000").cuadra());
        assertEquals(new BigDecimal("1.10"), CruceTotal.evaluar(new BigDecimal("989"), "1000").diferenciaPct());
    }

    @Test
    void sinLineasElCalculadoEsCeroYNoCuadraConUnTotalImpreso() {
        var r = CruceTotal.evaluar(null, "1.234,50");

        assertTrue(r.hayTotal());
        assertFalse(r.cuadra());
        assertEquals(new BigDecimal("100.00"), r.diferenciaPct());
        assertEquals(BigDecimal.ZERO, r.calculado());
    }

    @Test
    void elMensajeLlevaElCodigoDeLaAlertaYLasDosCifras() {
        String mensaje = CruceTotal.evaluar(new BigDecimal("4760000"), "5.236.000").mensaje();

        assertTrue(mensaje.startsWith(CruceTotal.ALERTA));
        assertTrue(mensaje.contains("5236000.00"));
        assertTrue(mensaje.contains("4760000.00"));
        assertTrue(mensaje.contains("9.09 %"));
    }

    @Test
    void entiendeLosFormatosDeNumeroDeLasCotizaciones() {
        assertTrue(CruceTotal.evaluar(new BigDecimal("12000.50"), "$ 12.000,50").cuadra());
        assertTrue(CruceTotal.evaluar(new BigDecimal("12000.50"), "COP 12,000.50").cuadra());
    }
}
