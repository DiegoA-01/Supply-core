package com.proyecto.supply_core.compra.analisis.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class MotorComparacionTest {

    /** Pesos sembrados en V2. */
    private static final Map<String, BigDecimal> PESOS = Map.of("PRECIO", bd("35"), "CALIDAD", bd("20"),
            "ENTREGA", bd("15"), "GARANTIA", bd("10"), "HISTORIAL", bd("15"), "RIESGO", bd("5"));
    private static final LocalDate HOY = LocalDate.of(2026, 10, 6);

    @Test
    void ejemploCompletoCalculadoAMano() {
        // A: precio 450/und, 3 días, 12 meses, historial 90, calidad 80, cotiza los 2 productos
        var a = new MotorComparacion.Oferta(1L, "A", bd("25704"), 3, 12, null, bd("90"), bd("80"),
                Map.of(10L, bd("450"), 20L, bd("1000")));
        // B: precio 500/und, 1 día, 6 meses, sin historial, solo cotiza el producto 10
        var b = new MotorComparacion.Oferta(2L, "B", bd("20000"), 1, 6, null, null, null, Map.of(10L, bd("500")));

        var r = MotorComparacion.comparar(List.of(10L, 20L), List.of(a, b), PESOS, HOY);

        var primero = r.get(0);
        assertEquals(1L, primero.cotizacionId());
        assertEquals(1, primero.posicion());
        // A: PRECIO 100, ENTREGA (1+1)/(3+1)=50, GARANTIA 100, HISTORIAL 90, CALIDAD 80, RIESGO 100
        assertEquals(bd("100.00"), primero.puntajes().get("PRECIO"));
        assertEquals(bd("50.00"), primero.puntajes().get("ENTREGA"));
        // 35 + 7.5 + 10 + 13.5 + 16 + 5 = 87
        assertEquals(bd("87.00"), primero.scoreFinal());

        var segundo = r.get(1);
        // B: PRECIO (450/500*100 + 0)/2 = 45, ENTREGA 100, GARANTIA 50, HISTORIAL 50, CALIDAD 50, RIESGO 50
        assertEquals(bd("45.00"), segundo.puntajes().get("PRECIO"));
        assertEquals(bd("50.00"), segundo.puntajes().get("RIESGO"));
        // 15.75 + 15 + 5 + 7.5 + 10 + 2.5 = 55.75
        assertEquals(bd("55.75"), segundo.scoreFinal());
        assertTrue(segundo.anomalias().stream().anyMatch(x -> x.tipo().equals("COBERTURA_INCOMPLETA")));
        assertTrue(segundo.anomalias().stream().anyMatch(x -> x.tipo().equals("PROVEEDOR_SIN_HISTORIAL")));
    }

    @Test
    void sinPlazoNiGarantiaPuntuaCeroYAlertaPeroNoExcluye() {
        var a = new MotorComparacion.Oferta(1L, "A", bd("100"), null, null, HOY.minusDays(1), bd("70"), null,
                Map.of(10L, bd("10")));

        var r = MotorComparacion.comparar(List.of(10L), List.of(a), PESOS, HOY);

        assertEquals(1, r.size());
        assertEquals(BigDecimal.ZERO, r.get(0).puntajes().get("ENTREGA"));
        assertTrue(r.get(0).anomalias().stream().anyMatch(x -> x.tipo().equals("VALIDEZ_VENCIDA")
                && x.severidad().equals("ALTA")));
        assertTrue(r.get(0).anomalias().stream().anyMatch(x -> x.tipo().equals("SIN_PLAZO")));
    }

    @Test
    void precioAtipicoSoloConTresOMasOfertas() {
        var a = oferta(1L, "10");
        var b = oferta(2L, "11");
        var c = oferta(3L, "40"); // muy por encima de la mediana (11)

        var r = MotorComparacion.comparar(List.of(10L), List.of(a, b, c), PESOS, HOY);

        var caro = r.stream().filter(x -> x.cotizacionId() == 3L).findFirst().orElseThrow();
        assertTrue(caro.anomalias().stream().anyMatch(x -> x.tipo().equals("PRECIO_ATIPICO")));
        assertEquals(3, caro.posicion());

        var dos = MotorComparacion.comparar(List.of(10L), List.of(a, c), PESOS, HOY);
        assertTrue(dos.stream().flatMap(x -> x.anomalias().stream()).noneMatch(x -> x.tipo().equals("PRECIO_ATIPICO")));
    }

    @Test
    void empateSeDesempataPorMenorCosto() {
        var a = new MotorComparacion.Oferta(1L, "A", bd("200"), 1, 1, null, bd("50"), bd("50"), Map.of(10L, bd("10")));
        var b = new MotorComparacion.Oferta(2L, "B", bd("100"), 1, 1, null, bd("50"), bd("50"), Map.of(10L, bd("10")));

        var r = MotorComparacion.comparar(List.of(10L), List.of(a, b), PESOS, HOY);

        assertEquals(r.get(0).scoreFinal(), r.get(1).scoreFinal());
        assertEquals(2L, r.get(0).cotizacionId());
    }

    private static MotorComparacion.Oferta oferta(Long id, String precio) {
        return new MotorComparacion.Oferta(id, "P" + id, bd(precio), 1, 1, null, bd("50"), bd("50"),
                Map.of(10L, bd(precio)));
    }

    private static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }
}
