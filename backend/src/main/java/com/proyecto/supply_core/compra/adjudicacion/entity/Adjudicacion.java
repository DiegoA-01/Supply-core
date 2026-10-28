package com.proyecto.supply_core.compra.adjudicacion.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.proyecto.supply_core.compra.adjudicacion.enums.EstadoAdjudicacion;
import com.proyecto.supply_core.compra.cotizacion.entity.Cotizacion;
import com.proyecto.supply_core.compra.rfq.entity.Rfq;

import org.hibernate.annotations.TenantId;
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
 * Decisión de a quién se le compra (UC-09, tabla {@code adjudicacion}). Nunca se borra: una rechazada queda
 * en el historial y se puede crear otra.
 */
@Entity
@Table(name = "adjudicacion")
@Getter
@Setter
@NoArgsConstructor
public class Adjudicacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Empresa dueña del registro. La asigna Hibernate al guardar y filtra toda consulta ({@code @TenantId}). */
    @TenantId
    @Column(name = "empresa_id", nullable = false, updatable = false)
    private Long empresaId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "rfq_id", nullable = false, updatable = false)
    private Rfq rfq;

    /** Análisis publicado en que se basó. */
    @Column(name = "analisis_cotizacion_id", nullable = false, updatable = false)
    private Long analisisId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cotizacion_seleccionada_id", nullable = false, updatable = false)
    private Cotizacion cotizacion;

    /** Comprador que adjudicó. */
    @Column(name = "usuario_id", nullable = false, updatable = false)
    private Long usuarioId;

    /** Total de la cotización elegida (con impuestos). */
    @Column(name = "monto_total", nullable = false, precision = 18, scale = 2, updatable = false)
    private BigDecimal montoTotal;

    /** {@code true} si eligió la recomendada (posición 1 del ranking). */
    @Column(name = "sigue_recomendacion", nullable = false, updatable = false)
    private boolean sigueRecomendacion;

    /** Obligatoria si no sigue la recomendación. */
    @Column(length = 1000, updatable = false)
    private String justificacion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoAdjudicacion estado = EstadoAdjudicacion.PENDIENTE_APROBACION;

    /** Política ADJUDICACION que exigió aprobación; {@code null} si nació aprobada. */
    @Column(name = "politica_aprobacion_id", updatable = false)
    private Long politicaAprobacionId;

    @Column(name = "aprobado_por_usuario_id")
    private Long aprobadoPorUsuarioId;

    /** Delegación con la que se aprobó; {@code null} si el aprobador usó su propio rol. */
    @Column(name = "delegacion_id")
    private Long delegacionId;

    @Column(name = "aprobado_en")
    private LocalDateTime aprobadoEn;

    @Column(name = "observaciones_aprobacion", length = 500)
    private String observacionesAprobacion;

    /** Fecha en que el comprador adjudicó. */
    @Column(name = "decidido_en", nullable = false, updatable = false)
    private LocalDateTime decididoEn;

    /** Fija la fecha de la adjudicación al insertar. */
    @PrePersist
    void alCrear() {
        if (decididoEn == null) {
            decididoEn = LocalDateTime.now();
        }
    }
}
