package com.proyecto.supply_core.venta.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import com.proyecto.supply_core.caja.enums.MedioPago;
import com.proyecto.supply_core.common.exception.NegocioException;
import com.proyecto.supply_core.venta.entity.Venta;
import com.proyecto.supply_core.venta.entity.VentaPago;
import com.proyecto.supply_core.venta.enums.FormaReembolso;
import com.proyecto.supply_core.venta.enums.MedioPagoVenta;

/** Reglas del reembolso de una devolución (spec 006), sin base de datos. */
class DevolucionServiceTest {

    /**
     * @param medios medios con que se pagó la venta
     * @return venta con esos pagos
     */
    private static Venta pagadaCon(MedioPagoVenta... medios) {
        Venta v = new Venta();
        for (MedioPagoVenta m : medios) {
            VentaPago p = new VentaPago();
            p.setMedio(m);
            p.setMonto(BigDecimal.TEN);
            v.getPagos().add(p);
        }
        return v;
    }

    /** El mismo medio solo aplica si la venta se pagó con uno; efectivo y saldo a favor siempre. */
    @Test
    void elMismoMedioSoloSiLaVentaSePagoConUno() {
        assertEquals(MedioPago.TARJETA_DEBITO,
                DevolucionService.medioDeReembolso(FormaReembolso.MISMO_MEDIO, pagadaCon(MedioPagoVenta.TARJETA_DEBITO)));
        assertEquals(MedioPago.EFECTIVO, DevolucionService.medioDeReembolso(FormaReembolso.EFECTIVO,
                pagadaCon(MedioPagoVenta.TARJETA_DEBITO, MedioPagoVenta.EFECTIVO)));
        assertNull(DevolucionService.medioDeReembolso(FormaReembolso.SALDO_A_FAVOR, pagadaCon(MedioPagoVenta.EFECTIVO)));
        assertEquals("MEDIO_AMBIGUO", assertThrows(NegocioException.class, () -> DevolucionService.medioDeReembolso(
                FormaReembolso.MISMO_MEDIO, pagadaCon(MedioPagoVenta.TARJETA_DEBITO, MedioPagoVenta.EFECTIVO))).getCodigo());
    }
}
