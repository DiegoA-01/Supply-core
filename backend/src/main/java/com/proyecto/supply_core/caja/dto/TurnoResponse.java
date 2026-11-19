package com.proyecto.supply_core.caja.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import com.proyecto.supply_core.caja.enums.EstadoTurno;
import com.proyecto.supply_core.caja.enums.MedioPago;

/**
 * Un turno de caja. Mientras está abierto, al cajero no se le muestra lo esperado ni las ventas: solo la marca
 * {@code debeRetirar} cuando el efectivo pasa el tope de la caja. Al cerrar, trae lo esperado, lo contado y las
 * diferencias por medio de pago (tiquete Z).
 *
 * @param id                    turno
 * @param cajaId                caja
 * @param caja                  nombre de la caja
 * @param bodega                bodega de la caja
 * @param cajeroId              cajero
 * @param cajero                nombre del cajero
 * @param estado                ABIERTO, CERRADO o APROBADO
 * @param abiertoEn             apertura
 * @param baseApertura          base
 * @param debeRetirar           el efectivo pasó el tope (solo con el turno abierto)
 * @param cerradoEn             cierre o {@code null}
 * @param esperado              esperado por medio (con el efectivo) o {@code null} si está abierto
 * @param contado               contado por medio (con el efectivo) o {@code null}
 * @param diferencias           contado − esperado por medio o {@code null}
 * @param diferencia            suma de las diferencias o {@code null}
 * @param requiereAprobacion    alguna diferencia pasó la tolerancia
 * @param observacion           nota del cajero o {@code null}
 * @param aprobadoPor           quién aprobó o {@code null}
 * @param aprobadoEn            cuándo o {@code null}
 * @param explicacionAprobacion explicación del supervisor o {@code null}
 * @param movimientos           libro de caja (al cajero con el turno abierto, solo base, retiros, gastos e ingresos)
 */
public record TurnoResponse(Long id, Long cajaId, String caja, String bodega, Long cajeroId, String cajero,
        EstadoTurno estado, LocalDateTime abiertoEn, BigDecimal baseApertura, boolean debeRetirar,
        LocalDateTime cerradoEn, Map<MedioPago, BigDecimal> esperado, Map<MedioPago, BigDecimal> contado,
        Map<MedioPago, BigDecimal> diferencias, BigDecimal diferencia, boolean requiereAprobacion, String observacion,
        String aprobadoPor, LocalDateTime aprobadoEn, String explicacionAprobacion,
        List<MovimientoCajaResponse> movimientos) {
}
