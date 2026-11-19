package com.proyecto.supply_core.catalogoproveedor.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import org.hibernate.annotations.TenantId;

import com.proyecto.supply_core.catalogoproveedor.enums.EstadoEquivalencia;
import com.proyecto.supply_core.inventario.entity.Producto;
import com.proyecto.supply_core.proveedor.entity.Proveedor;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * Artículo del catálogo de un proveedor (tabla {@code catalogo_item}). Lo sube el proveedor; la equivalencia con el
 * inventario ({@code producto}, {@code estadoEquivalencia}) es de la empresa y el proveedor nunca la ve.
 */
@Entity
@Table(name = "catalogo_item")
@Getter
@Setter
@NoArgsConstructor
public class CatalogoItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Empresa dueña del registro. La asigna Hibernate al guardar y filtra toda consulta ({@code @TenantId}). */
    @TenantId
    @Column(name = "empresa_id", nullable = false, updatable = false)
    private Long empresaId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "proveedor_id", nullable = false, updatable = false)
    private Proveedor proveedor;

    @Column(name = "codigo_proveedor", nullable = false, length = 80, updatable = false)
    private String codigoProveedor;

    @Column(nullable = false, length = 300)
    private String descripcion;

    @Column(length = 100)
    private String marca;

    /** Unidad como la escribe el proveedor (UND, M, CAJA24…). */
    @Column(length = 30)
    private String unidad;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal precio;

    @Column(name = "incluye_iva", nullable = false)
    private boolean incluyeIva;

    @Column(name = "vigente_hasta")
    private LocalDate vigenteHasta;

    /** {@code false} si el proveedor lo quitó de su último catálogo. */
    @Column(nullable = false)
    private boolean activo = true;

    /** Producto del inventario al que corresponde (sugerido o confirmado). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "producto_id")
    private Producto producto;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_equivalencia", nullable = false)
    private EstadoEquivalencia estadoEquivalencia = EstadoEquivalencia.SIN_REVISAR;

    /** Qué tanto se parece al producto sugerido, de 0 a 1. */
    @Column(precision = 5, scale = 2)
    private BigDecimal similitud;

    @Column(name = "revisado_por_usuario_id")
    private Long revisadoPorUsuarioId;

    @Column(name = "actualizado_en", nullable = false)
    private LocalDateTime actualizadoEn;
}
