package com.proyecto.supply_core.contrato.dto;

import java.math.BigDecimal;
import java.util.List;

import com.proyecto.supply_core.contrato.entity.Contrato;
import com.proyecto.supply_core.contrato.entity.ContratoPrecio;
import com.proyecto.supply_core.inventario.entity.Producto;

/**
 * Lo que costaría comprar una solicitud completa a un contrato, calculado por Java con los precios pactados.
 * Lo usan la oferta que ve el comprador y la emisión de la orden directa, así ambas dan el mismo resultado.
 *
 * @param contrato         contrato evaluado
 * @param lineas           líneas de la solicitud que el contrato cubre
 * @param sinCubrir        nombres de los productos de la solicitud que el contrato no incluye
 * @param subtotal         suma de netos
 * @param impuestos        suma de impuestos por línea
 * @param total            subtotal más impuestos
 * @param montoAprobado    monto estimado con el que se aprobó la solicitud (sin impuesto)
 * @param montoIncompleto  {@code true} si al aprobar la solicitud faltaba el costo de algún producto
 */
public record CalculoContrato(Contrato contrato, List<Linea> lineas, List<String> sinCubrir, BigDecimal subtotal,
        BigDecimal impuestos, BigDecimal total, BigDecimal montoAprobado, boolean montoIncompleto) {

    /**
     * Línea de la solicitud valorada al precio pactado.
     *
     * @param precio         precio pactado aplicado
     * @param producto       producto
     * @param cantidad       cantidad pedida, en unidades del producto
     * @param precioUnitario precio pactado sin impuesto
     * @param impuestoPct    impuesto %
     * @param subtotal       cantidad por precio, 2 decimales
     * @param disponible     lo que queda del tope antes de esta compra ({@code null} si no hay tope)
     * @param excedeTope     {@code true} si la cantidad pasa de lo disponible del tope
     */
    public record Linea(ContratoPrecio precio, Producto producto, BigDecimal cantidad, BigDecimal precioUnitario,
            BigDecimal impuestoPct, BigDecimal subtotal, BigDecimal disponible, boolean excedeTope) {
    }

    /**
     * Indica si el contrato incluye todos los productos de la solicitud.
     *
     * @return {@code true} si no queda ninguno sin cubrir
     */
    public boolean cubreTodo() {
        return sinCubrir.isEmpty();
    }

    /**
     * Indica si ninguna línea pasa del tope de su precio pactado.
     *
     * @return {@code true} si todas caben
     */
    public boolean dentroDelTope() {
        return lineas.stream().noneMatch(Linea::excedeTope);
    }

    /**
     * Indica si el costo al precio pactado no supera lo que se aprobó. Si faltaba el costo de algún producto, lo
     * aprobado no es comparable y se rechaza.
     *
     * @return {@code true} si el subtotal cabe en el monto aprobado
     */
    public boolean dentroDeLoAprobado() {
        return !montoIncompleto && montoAprobado != null && subtotal.compareTo(montoAprobado) <= 0;
    }

    /**
     * Indica si la orden directa se puede emitir con este contrato.
     *
     * @return {@code true} si cubre todo, cabe en el tope y no supera lo aprobado
     */
    public boolean puedeEmitirse() {
        return cubreTodo() && dentroDelTope() && dentroDeLoAprobado();
    }
}
