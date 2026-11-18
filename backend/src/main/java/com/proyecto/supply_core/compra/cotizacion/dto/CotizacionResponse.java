package com.proyecto.supply_core.compra.cotizacion.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.proyecto.supply_core.compra.cotizacion.entity.Cotizacion;
import com.proyecto.supply_core.compra.cotizacion.entity.CotizacionLinea;
import com.proyecto.supply_core.compra.cotizacion.enums.EstadoCotizacion;
import com.proyecto.supply_core.compra.cotizacion.enums.OrigenCotizacion;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Cotización. Si {@code sellada} es {@code true} (comprador mirando una RFQ todavía ABIERTA), solo se
 * muestra quién envió y cuándo: montos, condiciones y líneas van vacíos hasta el cierre.
 *
 * @param id                     id
 * @param rfqId                  RFQ
 * @param proveedorId            proveedor
 * @param proveedorRazonSocial   razón social
 * @param estado                 estado
 * @param origen                 formulario o archivo
 * @param moneda                 moneda
 * @param plazoEntregaDias       días de entrega
 * @param garantiaMeses          meses de garantía
 * @param validezHasta           validez de los precios
 * @param condicionesComerciales condiciones
 * @param subtotal               suma de netos (calculado en Java)
 * @param impuestos              suma de impuestos (calculado en Java)
 * @param total                  subtotal + impuestos
 * @param documentoOriginalId    archivo original subido por el proveedor
 * @param enviadoEn              fecha de envío
 * @param sellada                {@code true} si los precios aún no se pueden ver
 * @param lineas                 precios por producto
 */
public record CotizacionResponse(Long id, Long rfqId, Long proveedorId, String proveedorRazonSocial,
        EstadoCotizacion estado, OrigenCotizacion origen, String moneda, Integer plazoEntregaDias,
        Integer garantiaMeses, LocalDate validezHasta, String condicionesComerciales, BigDecimal subtotal,
        BigDecimal impuestos, BigDecimal total, Long documentoOriginalId, LocalDateTime enviadoEn, boolean sellada,
        List<Linea> lineas) {

    /**
     * Respuesta completa.
     *
     * @param c cotización con proveedor y líneas cargados
     * @return DTO
     */
    public static CotizacionResponse of(Cotizacion c) {
        return new CotizacionResponse(c.getId(), c.getRfq().getId(), c.getProveedor().getId(),
                c.getProveedor().getRazonSocial(), c.getEstado(), c.getOrigen(), c.getMoneda(),
                c.getPlazoEntregaDias(), c.getGarantiaMeses(), c.getValidezHasta(), c.getCondicionesComerciales(),
                c.getSubtotal(), c.getImpuestos(), c.getTotal(), c.getDocumentoOriginalId(), c.getEnviadoEn(), false,
                c.getLineas().stream().map(Linea::of).toList());
    }

    /**
     * Respuesta sellada: solo quién envió y cuándo.
     *
     * @param c cotización con proveedor cargado
     * @return DTO sin precios ni condiciones
     */
    public static CotizacionResponse sellada(Cotizacion c) {
        return new CotizacionResponse(c.getId(), c.getRfq().getId(), c.getProveedor().getId(),
                c.getProveedor().getRazonSocial(), c.getEstado(), c.getOrigen(), null, null, null, null, null, null,
                null, null, null, c.getEnviadoEn(), true, List.of());
    }

    /**
     * Línea cotizada.
     *
     * @param id                        id
     * @param rfqLineaId                línea de la RFQ
     * @param productoId                producto
     * @param productoCodigo            código
     * @param cantidadOfertada          cantidad ofertada
     * @param unidadOfertada            presentación del proveedor
     * @param factorConversion          unidades de la RFQ por unidad ofertada
     * @param precioUnitarioOfertado    precio de la presentación
     * @param descuentoPct              descuento %
     * @param impuestoPct               impuesto %
     * @param precioUnitarioNormalizado precio por unidad de la RFQ (Java)
     * @param subtotal                  neto de la línea (Java)
     */
    @Schema(name = "CotizacionLinea")
    public record Linea(Long id, Long rfqLineaId, Long productoId, String productoCodigo, BigDecimal cantidadOfertada,
            String unidadOfertada, BigDecimal factorConversion, BigDecimal precioUnitarioOfertado,
            BigDecimal descuentoPct, BigDecimal impuestoPct, BigDecimal precioUnitarioNormalizado,
            BigDecimal subtotal) {

        /**
         * Convierte la entidad.
         *
         * @param l línea
         * @return DTO
         */
        static Linea of(CotizacionLinea l) {
            return new Linea(l.getId(), l.getRfqLinea().getId(), l.getProducto().getId(), l.getProducto().getCodigo(),
                    l.getCantidadOfertada(), l.getUnidadOfertada(), l.getFactorConversion(),
                    l.getPrecioUnitarioOfertado(), l.getDescuentoPct(), l.getImpuestoPct(),
                    l.getPrecioUnitarioNormalizado(), l.getSubtotal());
        }
    }
}
