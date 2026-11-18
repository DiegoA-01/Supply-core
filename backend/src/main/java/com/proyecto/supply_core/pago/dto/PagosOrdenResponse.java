package com.proyecto.supply_core.pago.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Estado de pago de una orden (todas sus versiones).
 *
 * @param consecutivo  OC-000001
 * @param ordenVigenteId versión vigente (la última)
 * @param total        total de la versión vigente
 * @param pagado       suma de los pagos REGISTRADOS
 * @param saldo        total − pagado
 * @param pagos        pagos, del más antiguo al más reciente (incluye anulados)
 */
public record PagosOrdenResponse(String consecutivo, Long ordenVigenteId, BigDecimal total, BigDecimal pagado,
        BigDecimal saldo, List<PagoResponse> pagos) {
}
