package com.proyecto.supply_core.reorden.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.proyecto.supply_core.reorden.enums.EstadoReabastecimiento;

/**
 * Plan de reabastecimiento de un producto. Todas las cantidades van en la unidad del producto y las calcula Java;
 * la persona decide si pide, cuánto y a quién.
 *
 * @param productoId          producto
 * @param codigo              código
 * @param nombre              nombre
 * @param categoria           categoría
 * @param unidad              unidad del producto
 * @param stock               stock total hoy
 * @param stockMinimo         mínimo definido
 * @param stockMaximo         máximo definido o {@code null}
 * @param porRecibir          lo que falta por llegar de órdenes vivas
 * @param enSolicitudes       lo pedido en solicitudes que aún no son orden
 * @param consumoDiario       salidas promedio por día de calendario en el período analizado
 * @param diasCobertura       días que alcanza el stock de hoy al ritmo actual ({@code null} sin consumo)
 * @param fechaAgotamiento    día en que se acabaría el stock de hoy ({@code null} sin consumo)
 * @param plazoEntregaDias    días hábiles que tarda en llegar un pedido
 * @param plazoPropio         {@code true} si el plazo es del producto; {@code false} si es el de la empresa
 * @param diasSeguridad       días hábiles de colchón
 * @param plazoHistorico      promedio de días hábiles que tardaron de verdad las entregas ({@code null} sin datos)
 * @param entregasHistoricas  cuántas entregas se usaron para el promedio
 * @param llegadaSiPidoHoy    día en que llegaría un pedido hecho hoy
 * @param puntoPedido         stock con el que hay que pedir: consumo durante el plazo y la seguridad (nunca menor
 *                            que el mínimo)
 * @param pedirAntesDe        último día hábil para pedir sin quedarse corto ({@code null} sin consumo)
 * @param cantidadSugerida    cantidad para cubrir plazo, seguridad y los días de cobertura de la empresa
 * @param estado              urgencia
 * @param motivo              explicación corta del estado
 * @param sobrante            stock que la sede puede ceder sin quedarse corta (por encima de lo que necesita para
 *                            plazo, seguridad y cobertura, y de su mínimo)
 * @param cantidadTraslado    cuánto traer de otra sede antes de comprar (0 si no aplica; solo en la vista por sede)
 * @param trasladoDesdeSedeId sede de la que conviene trasladar ({@code null} si no aplica)
 * @param trasladoDesdeSede   nombre de esa sede ({@code null} si no aplica)
 * @param cantidadComprar     lo que queda por comprar después del traslado
 */
public record ReabastecimientoItem(Long productoId, String codigo, String nombre, String categoria, String unidad,
        BigDecimal stock, BigDecimal stockMinimo, BigDecimal stockMaximo, BigDecimal porRecibir,
        BigDecimal enSolicitudes, BigDecimal consumoDiario, BigDecimal diasCobertura, LocalDate fechaAgotamiento,
        int plazoEntregaDias, boolean plazoPropio, int diasSeguridad, BigDecimal plazoHistorico,
        int entregasHistoricas, LocalDate llegadaSiPidoHoy, BigDecimal puntoPedido, LocalDate pedirAntesDe,
        BigDecimal cantidadSugerida, EstadoReabastecimiento estado, String motivo, BigDecimal sobrante,
        BigDecimal cantidadTraslado, Long trasladoDesdeSedeId, String trasladoDesdeSede, BigDecimal cantidadComprar) {

    /**
     * Copia con un traslado sugerido: se trae de otra sede antes de comprar.
     *
     * @param cantidad cantidad a trasladar (no mayor que la sugerida)
     * @param sedeId   sede de origen
     * @param sede     nombre de la sede de origen
     * @return copia con el traslado y lo que queda por comprar
     */
    public ReabastecimientoItem conTraslado(BigDecimal cantidad, Long sedeId, String sede) {
        return new ReabastecimientoItem(productoId, codigo, nombre, categoria, unidad, stock, stockMinimo, stockMaximo,
                porRecibir, enSolicitudes, consumoDiario, diasCobertura, fechaAgotamiento, plazoEntregaDias,
                plazoPropio, diasSeguridad, plazoHistorico, entregasHistoricas, llegadaSiPidoHoy, puntoPedido,
                pedirAntesDe, cantidadSugerida, estado, motivo, sobrante, cantidad, sedeId, sede,
                cantidadSugerida.subtract(cantidad).max(BigDecimal.ZERO));
    }
}
