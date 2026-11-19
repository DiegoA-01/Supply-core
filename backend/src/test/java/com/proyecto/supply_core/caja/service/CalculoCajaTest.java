package com.proyecto.supply_core.caja.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.Test;

import com.proyecto.supply_core.caja.entity.MovimientoCaja;
import com.proyecto.supply_core.caja.enums.MedioPago;
import com.proyecto.supply_core.caja.enums.TipoMovimientoCaja;

/** Cuentas de la caja: esperado, diferencias, tolerancia y tope. */
class CalculoCajaTest {

    private static MovimientoCaja mov(TipoMovimientoCaja tipo, MedioPago medio, String monto) {
        MovimientoCaja m = new MovimientoCaja();
        m.setTipo(tipo);
        m.setMedioPago(medio);
        m.setMonto(new BigDecimal(monto));
        return m;
    }

    private static BigDecimal d(String v) {
        return new BigDecimal(v);
    }

    @Test
    void elEsperadoEsBaseMasVentasMenosRetirosYGastosPorMedio() {
        List<MovimientoCaja> libro = List.of(
                mov(TipoMovimientoCaja.BASE, MedioPago.EFECTIVO, "50000"),
                mov(TipoMovimientoCaja.VENTA, MedioPago.EFECTIVO, "120000"),
                mov(TipoMovimientoCaja.VENTA, MedioPago.TARJETA_DEBITO, "80000"),
                mov(TipoMovimientoCaja.RETIRO, MedioPago.EFECTIVO, "-100000"),
                mov(TipoMovimientoCaja.GASTO, MedioPago.EFECTIVO, "-15000"),
                mov(TipoMovimientoCaja.DEVOLUCION, MedioPago.EFECTIVO, "-5000"));
        Map<MedioPago, BigDecimal> esperado = CalculoCaja.esperado(libro);
        assertEquals(d("50000.00"), esperado.get(MedioPago.EFECTIVO));
        assertEquals(d("80000.00"), esperado.get(MedioPago.TARJETA_DEBITO));
        assertEquals(d("0.00"), esperado.get(MedioPago.TRANSFERENCIA));     // sin movimientos = 0
    }

    @Test
    void laDiferenciaEsContadoMenosEsperadoYLaToleranciaMiraCadaMedio() {
        Map<MedioPago, BigDecimal> esperado = CalculoCaja.esperado(List.of(
                mov(TipoMovimientoCaja.BASE, MedioPago.EFECTIVO, "50000"),
                mov(TipoMovimientoCaja.VENTA, MedioPago.TARJETA_CREDITO, "30000")));
        // faltan 1.500 en efectivo (dentro de la tolerancia) y sobran 3.000 en tarjeta crédito (fuera)
        Map<MedioPago, BigDecimal> contado = CalculoCaja.contado(d("48500"),
                Map.of(MedioPago.TARJETA_CREDITO, d("33000"), MedioPago.EFECTIVO, d("999999")));
        assertEquals(d("48500.00"), contado.get(MedioPago.EFECTIVO));     // el efectivo de "otros" se ignora
        Map<MedioPago, BigDecimal> dif = CalculoCaja.diferencias(contado, esperado);
        assertEquals(d("-1500.00"), dif.get(MedioPago.EFECTIVO));
        assertEquals(d("3000.00"), dif.get(MedioPago.TARJETA_CREDITO));
        assertEquals(d("1500.00"), CalculoCaja.total(dif));
        assertTrue(CalculoCaja.requiereAprobacion(dif, d("2000")));
        assertFalse(CalculoCaja.requiereAprobacion(dif, d("3000")));     // justo en la tolerancia no pide aprobación
    }

    @Test
    void elTopeAvisaRetirarSoloSiSePasa() {
        assertTrue(CalculoCaja.debeRetirar(d("500001"), d("500000")));
        assertFalse(CalculoCaja.debeRetirar(d("500000"), d("500000")));
        assertFalse(CalculoCaja.debeRetirar(d("9999999"), BigDecimal.ZERO)); // tope 0 = sin aviso
    }

    @Test
    void paraMuchosLibrosAlAzarLaCajaEsperadaEsLaSumaDeSusMovimientos() {
        Random azar = new Random(20261119L);
        for (int i = 0; i < 300; i++) {
            List<MovimientoCaja> libro = new ArrayList<>();
            BigDecimal efectivo = BigDecimal.ZERO;
            for (int j = 0; j < 1 + azar.nextInt(40); j++) {
                MedioPago medio = MedioPago.values()[azar.nextInt(MedioPago.values().length)];
                BigDecimal monto = BigDecimal.valueOf(azar.nextInt(2_000_000) - 600_000, 2);
                if (monto.signum() == 0) {
                    continue;
                }
                libro.add(mov(TipoMovimientoCaja.VENTA, medio, monto.toPlainString()));
                if (medio == MedioPago.EFECTIVO) {
                    efectivo = efectivo.add(monto);
                }
            }
            Map<MedioPago, BigDecimal> esperado = CalculoCaja.esperado(libro);
            assertEquals(0, efectivo.compareTo(esperado.get(MedioPago.EFECTIVO)));
            // contar exactamente lo esperado nunca deja diferencia
            Map<MedioPago, BigDecimal> dif = CalculoCaja.diferencias(
                    CalculoCaja.contado(esperado.get(MedioPago.EFECTIVO), esperado), esperado);
            assertEquals(0, CalculoCaja.total(dif).signum());
        }
    }
}
