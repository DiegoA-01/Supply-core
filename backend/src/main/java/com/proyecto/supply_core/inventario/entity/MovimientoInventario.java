package com.proyecto.supply_core.inventario.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.hibernate.annotations.Immutable;

import com.proyecto.supply_core.inventario.enums.TipoMovimiento;

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
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Movimiento de inventario (tabla {@code movimiento_inventario}): el kardex.
 * Inmutable: un error se corrige con otro movimiento (ajuste), nunca editando.
 */
@Entity
@Immutable
@Table(name = "movimiento_inventario")
@Getter
@Setter
@NoArgsConstructor
public class MovimientoInventario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "producto_id", nullable = false, updatable = false)
    private Producto producto;

    /** Bodega de la que sale el stock (salidas, ajuste negativo, devolución, traslado). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bodega_origen_id", updatable = false)
    private Bodega bodegaOrigen;

    /** Bodega a la que entra el stock (entrada, ajuste positivo, traslado). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bodega_destino_id", updatable = false)
    private Bodega bodegaDestino;

    @Column(name = "usuario_id", nullable = false, updatable = false)
    private Long usuarioId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private TipoMovimiento tipo;

    @Column(nullable = false, precision = 18, scale = 4, updatable = false)
    private BigDecimal cantidad;

    /** Saldo de la bodega afectada (en traslados: la de origen) después del movimiento. */
    @Column(name = "stock_resultante", nullable = false, precision = 18, scale = 4, updatable = false)
    private BigDecimal stockResultante;

    /** Solo en traslados: saldo de la bodega de destino después del movimiento (V6). */
    @Column(name = "stock_resultante_destino", precision = 18, scale = 4, updatable = false)
    private BigDecimal stockResultanteDestino;

    /** Origen del movimiento automático (p. ej. {@code RECEPCION}); {@code null} si es manual. */
    @Column(name = "referencia_tipo", length = 50, updatable = false)
    private String referenciaTipo;

    @Column(name = "referencia_id", updatable = false)
    private Long referenciaId;

    @Column(length = 250, updatable = false)
    private String motivo;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private LocalDateTime creadoEn;

    /** Fija la fecha del movimiento al insertar. */
    @PrePersist
    void alCrear() {
        if (creadoEn == null) {
            creadoEn = LocalDateTime.now();
        }
    }
}
