package com.proyecto.supply_core.compra.cotizacion.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import com.proyecto.supply_core.compra.cotizacion.entity.Cotizacion;
import com.proyecto.supply_core.compra.cotizacion.entity.CotizacionLinea;

class CalculadoraCotizacionTest {

    @Test
    void calculaNetoImpuestoTotalYPrecioNormalizado() {
        Cotizacion c = new Cotizacion();
        // 2 cajas x24 a $12.000 con 10 % de descuento e IVA 19 %
        c.agregarLinea(linea("2", "24", "12000", "10", "19"));
        // 5 und a $999.99 sin descuento ni impuesto
        c.agregarLinea(linea("5", "1", "999.99", "0", "0"));

        CalculadoraCotizacion.recalcular(c);

        CotizacionLinea cajas = c.getLineas().get(0);
        assertEquals(new BigDecimal("21600.00"), cajas.getSubtotal()); // 24.000 − 10 %
        assertEquals(new BigDecimal("450.0000"), cajas.getPrecioUnitarioNormalizado()); // 10.800 / 24
        assertEquals(new BigDecimal("4999.95"), c.getLineas().get(1).getSubtotal());
        assertEquals(new BigDecimal("26599.95"), c.getSubtotal());
        assertEquals(new BigDecimal("4104.00"), c.getImpuestos()); // 19 % de 21.600
        assertEquals(new BigDecimal("30703.95"), c.getTotal());
    }

    @Test
    void sinLineasLosMontosQuedanVacios() {
        Cotizacion c = new Cotizacion();
        c.setTotal(BigDecimal.TEN);
        CalculadoraCotizacion.recalcular(c);
        assertNull(c.getTotal());
        assertNull(c.getSubtotal());
    }

    @Test
    void redondeaHalfUp() {
        assertEquals(new BigDecimal("0.33"), CalculadoraCotizacion.neto(BigDecimal.ONE, new BigDecimal("0.333"),
                BigDecimal.ZERO));
        assertEquals(new BigDecimal("0.3333"), CalculadoraCotizacion.normalizado(BigDecimal.ONE, BigDecimal.ZERO,
                new BigDecimal("3")));
    }

    private static CotizacionLinea linea(String cantidad, String factor, String precio, String desc, String iva) {
        CotizacionLinea l = new CotizacionLinea();
        l.setCantidadOfertada(new BigDecimal(cantidad));
        l.setFactorConversion(new BigDecimal(factor));
        l.setPrecioUnitarioOfertado(new BigDecimal(precio));
        l.setDescuentoPct(new BigDecimal(desc));
        l.setImpuestoPct(new BigDecimal(iva));
        return l;
    }
}
