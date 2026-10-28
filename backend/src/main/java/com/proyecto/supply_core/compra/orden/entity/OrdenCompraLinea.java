package com.proyecto.supply_core.compra.orden.entity;

import java.math.BigDecimal;

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
 * Línea de una orden (tabla {@code orden_compra_linea}). Cantidad y precio van en la <b>unidad del
 * producto</b>, la misma del inventario: así la recepción suma el stock sin conversiones.
 */
@Entity
@Table(name = "orden_compra_linea")
@Getter
@Setter
@NoArgsConstructor
public class OrdenCompraLinea {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "orden_compra_id", nullable = false, updatable = false)
    private OrdenCompra orden;

    /** Línea cotizada de la que viene. */
    @Column(name = "cotizacion_linea_id", updatable = false)
    private Long cotizacionLineaId;

    /** Precio pactado de contrato del que viene (orden directa); {@code null} en las demás. Descuenta del tope. */
    @Column(name = "contrato_precio_id", updatable = false)
    private Long contratoPrecioId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "producto_id", nullable = false, updatable = false)
    private Producto producto;

    /** Cantidad en la unidad del producto. */
    @Column(nullable = false, precision = 18, scale = 4, updatable = false)
    private BigDecimal cantidad;

    /** Precio por unidad del producto, con descuento y sin impuesto. */
    @Column(name = "precio_unitario", nullable = false, precision = 18, scale = 4, updatable = false)
    private BigDecimal precioUnitario;

    @Column(name = "impuesto_pct", nullable = false, precision = 5, scale = 2, updatable = false)
    private BigDecimal impuestoPct;

    /** Neto de la línea (sin impuesto). */
    @Column(nullable = false, precision = 18, scale = 2, updatable = false)
    private BigDecimal subtotal;
}
