package com.proyecto.supply_core.venta.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.hibernate.annotations.TenantId;

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
 * Precio de un producto en una lista (tabla {@code precio_producto}). Es historial: nunca se sobrescribe; cambiar un
 * precio cierra la vigencia del anterior ({@code vigenteHasta}) y crea uno nuevo. El vigente es el que no tiene fin.
 */
@Entity
@Table(name = "precio_producto")
@Getter
@Setter
@NoArgsConstructor
public class PrecioProducto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Empresa dueña del registro. La asigna Hibernate al guardar y filtra toda consulta ({@code @TenantId}). */
    @TenantId
    @Column(name = "empresa_id", nullable = false, updatable = false)
    private Long empresaId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lista_precio_id", nullable = false, updatable = false)
    private ListaPrecio lista;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "producto_id", nullable = false, updatable = false)
    private Producto producto;

    /** Unidad en la que se fija el precio (en esta fase, la del producto). */
    @Column(name = "unidad_medida_id", nullable = false, updatable = false)
    private Long unidadMedidaId;

    @Column(nullable = false, precision = 18, scale = 2, updatable = false)
    private BigDecimal precio;

    /** Piso: vender por debajo exigirá autorización de supervisor (punto de venta). */
    @Column(name = "precio_minimo", precision = 18, scale = 2, updatable = false)
    private BigDecimal precioMinimo;

    @Column(name = "vigente_desde", nullable = false, updatable = false)
    private LocalDateTime vigenteDesde;

    /** {@code null} mientras sea el precio vigente. */
    @Column(name = "vigente_hasta")
    private LocalDateTime vigenteHasta;

    @Column(name = "creado_por_usuario_id", nullable = false, updatable = false)
    private Long creadoPorUsuarioId;

    @Column(length = 300, updatable = false)
    private String motivo;
}
