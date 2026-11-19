package com.proyecto.supply_core.reorden.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.proyecto.supply_core.reorden.dto.ReabastecimientoItem;
import com.proyecto.supply_core.reorden.dto.ReabastecimientoResponse;
import com.proyecto.supply_core.reorden.enums.EstadoReabastecimiento;
import com.proyecto.supply_core.reporte.dto.ClaseRotacion;
import com.proyecto.supply_core.reporte.dto.RotacionItem;

/**
 * Cálculo del plan con números a mano. Hoy es miércoles 18 de noviembre de 2026; el proveedor tarda 6 días hábiles
 * (llega el jueves 26: L = 8 días) y la seguridad es de 2 días hábiles (hasta el lunes 30: S = 4 días).
 * Consumo: 90 unidades en 90 días = 1 por día. Punto de pedido = 1 × (8 + 4) = 12.
 */
class ReabastecimientoCalculoTest {

    private static final LocalDate HOY = LocalDate.of(2026, 11, 18);
    private static final ReabastecimientoService.Parametros PLAZO_6 =
            new ReabastecimientoService.Parametros(6, true, 2, 30);

    private static RotacionItem rotacion(String stock, String minimo, String salidas) {
        return new RotacionItem(1L, "TOR-001", "Tornillo", "Tornillería", "UND", new BigDecimal(stock),
                new BigDecimal(minimo), new BigDecimal(salidas), BigDecimal.ZERO, BigDecimal.ONE, null, null, null, null,
                null, ClaseRotacion.MEDIA, false);
    }

    private static ReabastecimientoItem calcular(String stock, String minimo, String salidas, String maximo,
            String porRecibir) {
        return ReabastecimientoService.calcular(rotacion(stock, minimo, salidas),
                maximo == null ? null : new BigDecimal(maximo), new BigDecimal(porRecibir), BigDecimal.ZERO, 90, PLAZO_6,
                null, HOY);
    }

    private static void igual(String esperado, BigDecimal real) {
        assertEquals(0, new BigDecimal(esperado).compareTo(real), "esperado " + esperado + " y fue " + real);
    }

    @Test
    void urgenteSiSeAcabaAntesDeQueLlegueUnPedidoHechoHoy() {
        ReabastecimientoItem i = calcular("5", "0", "90", null, "0");
        assertEquals(EstadoReabastecimiento.URGENTE, i.estado());
        assertEquals(LocalDate.of(2026, 11, 26), i.llegadaSiPidoHoy());
        assertEquals(LocalDate.of(2026, 11, 23), i.fechaAgotamiento());
        assertEquals(HOY, i.pedirAntesDe());
        assertEquals("Se acaba hacia el 23 de noviembre y un pedido hecho hoy llega el 26 de noviembre (6 días hábiles).",
                i.motivo());
        igual("37", i.cantidadSugerida());          // 1 × (8 + 4 + 30) − 5
    }

    @Test
    void pedirYaSiLoDisponibleNoCubrePlazoMasSeguridad() {
        ReabastecimientoItem i = calcular("10", "0", "90", null, "0");
        assertEquals(EstadoReabastecimiento.PEDIR_YA, i.estado());
        igual("12", i.puntoPedido());
        igual("32", i.cantidadSugerida());          // 42 − 10
        igual("10.0", i.diasCobertura());
    }

    @Test
    void prontoSiHayQuePedirEnLosProximosDiasHabiles() {
        ReabastecimientoItem i = calcular("15", "0", "90", null, "0");
        assertEquals(EstadoReabastecimiento.PRONTO, i.estado());
        // le sobran 3 días sobre el punto de pedido: el sábado 21, que no es hábil, pasa al viernes 20
        assertEquals(LocalDate.of(2026, 11, 20), i.pedirAntesDe());
    }

    @Test
    void okSiAlcanzaHolgadoYLoQueVieneEnCaminoCuenta() {
        ReabastecimientoItem holgado = calcular("100", "0", "90", null, "0");
        assertEquals(EstadoReabastecimiento.OK, holgado.estado());
        assertTrue(holgado.motivo().endsWith("de 2027."), holgado.motivo());   // otro año: lleva el año
        ReabastecimientoItem conOrden = calcular("10", "0", "90", null, "40");
        assertEquals(EstadoReabastecimiento.OK, conOrden.estado());
        igual("0", conOrden.cantidadSugerida());
    }

    @Test
    void elMaximoLimitaLaCantidadYElMinimoSubeElPuntoDePedido() {
        igual("10", calcular("10", "0", "90", "20", "0").cantidadSugerida());   // objetivo 42 limitado a 20
        ReabastecimientoItem conMinimo = calcular("40", "50", "90", null, "0");
        igual("50", conMinimo.puntoPedido());
        assertEquals(EstadoReabastecimiento.PEDIR_YA, conMinimo.estado());
    }

    @Test
    void noRedondeaDeMasCuandoElConsumoDiarioNoEsExacto() {
        // 690 en 90 días = 7,6667 por día; 690 × 42 ÷ 90 = 322 exactos − 60 = 262 (no 263)
        igual("262", calcular("60", "0", "690", null, "0").cantidadSugerida());
    }

    @Test
    void sinConsumoSoloPideSiEstaBajoElMinimo() {
        igual("0", calcular("10", "5", "0", "50", "0").cantidadSugerida());   // con máximo tampoco pide si no consume
        ReabastecimientoItem quieto = calcular("10", "5", "0", null, "0");
        assertEquals(EstadoReabastecimiento.SIN_CONSUMO, quieto.estado());
        assertNull(quieto.pedirAntesDe());
        igual("0", quieto.cantidadSugerida());
        ReabastecimientoItem bajo = calcular("2", "5", "0", "20", "0");
        assertEquals(EstadoReabastecimiento.PEDIR_YA, bajo.estado());
        igual("18", bajo.cantidadSugerida());       // hasta el máximo
    }

    @Test
    void elMensajeDelResumenNombraLosProductosYLaCantidad() {
        ReabastecimientoItem urgente = calcular("5", "0", "90", null, "0");
        ReabastecimientoResponse plan = new ReabastecimientoResponse(HOY, 90, 6, 2, 30, 1, 0, 0, 0, 0, List.of(urgente));
        String mensaje = ReabastecimientoAvisoService.mensaje(plan, List.of(urgente));
        assertTrue(mensaje.contains("URGENTE TOR-001 - Tornillo (pedir 37 UND)"), mensaje);
        assertTrue(mensaje.startsWith("Hoy hay que pedir 1 producto(s)"), mensaje);
    }
}
