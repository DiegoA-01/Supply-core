package com.proyecto.supply_core.ia.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class NumeroTextoTest {

    @ParameterizedTest
    @CsvSource(delimiter = ';', value = {
            "$ 12.000,50;12000.50",
            "1,234.5;1234.5",
            "12.000;12000",
            "12,000;12000",
            "12,5;12.5",
            "0.75;0.75",
            "1.234.567;1234567",
            "19 %;19",
            "30 días;30",
            "COP 450;450" })
    void interpretaFormatosComunes(String texto, String esperado) {
        assertEquals(0, new BigDecimal(esperado).compareTo(NumeroTexto.leer(texto).orElseThrow()));
    }

    @Test
    void sinNumeroDevuelveVacio() {
        assertTrue(NumeroTexto.leer(null).isEmpty());
        assertTrue(NumeroTexto.leer("").isEmpty());
        assertTrue(NumeroTexto.leer("no aplica").isEmpty());
        assertTrue(NumeroTexto.leer("-").isEmpty());
    }

    @Test
    void confianzaYPorcentajeSeAcotan() {
        assertEquals(new BigDecimal("1.000"), ExtraccionService.confianza(1.7));
        assertEquals(new BigDecimal("0.000"), ExtraccionService.confianza(-0.2));
        assertEquals(new BigDecimal("0.000"), ExtraccionService.confianza(Double.NaN));
        assertEquals(BigDecimal.ZERO, ExtraccionService.porcentaje(""));
        assertEquals(new BigDecimal("19.00"), ExtraccionService.porcentaje("19%"));
        assertEquals(null, ExtraccionService.porcentaje("150"));
    }
}
