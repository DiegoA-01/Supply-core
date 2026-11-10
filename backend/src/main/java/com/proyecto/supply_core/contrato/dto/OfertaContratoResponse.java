package com.proyecto.supply_core.contrato.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Oferta de un contrato vigente para una solicitud aprobada: cuánto costaría al precio pactado y si se puede emitir
 * la orden directa. Solo informa: la persona decide si emite la orden directa o va por RFQ.
 *
 * @param contratoId           contrato
 * @param numero               número del contrato
 * @param proveedorId          proveedor
 * @param proveedorRazonSocial razón social
 * @param vigenteHasta         último día de vigencia
 * @param cubreTodo            si el contrato incluye todos los productos de la solicitud
 * @param productosSinCubrir   productos de la solicitud que el contrato no incluye
 * @param dentroDelTope        si todas las cantidades caben en lo que queda de cada tope
 * @param dentroDeLoAprobado   si el subtotal no supera el monto con que se aprobó la solicitud
 * @param puedeEmitirse        si la orden directa se puede emitir hoy con este contrato
 * @param subtotal             suma de netos al precio pactado
 * @param impuestos            suma de impuestos
 * @param total                subtotal más impuestos
 * @param montoAprobado        monto estimado con el que se aprobó la solicitud
 * @param lineas               líneas cubiertas por el contrato
 */
public record OfertaContratoResponse(Long contratoId, String numero, Long proveedorId, String proveedorRazonSocial,
        LocalDate vigenteHasta, boolean cubreTodo, List<String> productosSinCubrir, boolean dentroDelTope,
        boolean dentroDeLoAprobado, boolean puedeEmitirse, BigDecimal subtotal, BigDecimal impuestos,
        BigDecimal total, BigDecimal montoAprobado, List<Linea> lineas) {

    /**
     * Convierte el cálculo en la oferta que ve el comprador.
     *
     * @param c cálculo del contrato contra la solicitud
     * @return DTO
     */
    public static OfertaContratoResponse of(CalculoContrato c) {
        return new OfertaContratoResponse(c.contrato().getId(), c.contrato().getNumero(),
                c.contrato().getProveedor().getId(), c.contrato().getProveedor().getRazonSocial(),
                c.contrato().getVigenteHasta(), c.cubreTodo(), c.sinCubrir(), c.dentroDelTope(),
                c.dentroDeLoAprobado(), c.puedeEmitirse(), c.subtotal(), c.impuestos(), c.total(), c.montoAprobado(),
                c.lineas().stream().map(l -> new Linea(l.producto().getId(), l.producto().getNombre(), l.cantidad(),
                        l.precioUnitario(), l.impuestoPct(), l.subtotal(), l.disponible(), l.excedeTope())).toList());
    }

    /**
     * Línea de la oferta.
     *
     * @param productoId     producto
     * @param productoNombre nombre
     * @param cantidad       cantidad pedida en unidades del producto
     * @param precioUnitario precio pactado sin impuesto
     * @param impuestoPct    impuesto %
     * @param subtotal       neto de la línea
     * @param disponible     lo que queda del tope ({@code null} si no hay tope)
     * @param excedeTope     si la cantidad pasa de lo que queda del tope
     */
    @Schema(name = "OfertaContratoLinea")
    public record Linea(Long productoId, String productoNombre, BigDecimal cantidad, BigDecimal precioUnitario,
            BigDecimal impuestoPct, BigDecimal subtotal, BigDecimal disponible, boolean excedeTope) {
    }
}
