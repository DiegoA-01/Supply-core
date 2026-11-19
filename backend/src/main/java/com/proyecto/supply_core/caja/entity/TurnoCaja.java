package com.proyecto.supply_core.caja.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.TenantId;
import org.hibernate.type.SqlTypes;

import com.proyecto.supply_core.caja.enums.EstadoTurno;

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
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Turno de un cajero en una caja (tabla {@code turno_caja}, V33). Los esperados y las diferencias los calcula Java al
 * cerrar y se guardan para que el histórico no cambie. Los mapas por medio de pago se guardan como JSON.
 */
@Entity
@Table(name = "turno_caja")
@Getter
@Setter
@NoArgsConstructor
public class TurnoCaja {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Empresa dueña del registro. La asigna Hibernate al guardar y filtra toda consulta ({@code @TenantId}). */
    @TenantId
    @Column(name = "empresa_id", nullable = false, updatable = false)
    private Long empresaId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "caja_id", nullable = false, updatable = false)
    private Caja caja;

    @Column(name = "cajero_usuario_id", nullable = false, updatable = false)
    private Long cajeroUsuarioId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private EstadoTurno estado = EstadoTurno.ABIERTO;

    @Column(name = "abierto_en", nullable = false, updatable = false)
    private LocalDateTime abiertoEn;

    @Column(name = "base_apertura", nullable = false, precision = 18, scale = 2, updatable = false)
    private BigDecimal baseApertura;

    @Column(name = "cerrado_en")
    private LocalDateTime cerradoEn;

    @Column(name = "contado_efectivo", precision = 18, scale = 2)
    private BigDecimal contadoEfectivo;

    /** Contado por medio distinto al efectivo (JSON {@code {"TARJETA_DEBITO": 120000, ...}}). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "contado_otros")
    private String contadoOtros;

    @Column(name = "esperado_efectivo", precision = 18, scale = 2)
    private BigDecimal esperadoEfectivo;

    /** Esperado por medio distinto al efectivo (JSON). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "esperado_otros")
    private String esperadoOtros;

    /** Suma de las diferencias de todos los medios (contado − esperado). */
    @Column(precision = 18, scale = 2)
    private BigDecimal diferencia;

    /** Diferencia por medio de pago (JSON). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "diferencias")
    private String diferencias;

    /** Alguna diferencia pasó la tolerancia: un supervisor debe explicarla y aprobarla. */
    @Column(name = "requiere_aprobacion", nullable = false)
    private boolean requiereAprobacion;

    @Column(name = "observacion_cierre", length = 500)
    private String observacionCierre;

    @Column(name = "aprobado_por_usuario_id")
    private Long aprobadoPorUsuarioId;

    @Column(name = "aprobado_en")
    private LocalDateTime aprobadoEn;

    @Column(name = "explicacion_aprobacion", length = 500)
    private String explicacionAprobacion;

    /** Bloqueo optimista: dos cierres o aprobaciones a la vez no se pisan. */
    @Version
    @Column(nullable = false)
    private int version;
}
