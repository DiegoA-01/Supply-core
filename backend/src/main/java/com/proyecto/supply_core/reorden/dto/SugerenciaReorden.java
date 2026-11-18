package com.proyecto.supply_core.reorden.dto;

import java.math.BigDecimal;

/**
 * Sugerencia de reposición de un producto que está por debajo de su stock mínimo. Solo informa: la persona decide
 * si crea la solicitud, qué cantidad pide y a quién le compra.
 * <p>La cantidad sugerida es {@code stockMaximo - stockActual - enOrdenes - enSolicitudes}, nunca negativa.</p>
 *
 * @param productoId       producto
 * @param codigo           código
 * @param nombre           nombre
 * @param unidad           unidad del producto (todas las cantidades van en esta unidad)
 * @param stockActual      total en todas las bodegas
 * @param stockMinimo      mínimo definido
 * @param stockMaximo      máximo definido ({@code null} si no tiene)
 * @param enOrdenes        lo que falta por recibir de órdenes vivas (pedido menos aceptado)
 * @param enSolicitudes    lo pedido en solicitudes pendientes, aprobadas o en RFQ que aún no son orden
 * @param cantidadSugerida cantidad a pedir para llegar al máximo ({@code null} si el producto no tiene máximo)
 * @param sinMaximo        {@code true} si el producto no tiene stock máximo y no se puede calcular
 * @param yaCubierto       {@code true} si lo que viene en camino ya alcanza el máximo y no hace falta pedir más
 * @param contratoSugerido contrato vigente más barato para este producto ({@code null} si no hay)
 */
public record SugerenciaReorden(Long productoId, String codigo, String nombre, String unidad, BigDecimal stockActual,
        BigDecimal stockMinimo, BigDecimal stockMaximo, BigDecimal enOrdenes, BigDecimal enSolicitudes,
        BigDecimal cantidadSugerida, boolean sinMaximo, boolean yaCubierto, ContratoSugerido contratoSugerido) {

    /**
     * Contrato vigente que fija el menor precio para el producto. Es una pista: la orden directa o la RFQ siguen
     * siendo decisión de la persona.
     *
     * @param contratoId           contrato
     * @param numero               número del contrato
     * @param proveedorId          proveedor
     * @param proveedorRazonSocial razón social
     * @param precioUnitario       precio pactado sin impuesto
     */
    public record ContratoSugerido(Long contratoId, String numero, Long proveedorId, String proveedorRazonSocial,
            BigDecimal precioUnitario) {
    }
}
