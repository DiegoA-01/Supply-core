package com.proyecto.supply_core.reporte.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Resultado del cuadre diario de un día (spec 009).
 *
 * @param fecha              día revisado
 * @param cuadra             {@code true} si ventas, inventario y caja coinciden y no hay turnos olvidados
 * @param ventasConfirmadas  lo que las ventas del día cobraron por medios que entran a la caja
 * @param ventasEnCaja       lo que la caja registró por esas ventas
 * @param unidadesVendidas   unidades vendidas
 * @param unidadesSalida     salidas de inventario por esas ventas
 * @param turnosAbiertos24h  turnos con más de 24 horas abiertos
 * @param ventas             ventas que no cuadran
 * @param turnos             turnos olvidados
 * @param revisadoEn         cuándo se revisó
 */
public record CuadreResponse(LocalDate fecha, boolean cuadra, BigDecimal ventasConfirmadas, BigDecimal ventasEnCaja,
        BigDecimal unidadesVendidas, BigDecimal unidadesSalida, int turnosAbiertos24h, List<VentaDescuadrada> ventas,
        List<TurnoOlvidado> turnos, LocalDateTime revisadoEn) {

    /**
     * @param ventaId      venta
     * @param consecutivo  consecutivo
     * @param cobrado      lo cobrado por medios de caja
     * @param enCaja       lo registrado en la caja
     * @param vendidas     unidades vendidas
     * @param salidas      salidas de inventario
     */
    public record VentaDescuadrada(Long ventaId, String consecutivo, BigDecimal cobrado, BigDecimal enCaja,
            BigDecimal vendidas, BigDecimal salidas) {
    }

    /**
     * @param turnoId    turno
     * @param caja       caja
     * @param cajero     cajero
     * @param abiertoEn  apertura
     */
    public record TurnoOlvidado(Long turnoId, String caja, String cajero, LocalDateTime abiertoEn) {
    }
}
