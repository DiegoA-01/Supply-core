package com.proyecto.supply_core.inventario.entity;

import java.math.BigDecimal;

import com.proyecto.supply_core.catalogo.entity.CategoriaProducto;
import com.proyecto.supply_core.catalogo.entity.UnidadMedida;

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
 * Producto del catálogo (tabla {@code producto}, UC-02). El stock NO vive aquí: está por bodega
 * en {@link StockProductoBodega} y solo lo cambia {@code MovimientoInventarioService}.
 * Nunca se borra: se desactiva y conserva su historial.
 */
@Entity
@Table(name = "producto")
@Getter
@Setter
@NoArgsConstructor
public class Producto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "categoria_producto_id", nullable = false)
    private CategoriaProducto categoria;

    /** Unidad en la que se lleva el stock y se expresan los movimientos. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "unidad_medida_id", nullable = false)
    private UnidadMedida unidadMedida;

    /** Único, en mayúsculas. */
    @Column(nullable = false, unique = true, length = 50)
    private String codigo;

    @Column(nullable = false, length = 200)
    private String nombre;

    @Column(length = 500)
    private String descripcion;

    /** Total entre todas las bodegas por debajo del cual se genera alerta (UC flujo paso 1). */
    @Column(name = "stock_minimo", nullable = false, precision = 18, scale = 4)
    private BigDecimal stockMinimo = BigDecimal.ZERO;

    @Column(name = "stock_maximo", precision = 18, scale = 4)
    private BigDecimal stockMaximo;

    /** Precio de referencia (para detectar precios atípicos en cotizaciones). */
    @Column(name = "costo_referencia", precision = 18, scale = 4)
    private BigDecimal costoReferencia;

    @Column(nullable = false)
    private boolean activo = true;
}
