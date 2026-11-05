package com.proyecto.supply_core.recepcion.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.proyecto.supply_core.compra.orden.entity.OrdenCompraLinea;
import com.proyecto.supply_core.recepcion.enums.EstadoRecepcionLinea;
import com.proyecto.supply_core.recepcion.enums.MotivoRechazo;

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
 * Lo que pasó con una línea de la orden en una recepción (tabla {@code recepcion_linea}). Todas las
 * cantidades van en la unidad del producto. Hereda la empresa de su recepción.
 * <p>{@code recibida} = lo que llegó; {@code rechazada} = parte de lo recibido que no sirve (con motivo);
 * {@code aceptada} = lo bueno que cabe en lo pedido (la única cantidad que sube el stock);
 * {@code excedente} = lo bueno que sobra sobre lo pedido (no suma stock hasta que compras decida);
 * {@code faltante} = lo que aún falta de la línea después de esta recepción.</p>
 */
@Entity
@Table(name = "recepcion_linea")
@Getter
@Setter
@NoArgsConstructor
public class RecepcionLinea {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recepcion_id", nullable = false, updatable = false)
    private Recepcion recepcion;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "orden_compra_linea_id", nullable = false, updatable = false)
    private OrdenCompraLinea ordenLinea;

    @Column(name = "cantidad_recibida", nullable = false, precision = 18, scale = 4, updatable = false)
    private BigDecimal cantidadRecibida;

    @Column(name = "cantidad_faltante", nullable = false, precision = 18, scale = 4, updatable = false)
    private BigDecimal cantidadFaltante;

    @Column(name = "cantidad_rechazada", nullable = false, precision = 18, scale = 4, updatable = false)
    private BigDecimal cantidadRechazada;

    /** Sube con la resolución del excedente cuando compras decide quedarse con él. */
    @Column(name = "cantidad_aceptada", nullable = false, precision = 18, scale = 4)
    private BigDecimal cantidadAceptada;

    @Column(name = "cantidad_excedente", nullable = false, precision = 18, scale = 4, updatable = false)
    private BigDecimal cantidadExcedente;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoRecepcionLinea estado = EstadoRecepcionLinea.ACEPTADA;

    @Enumerated(EnumType.STRING)
    @Column(name = "motivo_rechazo", updatable = false)
    private MotivoRechazo motivoRechazo;

    @Column(name = "observacion_calidad", length = 500, updatable = false)
    private String observacionCalidad;

    /** Quién resolvió el excedente. */
    @Column(name = "resuelto_por_usuario_id")
    private Long resueltoPorUsuarioId;

    @Column(name = "resuelto_en")
    private LocalDateTime resueltoEn;

    @Column(name = "motivo_resolucion", length = 500)
    private String motivoResolucion;
}
