package com.proyecto.supply_core.cartera.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

import com.proyecto.supply_core.cliente.entity.Cliente;

/** Edades de cartera y excepción de crédito (spec 007), sin base de datos. */
class CalculoCarteraTest {

    private static final LocalDate HOY = LocalDate.of(2026, 11, 20);

    /**
     * @param saldo saldo
     * @param dias  días desde el vencimiento (negativo = aún no vence)
     * @return cuenta
     */
    private static CalculoCartera.Cuenta cuenta(String saldo, int dias) {
        return new CalculoCartera.Cuenta(new BigDecimal(saldo), HOY.minusDays(dias));
    }

    /** Cada cuenta cae en su tramo; lo que vence hoy aún está al día. */
    @Test
    void cadaCuentaCaeEnSuTramo() {
        CalculoCartera.Edades e = CalculoCartera.edades(List.of(cuenta("100", -5), cuenta("50", 0), cuenta("10", 1),
                cuenta("20", 30), cuenta("30", 31), cuenta("40", 90), cuenta("70", 91)), HOY);
        assertEquals(new BigDecimal("150.00"), e.alDia());
        assertEquals(new BigDecimal("30.00"), e.d1a30());
        assertEquals(new BigDecimal("30.00"), e.d31a60());
        assertEquals(new BigDecimal("40.00"), e.d61a90());
        assertEquals(new BigDecimal("70.00"), e.mas90());
        assertEquals(new BigDecimal("320.00"), e.total());
        assertEquals(new BigDecimal("170.00"), e.vencido());
    }

    /** Propiedad: la suma de las edades es siempre el saldo total. */
    @Test
    void lasEdadesSumanElSaldoTotal() {
        Random azar = new Random(7);
        for (int i = 0; i < 500; i++) {
            List<CalculoCartera.Cuenta> cuentas = new ArrayList<>();
            BigDecimal suma = BigDecimal.ZERO;
            for (int j = 0; j < 1 + azar.nextInt(10); j++) {
                BigDecimal saldo = BigDecimal.valueOf(azar.nextInt(5_000_000)).movePointLeft(2);
                cuentas.add(new CalculoCartera.Cuenta(saldo, HOY.minusDays(azar.nextInt(200) - 60)));
                suma = suma.add(saldo);
            }
            CalculoCartera.Edades e = CalculoCartera.edades(cuentas, HOY);
            assertEquals(0, suma.compareTo(e.total()));
            assertEquals(0, e.total().compareTo(e.alDia().add(e.d1a30()).add(e.d31a60()).add(e.d61a90()).add(e.mas90())));
        }
    }

    /** La excepción vale hasta su último día incluido. */
    @Test
    void laExcepcionValeHastaSuUltimoDia() {
        Cliente c = new Cliente();
        assertFalse(CarteraService.excepcionVigente(c, HOY));
        c.setCreditoExcepcionHasta(HOY);
        assertTrue(CarteraService.excepcionVigente(c, HOY));
        assertFalse(CarteraService.excepcionVigente(c, HOY.plusDays(1)));
    }
}
