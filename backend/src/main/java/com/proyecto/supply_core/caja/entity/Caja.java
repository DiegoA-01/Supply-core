package com.proyecto.supply_core.caja.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.hibernate.annotations.TenantId;

import com.proyecto.supply_core.empresa.entity.Sede;
import com.proyecto.supply_core.inventario.entity.Bodega;

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

/** Punto de venta de una sede que descuenta de una sola bodega (tabla {@code caja}, V33, decisión D7). */
@Entity
@Table(name = "caja")
@Getter
@Setter
@NoArgsConstructor
public class Caja {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Empresa dueña del registro. La asigna Hibernate al guardar y filtra toda consulta ({@code @TenantId}). */
    @TenantId
    @Column(name = "empresa_id", nullable = false, updatable = false)
    private Long empresaId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sede_id", nullable = false)
    private Sede sede;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bodega_id", nullable = false)
    private Bodega bodega;

    @Column(nullable = false, length = 100)
    private String nombre;

    /** Prefijo de los consecutivos de venta de esta caja (por ejemplo {@code C1}). */
    @Column(name = "prefijo_consecutivo", nullable = false, length = 10)
    private String prefijoConsecutivo;

    /** Efectivo a partir del cual se avisa que hay que retirar a la bóveda (0 = sin aviso). */
    @Column(name = "tope_efectivo", nullable = false, precision = 18, scale = 2)
    private BigDecimal topeEfectivo = BigDecimal.ZERO;

    @Column(nullable = false)
    private boolean activa = true;

    @Column(name = "creado_en", nullable = false, insertable = false, updatable = false)
    private LocalDateTime creadoEn;
}
