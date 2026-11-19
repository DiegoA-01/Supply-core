package com.proyecto.supply_core.inventario.entity;

import java.math.BigDecimal;

import org.hibernate.annotations.TenantId;

import com.proyecto.supply_core.empresa.entity.Sede;

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
 * Mínimo y máximo de un producto en una sede (tabla {@code producto_sede}). Cada sede se maneja como un punto
 * independiente; sin fila, la sede se guía solo por su consumo.
 */
@Entity
@Table(name = "producto_sede")
@Getter
@Setter
@NoArgsConstructor
public class ProductoSede {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Empresa dueña del registro. La asigna Hibernate al guardar y filtra toda consulta ({@code @TenantId}). */
    @TenantId
    @Column(name = "empresa_id", nullable = false, updatable = false)
    private Long empresaId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "producto_id", nullable = false, updatable = false)
    private Producto producto;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sede_id", nullable = false, updatable = false)
    private Sede sede;

    /** Por debajo de este stock en la sede se avisa (0 = sin mínimo propio). */
    @Column(name = "stock_minimo", nullable = false, precision = 18, scale = 4)
    private BigDecimal stockMinimo = BigDecimal.ZERO;

    /** Tope de lo que conviene tener en la sede; {@code null} si no tiene. */
    @Column(name = "stock_maximo", precision = 18, scale = 4)
    private BigDecimal stockMaximo;
}
