package com.proyecto.supply_core.venta.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.Test;

import com.proyecto.supply_core.common.exception.NegocioException;
import com.proyecto.supply_core.venta.enums.MedioPagoVenta;

/** Pruebas del cálculo del dinero de la venta (spec 004): sin base de datos. */
class CalculoVentaTest {

    /**
     * @param v texto
     * @return número
     */
    private static BigDecimal n(String v) {
        return new BigDecimal(v);
    }

    /** Precio con IVA incluido (mostrador, D2): la base se separa del total. */
    @Test
    void conIvaIncluidoLaBaseSeSeparaDelTotal() {
        CalculoVenta.ResultadoLinea r = CalculoVenta.linea(new CalculoVenta.Linea(n("11900"), n("2"), n("0"), n("19"), true));
        assertEquals(n("23800.00"), r.total());
        assertEquals(n("20000.00"), r.base());
        assertEquals(n("3800.00"), r.impuesto());
        assertEquals(n("0.00"), r.descuento());
    }

    /** Precio sin IVA: el impuesto se suma a la base. */
    @Test
    void sinIvaIncluidoElImpuestoSeSuma() {
        CalculoVenta.ResultadoLinea r = CalculoVenta.linea(new CalculoVenta.Linea(n("10000"), n("3"), n("0"), n("19"), false));
        assertEquals(n("30000.00"), r.base());
        assertEquals(n("5700.00"), r.impuesto());
        assertEquals(n("35700.00"), r.total());
    }

    /** El descuento se aplica sobre el bruto y baja el precio unitario. */
    @Test
    void elDescuentoBajaElTotalYElUnitario() {
        CalculoVenta.ResultadoLinea r = CalculoVenta.linea(new CalculoVenta.Linea(n("2000"), n("5"), n("10"), n("19"), true));
        assertEquals(n("10000.00"), r.bruto());
        assertEquals(n("1000.00"), r.descuento());
        assertEquals(n("9000.00"), r.total());
        assertEquals(n("1800.00"), r.precioUnitario());
        assertEquals(r.total(), r.base().add(r.impuesto()));
    }

    /** Un producto exento no lleva impuesto. */
    @Test
    void unProductoExentoNoLlevaImpuesto() {
        CalculoVenta.ResultadoLinea r = CalculoVenta.linea(new CalculoVenta.Linea(n("1500"), n("1.5"), n("0"), BigDecimal.ZERO, true));
        assertEquals(n("2250.00"), r.total());
        assertEquals(n("0.00"), r.impuesto());
    }

    /** D3: solo en efectivo se redondea al múltiplo de $50 más cercano; con tarjeta no. */
    @Test
    void elEfectivoSeRedondeaACincuentaYLaTarjetaNo() {
        assertEquals(n("-20.00"), CalculoVenta.redondeo(n("23820"), true));
        assertEquals(n("25.00"), CalculoVenta.redondeo(n("23825"), true));
        assertEquals(n("0.00"), CalculoVenta.redondeo(n("23850"), true));
        assertEquals(n("0.00"), CalculoVenta.redondeo(n("23820"), false));
        // menos de $25 no se redondea a cero (el pago en efectivo quedaría en 0): se cobra exacto
        assertEquals(n("0.00"), CalculoVenta.redondeo(n("20"), true));
        assertEquals(n("25.00"), CalculoVenta.redondeo(n("25"), true));
    }

    /** El cambio sale del efectivo y en la caja queda lo cobrado. */
    @Test
    void elCambioSaleDelEfectivo() {
        CalculoVenta.ResultadoPagos r = CalculoVenta.pagos(n("23800"), List.of(
                new CalculoVenta.Pago(MedioPagoVenta.TARJETA_DEBITO, n("10000")),
                new CalculoVenta.Pago(MedioPagoVenta.EFECTIVO, n("20000"))));
        assertEquals(n("6200.00"), r.cambio());
        assertEquals(n("13800.00"), r.porMedio().get(MedioPagoVenta.EFECTIVO));
        assertEquals(n("10000.00"), r.porMedio().get(MedioPagoVenta.TARJETA_DEBITO));
    }

