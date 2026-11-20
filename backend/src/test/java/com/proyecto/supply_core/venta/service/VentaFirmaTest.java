package com.proyecto.supply_core.venta.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.proyecto.supply_core.venta.dto.LineaVentaRequest;
import com.proyecto.supply_core.venta.entity.Venta;
import com.proyecto.supply_core.venta.entity.VentaLinea;

/**
 * Huella del carrito: un reenvío con la misma clave solo devuelve la venta anterior si es el mismo carrito.
 */
class VentaFirmaTest {

    /**
     * @param productoId producto
     * @param cantidad   cantidad
     * @param descuento  porcentaje
     * @return línea pedida
     */
    private static LineaVentaRequest pedida(long productoId, String cantidad, String descuento) {
        return new LineaVentaRequest(productoId, null, new BigDecimal(cantidad), descuento == null ? null : new BigDecimal(descuento));
    }

    /**
     * @param productoId producto
     * @param cantidad   cantidad
     * @param descuento  porcentaje
     * @return línea guardada
     */
    private static VentaLinea guardada(long productoId, String cantidad, String descuento) {
        VentaLinea l = new VentaLinea();
        l.setProductoId(productoId);
        l.setCantidad(new BigDecimal(cantidad));
        l.setDescuentoPct(new BigDecimal(descuento));
        return l;
    }

    @Test
    void elMismoCarritoCoincideAunqueCambieLaEscalaOElOrden() {
        Venta v = new Venta();
        v.getLineas().add(guardada(7, "2.0000", "0.00"));
        v.getLineas().add(guardada(3, "1.5000", "10.00"));
        assertEquals(VentaService.firmaVenta(v), VentaService.firma(List.of(pedida(3, "1.5", "10"), pedida(7, "2", null))));
    }

    @Test
    void otroCarritoNoCoincide() {
        Venta v = new Venta();
        v.getLineas().add(guardada(7, "2.0000", "0.00"));
        assertNotEquals(VentaService.firmaVenta(v), VentaService.firma(List.of(pedida(7, "3", null))));
        assertNotEquals(VentaService.firmaVenta(v), VentaService.firma(List.of(pedida(7, "2", "5"))));
        assertNotEquals(VentaService.firmaVenta(v), VentaService.firma(List.of(pedida(7, "2", null), pedida(8, "1", null))));
    }
}
