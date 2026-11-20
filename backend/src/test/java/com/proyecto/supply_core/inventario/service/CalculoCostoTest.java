package com.proyecto.supply_core.inventario.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

/** Costo promedio ponderado (spec 008), sin base de datos. */
class CalculoCostoTest {

    /**
     * @param v texto
     * @return número
     */
    private static BigDecimal n(String v) {
        return new BigDecimal(v);
    }

    /** Sin existencia o sin costo previo, el promedio es el costo de la compra. */
    @Test
    void sinExistenciaElCostoEsElDeLaCompra() {
        assertEquals(n("120.0000"), CalculoCosto.promedio(BigDecimal.ZERO, n("90"), n("10"), n("120")));
        assertEquals(n("120.0000"), CalculoCosto.promedio(n("5"), null, n("10"), n("120")));
    }

    /** Ponderado por cantidades, igual que a mano. */
    @Test
    void ponderaPorLasCantidades() {
        // (10 × 80 + 10 × 100) ÷ 20 = 90
        assertEquals(n("90.0000"), CalculoCosto.promedio(n("10"), n("80"), n("10"), n("100")));
        // (20 × 90 + 10 × 120) ÷ 30 = 100; luego (30 × 100 + 10 × 120) ÷ 40 = 105
        BigDecimal primero = CalculoCosto.promedio(n("20"), n("90"), n("10"), n("120"));
        assertEquals(n("100.0000"), primero);
        assertEquals(n("105.0000"), CalculoCosto.promedio(n("30"), primero, n("10"), n("120")));
    }

    /** Con decimales redondea a 4 cifras, mitad hacia arriba. */
    @Test
    void redondeaACuatroDecimales() {
        // (3 × 1000 + 1 × 1001) ÷ 4 = 1000.25
        assertEquals(n("1000.2500"), CalculoCosto.promedio(n("3"), n("1000"), n("1"), n("1001")));
        // (2 × 10 + 1 × 11) ÷ 3 = 10.33333…
        assertEquals(n("10.3333"), CalculoCosto.promedio(n("2"), n("10"), n("1"), n("11")));
    }
}
