package com.proyecto.supply_core.common.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

/** Días hábiles de Colombia: festivos oficiales de 2026 y conteo de plazos. */
class DiasHabilesTest {

    @Test
    void reconoceLosDieciochoFestivosDe2026() {
        List<LocalDate> festivos2026 = List.of(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 12),
                LocalDate.of(2026, 3, 23), LocalDate.of(2026, 4, 2), LocalDate.of(2026, 4, 3), LocalDate.of(2026, 5, 1),
                LocalDate.of(2026, 5, 18), LocalDate.of(2026, 6, 8), LocalDate.of(2026, 6, 15),
                LocalDate.of(2026, 6, 29), LocalDate.of(2026, 7, 20), LocalDate.of(2026, 8, 7),
                LocalDate.of(2026, 8, 17), LocalDate.of(2026, 10, 12), LocalDate.of(2026, 11, 2),
                LocalDate.of(2026, 11, 16), LocalDate.of(2026, 12, 8), LocalDate.of(2026, 12, 25));
        festivos2026.forEach(f -> assertTrue(DiasHabiles.esFestivo(f), f + " es festivo"));
        long total = LocalDate.of(2026, 1, 1).datesUntil(LocalDate.of(2027, 1, 1)).filter(DiasHabiles::esFestivo).count();
        assertEquals(18, total);
        assertFalse(DiasHabiles.esFestivo(LocalDate.of(2026, 11, 11)));   // se trasladó al lunes 16
        assertEquals(LocalDate.of(2026, 4, 5), DiasHabiles.pascua(2026));
        assertEquals(LocalDate.of(2027, 3, 28), DiasHabiles.pascua(2027));
    }

    @Test
    void sumaSaltandoFinesDeSemanaYFestivos() {
        LocalDate miercoles = LocalDate.of(2026, 11, 18);
        // 6 días hábiles: jue 19, vie 20, lun 23, mar 24, mié 25, jue 26
        assertEquals(LocalDate.of(2026, 11, 26), DiasHabiles.sumar(miercoles, 6));
        // viernes 13 + 1 hábil: el lunes 16 es festivo, llega el martes 17
        assertEquals(LocalDate.of(2026, 11, 17), DiasHabiles.sumar(LocalDate.of(2026, 11, 13), 1));
        assertEquals(miercoles, DiasHabiles.sumar(miercoles, 0));
        assertEquals(LocalDate.of(2026, 11, 13), DiasHabiles.restar(LocalDate.of(2026, 11, 17), 1));
        assertThrows(IllegalArgumentException.class, () -> DiasHabiles.sumar(miercoles, -1));
    }

    @Test
    void cuentaLosDiasHabilesEntreDosFechas() {
        assertEquals(6, DiasHabiles.entre(LocalDate.of(2026, 11, 18), LocalDate.of(2026, 11, 26)));
        assertEquals(1, DiasHabiles.entre(LocalDate.of(2026, 11, 13), LocalDate.of(2026, 11, 17)));
        assertEquals(0, DiasHabiles.entre(LocalDate.of(2026, 11, 20), LocalDate.of(2026, 11, 20)));
        assertEquals(8, DiasHabiles.calendario(LocalDate.of(2026, 11, 18), LocalDate.of(2026, 11, 26)));
    }
}