    /** Pagos que no alcanzan o un medio distinto del efectivo que se pasa: se rechazan. */
    @Test
    void losPagosQueNoCuadranSeRechazan() {
        assertEquals("PAGO_INSUFICIENTE", assertThrows(NegocioException.class, () -> CalculoVenta.pagos(n("10000"),
                List.of(new CalculoVenta.Pago(MedioPagoVenta.EFECTIVO, n("9999"))))).getCodigo());
        assertEquals("PAGO_EXCEDE", assertThrows(NegocioException.class, () -> CalculoVenta.pagos(n("10000"),
                List.of(new CalculoVenta.Pago(MedioPagoVenta.TARJETA_CREDITO, n("10001"))))).getCodigo());
        assertEquals("PAGO_EXCEDE", assertThrows(NegocioException.class, () -> CalculoVenta.pagos(n("10000"), List.of(
                new CalculoVenta.Pago(MedioPagoVenta.TRANSFERENCIA, n("10000")),
                new CalculoVenta.Pago(MedioPagoVenta.EFECTIVO, n("5000"))))).getCodigo());
        // el crédito (spec 007) y el saldo a favor nunca se pasan del total ni dan cambio
        assertEquals("PAGO_EXCEDE", assertThrows(NegocioException.class, () -> CalculoVenta.pagos(n("10000"),
                List.of(new CalculoVenta.Pago(MedioPagoVenta.CREDITO, n("10001"))))).getCodigo());
        CalculoVenta.ResultadoPagos mixto = CalculoVenta.pagos(n("10000"), List.of(
                new CalculoVenta.Pago(MedioPagoVenta.CREDITO, n("6000")), new CalculoVenta.Pago(MedioPagoVenta.SALDO_FAVOR, n("1000")),
                new CalculoVenta.Pago(MedioPagoVenta.EFECTIVO, n("5000"))));
        assertEquals(n("2000.00"), mixto.cambio());
        assertEquals(n("3000.00"), mixto.porMedio().get(MedioPagoVenta.EFECTIVO));
    }

    /**
     * Propiedades del dinero con ventas aleatorias: total = Σ líneas = base + impuestos; lo que queda en la caja más
     * el cambio es lo que se pagó; lo que queda en la caja es lo que se cobró.
     */
    @Test
    void elDineroSiempreCuadraEnVentasAleatorias() {
        Random azar = new Random(20261120L);
        int[] tarifas = { 0, 5, 19 };
        for (int i = 0; i < 2000; i++) {
            List<CalculoVenta.ResultadoLinea> lineas = new ArrayList<>();
            int cuantas = 1 + azar.nextInt(8);
            for (int j = 0; j < cuantas; j++) {
                BigDecimal precio = BigDecimal.valueOf(50 + azar.nextInt(500_000)).movePointLeft(azar.nextInt(3));
                BigDecimal cantidad = BigDecimal.valueOf(1 + azar.nextInt(40_000)).movePointLeft(azar.nextInt(4));
                BigDecimal descuento = BigDecimal.valueOf(azar.nextInt(9_001)).movePointLeft(2);
                CalculoVenta.ResultadoLinea r = CalculoVenta.linea(new CalculoVenta.Linea(precio, cantidad, descuento,
                        BigDecimal.valueOf(tarifas[azar.nextInt(3)]), azar.nextBoolean()));
                assertEquals(r.total(), r.base().add(r.impuesto()), "línea: total = base + impuesto");
                assertEquals(r.bruto().subtract(r.descuento()).compareTo(r.base()) >= 0, true);
                assertTrue(r.impuesto().signum() >= 0 && r.base().signum() >= 0);
                lineas.add(r);
            }
            CalculoVenta.Totales t = CalculoVenta.totales(lineas);
            assertEquals(lineas.stream().map(CalculoVenta.ResultadoLinea::total).reduce(BigDecimal.ZERO, BigDecimal::add)
                    .compareTo(t.total()), 0, "total = Σ líneas");
            assertEquals(t.total(), t.base().add(t.impuestos()), "total = base + impuestos");

            boolean soloEfectivo = azar.nextBoolean();
            BigDecimal aPagar = t.total().add(CalculoVenta.redondeo(t.total(), soloEfectivo));
            assertEquals(0, aPagar.subtract(t.total()).abs().compareTo(n("25")) <= 0 ? 0 : 1, "el redondeo no pasa de $25");
            List<CalculoVenta.Pago> pagos = new ArrayList<>();
            if (soloEfectivo) {
                pagos.add(new CalculoVenta.Pago(MedioPagoVenta.EFECTIVO, aPagar.add(BigDecimal.valueOf(azar.nextInt(50_000)))));
            } else {
                BigDecimal tarjeta = aPagar.multiply(BigDecimal.valueOf(azar.nextInt(100))).movePointLeft(2)
                        .setScale(2, java.math.RoundingMode.DOWN);
                if (tarjeta.signum() > 0) {
                    pagos.add(new CalculoVenta.Pago(MedioPagoVenta.TARJETA_DEBITO, tarjeta));
                }
                pagos.add(new CalculoVenta.Pago(MedioPagoVenta.EFECTIVO,
                        aPagar.subtract(tarjeta).add(BigDecimal.valueOf(azar.nextInt(20_000)))));
            }
            CalculoVenta.ResultadoPagos r = CalculoVenta.pagos(aPagar, pagos);
            BigDecimal enCaja = r.porMedio().values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal pagado = pagos.stream().map(CalculoVenta.Pago::monto).reduce(BigDecimal.ZERO, BigDecimal::add);
            assertEquals(0, enCaja.compareTo(aPagar), "en la caja queda lo cobrado");
            assertEquals(0, enCaja.add(r.cambio()).compareTo(pagado), "caja + cambio = pagado");
            assertTrue(r.cambio().signum() >= 0);
            for (Map.Entry<MedioPagoVenta, BigDecimal> e : r.porMedio().entrySet()) {
                assertTrue(e.getValue().signum() >= 0, "ningún medio queda negativo");
            }
        }
    }
}
