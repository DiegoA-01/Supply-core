package com.proyecto.supply_core.compra.cotizacion.entity;

import java.math.BigDecimal;

import com.proyecto.supply_core.compra.rfq.entity.RfqLinea;
import com.proyecto.supply_core.inventario.entity.Producto;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Precio ofertado para un producto de la RFQ (tabla {@code cotizacion_linea}).
 * <p>El proveedor cotiza en su propia presentación ({@code unidadOfertada}, p. ej. "caja x24") e indica
 * cuántas unidades de la RFQ trae cada una ({@code factorConversion}). Así Java compara precios en la
 * misma unidad ({@code precioUnitarioNormalizado}).</p>
 */
@Entity
@Table(name = "cotizacion_linea")
@Getter
@Setter
@NoArgsConstructor
public class CotizacionLinea {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cotizacion_id", nullable = false)
    private Cotizacion cotizacion;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "rfq_linea_id", nullable = false)
    private RfqLinea rfqLinea;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "producto_id", nullable = false)
    private Producto producto;

    @Column(name = "cantidad_ofertada", nullable = false, precision = 18, scale = 4)
    private BigDecimal cantidadOfertada;

    /** Presentación del proveedor, tal cual ("caja x24", "rollo 100 m"). */
    @Column(name = "unidad_ofertada", nullable = false, length = 50)
    private String unidadOfertada;

    /** Unidades de la RFQ que trae cada unidad ofertada (caja x24 frente a UND = 24). */
    @Column(name = "factor_conversion", nullable = false, precision = 18, scale = 6)
    private BigDecimal factorConversion;

    @Column(name = "precio_unitario_ofertado", nullable = false, precision = 18, scale = 4)
    private BigDecimal precioUnitarioOfertado;

    @Column(name = "descuento_pct", nullable = false, precision = 5, scale = 2)
    private BigDecimal descuentoPct = BigDecimal.ZERO;

    @Column(name = "impuesto_pct", nullable = false, precision = 5, scale = 2)
    private BigDecimal impuestoPct = BigDecimal.ZERO;

    /** Precio por unidad de la RFQ, con descuento y sin impuesto (calculado en Java). */
    @Column(name = "precio_unitario_normalizado", precision = 18, scale = 4)
    private BigDecimal precioUnitarioNormalizado;

    /** Neto de la línea: cantidad x precio − descuento, sin impuesto (calculado en Java). */
    @Column(precision = 18, scale = 2)
    private BigDecimal subtotal;
}
