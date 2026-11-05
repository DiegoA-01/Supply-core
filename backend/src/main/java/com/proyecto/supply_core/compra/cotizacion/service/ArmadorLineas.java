package com.proyecto.supply_core.compra.cotizacion.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.proyecto.supply_core.common.exception.NegocioException;
import com.proyecto.supply_core.compra.cotizacion.dto.LineaCotizacionRequest;
import com.proyecto.supply_core.compra.cotizacion.entity.Cotizacion;
import com.proyecto.supply_core.compra.cotizacion.entity.CotizacionLinea;
import com.proyecto.supply_core.compra.rfq.entity.Rfq;
import com.proyecto.supply_core.compra.rfq.entity.RfqLinea;

/**
 * Arma las líneas de una cotización a partir de precios recibidos (formulario del proveedor o digitación
 * del comprador) y recalcula los montos. Quien llama debe haber borrado antes las líneas viejas.
 */
public final class ArmadorLineas {

    /** Clase de utilidades: no se instancia. */
    private ArmadorLineas() {
    }

    /**
     * Agrega una línea por cada precio recibido y recalcula los montos con {@link CalculadoraCotizacion}.
     *
     * @param c      cotización sin líneas
     * @param rfq    RFQ con sus líneas
     * @param lineas precios recibidos (puede ser {@code null})
     * @throws NegocioException si una línea no pertenece a la RFQ
     */
    public static void armar(Cotizacion c, Rfq rfq, List<LineaCotizacionRequest> lineas) {
        Map<Long, RfqLinea> lineasRfq = rfq.getLineas().stream()
                .collect(Collectors.toMap(RfqLinea::getId, Function.identity()));
        for (LineaCotizacionRequest l : lineas == null ? List.<LineaCotizacionRequest>of() : lineas) {
            RfqLinea rl = lineasRfq.get(l.rfqLineaId());
            if (rl == null) {
                throw new NegocioException("LINEA_RFQ_INVALIDA",
                        "La línea " + l.rfqLineaId() + " no pertenece a la " + rfq.getConsecutivo());
            }
            c.agregarLinea(nueva(rl, l.cantidadOfertada(), l.unidadOfertada().trim(), l.factorConversion(),
                    l.precioUnitarioOfertado(), l.descuentoPct(), l.impuestoPct()));
        }
        CalculadoraCotizacion.recalcular(c);
    }

    /**
     * Crea una línea (descuento e impuesto nulos = 0).
     *
     * @param rl        línea de la RFQ
     * @param cantidad  cantidad ofertada
     * @param unidad    presentación ofertada
     * @param factor    unidades de la RFQ por unidad ofertada
     * @param precio    precio unitario ofertado
     * @param descuento descuento %
     * @param impuesto  impuesto %
     * @return línea sin asociar
     */
    public static CotizacionLinea nueva(RfqLinea rl, BigDecimal cantidad, String unidad, BigDecimal factor,
            BigDecimal precio, BigDecimal descuento, BigDecimal impuesto) {
        CotizacionLinea linea = new CotizacionLinea();
        linea.setRfqLinea(rl);
        linea.setProducto(rl.getProducto());
        linea.setCantidadOfertada(cantidad);
        linea.setUnidadOfertada(unidad);
        linea.setFactorConversion(factor);
        linea.setPrecioUnitarioOfertado(precio);
        linea.setDescuentoPct(descuento == null ? BigDecimal.ZERO : descuento);
        linea.setImpuestoPct(impuesto == null ? BigDecimal.ZERO : impuesto);
        return linea;
    }
}
