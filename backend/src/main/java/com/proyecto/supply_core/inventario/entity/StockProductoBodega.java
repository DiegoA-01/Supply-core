package com.proyecto.supply_core.inventario.entity;

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
 * Existencia real de un producto en una bodega (tabla {@code stock_producto_bodega}).
 * <p>Regla que no se negocia: solo {@code MovimientoInventarioService} modifica {@code cantidad},
 * siempre con la fila bloqueada ({@code SELECT ... FOR UPDATE}) y registrando el movimiento.
 * No existe endpoint para editar el stock.</p>
 */
@Entity
@Table(name = "stock_producto_bodega")
@Getter
@Setter
@NoArgsConstructor
public class StockProductoBodega {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "producto_id", nullable = false)
    private Producto producto;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bodega_id", nullable = false)
    private Bodega bodega;

    /** Nunca negativa (CHECK en la BD y validación en el Service). */
    @Column(nullable = false, precision = 18, scale = 4)
    private BigDecimal cantidad = BigDecimal.ZERO;
}
