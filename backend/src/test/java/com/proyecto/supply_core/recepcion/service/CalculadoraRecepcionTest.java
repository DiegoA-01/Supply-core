package com.proyecto.supply_core.recepcion.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.proyecto.supply_core.proveedor.dto.IndicadoresEvaluacion;
import com.proyecto.supply_core.recepcion.service.CalculadoraRecepcion.Totales;

class CalculadoraRecepcionTest {

    private static BigDecimal n(String v) {
        return new BigDecimal(v);
    }

    @Test
    void loBuenoEntraHastaLoPedidoYLoQueSobraEsExcedente() {
        var r = CalculadoraRecepcion.repartir(n("10"), n("0"), n("12"), n("0"));

        assertEquals(0, n("10").compareTo(r.aceptada()));
        assertEquals(0, n("2").compareTo(r.excedente()));
        assertEquals(0, BigDecimal.ZERO.compareTo(r.faltante()));
    }

    @Test
    void loRechazadoNoEntraYElFaltanteSeCalculaSobreLoAceptado() {
        var r = CalculadoraRecepcion.repartir(n("10"), n("0"), n("8"), n("3"));

        assertEquals(0, n("5").compareTo(r.aceptada()));
        assertEquals(0, BigDecimal.ZERO.compareTo(r.excedente()));
        assertEquals(0, n("5").compareTo(r.faltante()));
    }

    @Test
    void unaSegundaRecepcionDescuentaLoYaAceptado() {
        var r = CalculadoraRecepcion.repartir(n("10"), n("6"), n("5"), n("0"));

        assertEquals(0, n("4").compareTo(r.aceptada()));
        assertEquals(0, n("1").compareTo(r.excedente()));
        assertEquals(0, BigDecimal.ZERO.compareTo(r.faltante()));
    }

    @Test
    void conLaLineaYaCompletaTodoLoBuenoEsExcedente() {
        var r = CalculadoraRecepcion.repartir(n("10"), n("10"), n("3"), n("0"));

        assertEquals(0, BigDecimal.ZERO.compareTo(r.aceptada()));
        assertEquals(0, n("3").compareTo(r.excedente()));
    }

    @Test
    void rechazarMasDeLoRecibidoEsUnError() {
        assertThrows(IllegalArgumentException.class,
                () -> CalculadoraRecepcion.repartir(n("10"), n("0"), n("2"), n("3")));
    }

    @Test
    void puntualidadPierdeDiezPuntosPorDiaDeAtrasoYNuncaBajaDeCero() {
        LocalDate esperada = LocalDate.of(2026, 10, 10);

        assertEquals(0, n("100").compareTo(CalculadoraRecepcion.puntualidad(esperada, esperada)));
        assertEquals(0, n("100").compareTo(CalculadoraRecepcion.puntualidad(esperada, esperada.minusDays(3))));
        assertEquals(0, n("70").compareTo(CalculadoraRecepcion.puntualidad(esperada, esperada.plusDays(3))));
        assertEquals(0, BigDecimal.ZERO.compareTo(CalculadoraRecepcion.puntualidad(esperada, esperada.plusDays(30))));
        assertNull(CalculadoraRecepcion.puntualidad(esperada, null));
    }

    @Test
    void indicadoresDeUnaEntregaPerfecta() {
        var lineas = List.of(new Totales(n("10"), n("10"), n("10"), n("0"), n("0")));

        IndicadoresEvaluacion i = CalculadoraRecepcion.indicadores(lineas, LocalDate.of(2026, 10, 10),
                LocalDate.of(2026, 10, 9));

        assertEquals(0, n("100").compareTo(i.puntualidad()));
        assertEquals(0, n("100").compareTo(i.cumplimientoCantidad()));
        assertEquals(0, n("100").compareTo(i.incidencias()));
        assertEquals(0, n("100").compareTo(i.calidad()));
        assertNull(i.precio());
        assertNull(i.respuesta());
    }

    @Test
    void indicadoresConRechazoYFaltante() {
        // línea 1: pidió 10, llegaron 10 y se rechazaron 5 (aceptó 5); línea 2: pidió 4 y llegó completo
        var lineas = List.of(new Totales(n("10"), n("5"), n("10"), n("5"), n("0")),
                new Totales(n("4"), n("4"), n("4"), n("0"), n("0")));

        IndicadoresEvaluacion i = CalculadoraRecepcion.indicadores(lineas, LocalDate.of(2026, 10, 10),
                LocalDate.of(2026, 10, 12));

        assertEquals(0, n("80").compareTo(i.puntualidad()));
        assertEquals(0, n("75").compareTo(i.cumplimientoCantidad()));   // (0,5 + 1) / 2
        assertEquals(0, n("50").compareTo(i.incidencias()));            // 1 de 2 líneas con incidencia
        assertEquals(0, n("75").compareTo(i.calidad()));                // (0,5 + 1) / 2
    }

    @Test
    void sinNadaRecibidoLaCalidadNoTieneDatos() {
        var lineas = List.of(new Totales(n("10"), n("0"), n("0"), n("0"), n("0")));

        IndicadoresEvaluacion i = CalculadoraRecepcion.indicadores(lineas, LocalDate.of(2026, 10, 10), null);

        assertNull(i.puntualidad());
        assertNull(i.calidad());
        assertEquals(0, BigDecimal.ZERO.compareTo(i.cumplimientoCantidad()));
    }
}
