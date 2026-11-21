package com.proyecto.supply_core.venta.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Regla del umbral de alertas por cajero (spec 010), sin base de datos. */
class AlertaCajeroServiceTest {

    /** Avisa al llegar al umbral, no antes. */
    @Test
    void avisaAlLlegarAlUmbral() {
        assertFalse(AlertaCajeroService.alcanza(2, 3));
        assertTrue(AlertaCajeroService.alcanza(3, 3));
        assertTrue(AlertaCajeroService.alcanza(7, 3));
    }

    /** Un umbral en cero nunca avisa. */
    @Test
    void unUmbralEnCeroNoAvisa() {
        assertFalse(AlertaCajeroService.alcanza(100, 0));
    }
}
