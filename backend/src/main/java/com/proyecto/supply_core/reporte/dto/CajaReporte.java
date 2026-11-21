package com.proyecto.supply_core.reporte.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Reporte de caja de un periodo (spec 009): cierres por turno, diferencias por cajero, retiros, gastos, anulaciones y
 * descuentos o precios autorizados. Lo calcula Java; la pantalla solo lo muestra.
 *
 * @param desde        primer día (incluido)
 * @param hasta        último día (incluido)
 * @param turnos       turnos abiertos en el periodo, del más reciente al más antiguo
 * @param porCajero    diferencias sumadas por cajero, del mayor faltante al menor
 * @param movimientos  retiros a la bóveda y gastos pagados de la caja
 * @param anulaciones  ventas anuladas en el periodo
 * @param autorizados  líneas vendidas con descuento o precio autorizado por un supervisor
 * @param totales      sumas del periodo
 */
public record CajaReporte(LocalDate desde, LocalDate hasta, List<Turno> turnos, List<Cajero> porCajero,
        List<Movimiento> movimientos, List<Anulacion> anulaciones, List<Autorizado> autorizados, Totales totales) {

    /**
     * @param id                 turno
     * @param caja               caja
     * @param cajero             cajero
     * @param estado             ABIERTO, CERRADO o APROBADO
     * @param abiertoEn          apertura
     * @param cerradoEn          cierre ({@code null} si sigue abierto)
     * @param esperadoEfectivo   efectivo esperado al cierre
     * @param contadoEfectivo    efectivo contado
     * @param diferencia         diferencia total del cierre (negativa = faltante)
     * @param porAprobar         {@code true} si la diferencia pasa la tolerancia y nadie la ha aprobado
     * @param aprobadoPor        supervisor que aprobó la diferencia
     * @param explicacion        lo que escribió al aprobar
     */
    @Schema(name = "TurnoCajaReporte")
    public record Turno(Long id, String caja, String cajero, String estado, LocalDateTime abiertoEn,
            LocalDateTime cerradoEn, BigDecimal esperadoEfectivo, BigDecimal contadoEfectivo, BigDecimal diferencia,
            boolean porAprobar, String aprobadoPor, String explicacion) {
    }

    /**
     * @param cajeroId    cajero
     * @param cajero      nombre
     * @param turnos      turnos del periodo
     * @param cerrados    turnos ya cerrados
     * @param diferencia  suma de las diferencias
     * @param faltantes   suma de los faltantes (negativa o 0)
     * @param sobrantes   suma de los sobrantes (positiva o 0)
     * @param porAprobar  turnos con diferencia sin aprobar
     */
    @Schema(name = "CajeroCajaReporte")
    public record Cajero(Long cajeroId, String cajero, long turnos, long cerrados, BigDecimal diferencia,
            BigDecimal faltantes, BigDecimal sobrantes, long porAprobar) {
    }

    /**
     * @param tipo        RETIRO o GASTO
     * @param caja        caja
     * @param medio       medio de pago
     * @param monto       valor (positivo)
     * @param motivo      motivo escrito
     * @param registradoPor quién lo registró
     * @param autorizadoPor supervisor que lo autorizó
     * @param creadoEn    cuándo
     */
    @Schema(name = "MovimientoCajaReporte")
    public record Movimiento(String tipo, String caja, String medio, BigDecimal monto, String motivo,
            String registradoPor, String autorizadoPor, LocalDateTime creadoEn) {
    }

    /**
     * @param ventaId       venta
     * @param consecutivo   consecutivo
     * @param total         lo que se había cobrado
     * @param motivo        motivo de la anulación
     * @param cajero        quien la anuló
     * @param autorizadoPor supervisor que la autorizó
     * @param anuladaEn     cuándo
     */
    @Schema(name = "AnulacionCajaReporte")
    public record Anulacion(Long ventaId, String consecutivo, BigDecimal total, String motivo, String cajero,
            String autorizadoPor, LocalDateTime anuladaEn) {
    }

    /**
     * @param ventaId        venta
     * @param consecutivo    consecutivo
     * @param producto       código y nombre
     * @param tipo           «Descuento» o «Precio bajo el mínimo»
     * @param descuentoPct   porcentaje de descuento
     * @param descuentoValor valor del descuento
     * @param cajero         quien vendió
     * @param autorizadoPor  supervisor que lo autorizó
     * @param confirmadaEn   cuándo
     */
    @Schema(name = "AutorizadoCajaReporte")
    public record Autorizado(Long ventaId, String consecutivo, String producto, String tipo, BigDecimal descuentoPct,
            BigDecimal descuentoValor, String cajero, String autorizadoPor, LocalDateTime confirmadaEn) {
    }

    /**
     * @param turnos       turnos del periodo
     * @param diferencia   suma de las diferencias de los cierres
     * @param faltantes    suma de los faltantes
     * @param sobrantes    suma de los sobrantes
     * @param porAprobar   turnos con diferencia sin aprobar
     * @param retiros      suma de los retiros
     * @param gastos       suma de los gastos
     * @param anulaciones  suma de lo anulado
     * @param descuentos   suma de los descuentos autorizados
     */
    @Schema(name = "TotalesCajaReporte")
    public record Totales(long turnos, BigDecimal diferencia, BigDecimal faltantes, BigDecimal sobrantes, long porAprobar,
            BigDecimal retiros, BigDecimal gastos, BigDecimal anulaciones, BigDecimal descuentos) {
    }
}
