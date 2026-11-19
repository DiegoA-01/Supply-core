package com.proyecto.supply_core.venta.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** Limpieza del texto que el cajero escribe o escanea antes de buscar. */
class VentaProductoServiceTest {

    @Test
    void elTextoSeRecortaYNoPasaDeCienCaracteres() {
        assertEquals("", VentaProductoService.limpiar(null));
        assertEquals("", VentaProductoService.limpiar("   "));
        assertEquals("CAB-12", VentaProductoService.limpiar("  CAB-12 "));
        assertEquals(100, VentaProductoService.limpiar("x".repeat(150)).length());
    }

    @Test
    void losComodinesSeBuscanComoTextoYNoComoPatron() {
        // "%" no debe traer todo el catálogo y "_" no debe valer por cualquier letra
        assertEquals("!%", VentaProductoService.escaparLike("%"));
        assertEquals("tor!_12", VentaProductoService.escaparLike("tor_12"));
        assertEquals("a!!b", VentaProductoService.escaparLike("a!b"));
        assertEquals("cable 12", VentaProductoService.escaparLike("cable 12"));
    }
}
