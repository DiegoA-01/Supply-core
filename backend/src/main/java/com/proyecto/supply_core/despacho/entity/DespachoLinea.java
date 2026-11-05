package com.proyecto.supply_core.despacho.entity;

import java.math.BigDecimal;

import com.proyecto.supply_core.catalogo.entity.UnidadMedida;
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
 * Línea de un despacho (tabla {@code despacho_linea}). La cantidad va en la unidad elegida
 * (p. ej. metros de un cable que se compra por rollo); {@code cantidadBase} es la cantidad
 * convertida a la unidad del producto, que es la que se descuenta del stock al confirmar.
 */
@Entity
@Table(name = "despacho_linea")
@Getter
@Setter
@NoArgsConstructor
public class DespachoLinea {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "despacho_id", nullable = false)
    private Despacho despacho;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "producto_id", nullable = false)
    private Producto producto;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "unidad_medida_id", nullable = false)
    private UnidadMedida unidadMedida;

    @Column(nullable = false, precision = 18, scale = 4)
    private BigDecimal cantidad;

    /** Cantidad en la unidad del producto (se fija al confirmar). */
    @Column(name = "cantidad_base", precision = 18, scale = 4)
    private BigDecimal cantidadBase;
}
