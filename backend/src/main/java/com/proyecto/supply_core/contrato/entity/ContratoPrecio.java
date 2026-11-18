package com.proyecto.supply_core.contrato.entity;

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
 * Precio pactado de un producto dentro de un contrato (tabla {@code contrato_precio}). El precio es por unidad
 * del producto (la del inventario) y sin impuesto. No lleva {@code empresa_id}: hereda la empresa de su contrato.
 */
@Entity
@Table(name = "contrato_precio")
@Getter
@Setter
@NoArgsConstructor
public class ContratoPrecio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "contrato_id", nullable = false, updatable = false)
    private Contrato contrato;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "producto_id", nullable = false, updatable = false)
    private Producto producto;

    /** Precio por unidad del producto, sin impuesto. */
    @Column(name = "precio_unitario", nullable = false, precision = 18, scale = 4, updatable = false)
    private BigDecimal precioUnitario;

    @Column(name = "impuesto_pct", nullable = false, precision = 5, scale = 2, updatable = false)
    private BigDecimal impuestoPct;

    /** Tope de cantidad durante toda la vigencia, en unidades del producto; {@code null} = sin tope. */
    @Column(name = "cantidad_maxima", precision = 18, scale = 4, updatable = false)
    private BigDecimal cantidadMaxima;
}
