package com.proyecto.supply_core.caja.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.proyecto.supply_core.caja.enums.MedioPago;
import com.proyecto.supply_core.caja.enums.TipoMovimientoCaja;

/**
 * Un movimiento del libro de caja.
 *
 * @param id           movimiento
 * @param tipo         tipo
 * @param medioPago    medio
 * @param monto        positivo entra, negativo sale
 * @param motivo       motivo o {@code null}
 * @param soporte      soporte o {@code null}
 * @param usuario      quién lo registró
 * @param autorizadoPor quién lo autorizó o {@code null}
 * @param creadoEn     cuándo
 */
public record MovimientoCajaResponse(Long id, TipoMovimientoCaja tipo, MedioPago medioPago, BigDecimal monto,
        String motivo, String soporte, String usuario, String autorizadoPor, LocalDateTime creadoEn) {
}
