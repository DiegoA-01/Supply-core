package com.proyecto.supply_core.venta.entity;

import java.math.BigDecimal;

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
 * Línea de una venta (tabla {@code venta_linea}): foto fija del precio, el descuento, el impuesto y el costo del día
 * de la venta. No lleva empresa propia: hereda la de su venta.
 */
@Entity
@Table(name = "venta_linea")
@Getter
@Setter
@NoArgsConstructor
public class VentaLinea {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "venta_id", nullable = false, updatable = false)
    private Venta venta;

    @Column(name = "producto_id", nullable = false, updatable = false)
    private Long productoId;

    @Column(name = "unidad_medida_id", nullable = false, updatable = false)
    private Long unidadMedidaId;

    /** Cantidad en la unidad en que se vendió. */
    @Column(nullable = false, updatable = false, precision = 18, scale = 4)
    private BigDecimal cantidad;

    /** La misma cantidad en la unidad del producto (lo que sale del stock). */
    @Column(name = "cantidad_unidad_producto", nullable = false, updatable = false, precision = 18, scale = 4)
    private BigDecimal cantidadUnidadProducto;

    @Column(name = "precio_lista", nullable = false, updatable = false, precision = 18, scale = 2)
    private BigDecimal precioLista;

    @Column(name = "precio_unitario", nullable = false, updatable = false, precision = 18, scale = 2)
    private BigDecimal precioUnitario;

    @Column(name = "descuento_pct", nullable = false, updatable = false, precision = 5, scale = 2)
    private BigDecimal descuentoPct;

    @Column(name = "descuento_valor", nullable = false, updatable = false, precision = 18, scale = 2)
    private BigDecimal descuentoValor;

    @Column(name = "tarifa_impuesto_pct", nullable = false, updatable = false, precision = 5, scale = 2)
    private BigDecimal tarifaImpuestoPct;

    @Column(name = "precio_incluye_impuesto", nullable = false, updatable = false)
    private boolean precioIncluyeImpuesto;

    @Column(name = "base_gravable", nullable = false, updatable = false, precision = 18, scale = 2)
    private BigDecimal baseGravable;

    @Column(nullable = false, updatable = false, precision = 18, scale = 2)
    private BigDecimal impuesto;

    @Column(name = "total_linea", nullable = false, updatable = false, precision = 18, scale = 2)
    private BigDecimal totalLinea;

    /** Último costo de compra por unidad del producto (el costo promedio llega en la spec 008). */
    @Column(name = "costo_unitario", updatable = false, precision = 18, scale = 4)
    private BigDecimal costoUnitario;

    @Column(name = "autorizado_por_usuario_id", updatable = false)
    private Long autorizadoPorUsuarioId;
}
