package com.proyecto.supply_core.compra.solicitud.entity;

import java.time.LocalDateTime;

import org.hibernate.annotations.Immutable;

import com.proyecto.supply_core.compra.solicitud.enums.DecisionAprobacion;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Decisión de un aprobador sobre una solicitud (UC-05, tabla {@code aprobacion_solicitud}).
 * Inmutable: una solicitud devuelta y reenviada acumula varias filas (historial).
 */
@Entity
@Immutable
@Table(name = "aprobacion_solicitud")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AprobacionSolicitud {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "solicitud_compra_id", nullable = false, updatable = false)
    private Long solicitudCompraId;

    @Column(name = "usuario_aprobador_id", nullable = false, updatable = false)
    private Long usuarioAprobadorId;

    @Column(name = "politica_aprobacion_id", updatable = false)
    private Long politicaAprobacionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private DecisionAprobacion decision;

    @Column(length = 500, updatable = false)
    private String observaciones;

    /** Delegación con la que se decidió; {@code null} si decidió con su propio rol. */
    @Column(name = "delegacion_id", updatable = false)
    private Long delegacionId;

    @Column(name = "decidido_en", nullable = false, updatable = false)
    private LocalDateTime decididoEn;

    /**
     * Crea una decisión lista para insertar.
     *
     * @param solicitudCompraId    solicitud decidida
     * @param usuarioAprobadorId   quién decide
     * @param politicaAprobacionId política que exigía la aprobación
     * @param decision             decisión
     * @param observaciones        comentario (obligatorio si no es APROBADA)
     */
    public AprobacionSolicitud(Long solicitudCompraId, Long usuarioAprobadorId, Long politicaAprobacionId,
            DecisionAprobacion decision, String observaciones) {
        this(solicitudCompraId, usuarioAprobadorId, politicaAprobacionId, decision, observaciones, null);
    }

    /**
     * Crea una decisión lista para insertar, indicando si se tomó por delegación.
     *
     * @param solicitudCompraId    solicitud decidida
     * @param usuarioAprobadorId   quién decide
     * @param politicaAprobacionId política que exigía la aprobación
     * @param decision             decisión
     * @param observaciones        comentario (obligatorio si no es APROBADA)
     * @param delegacionId         delegación con la que decide ({@code null} si decide con su propio rol)
     */
    public AprobacionSolicitud(Long solicitudCompraId, Long usuarioAprobadorId, Long politicaAprobacionId,
            DecisionAprobacion decision, String observaciones, Long delegacionId) {
        this.solicitudCompraId = solicitudCompraId;
        this.usuarioAprobadorId = usuarioAprobadorId;
        this.politicaAprobacionId = politicaAprobacionId;
        this.decision = decision;
        this.observaciones = observaciones;
        this.delegacionId = delegacionId;
    }

    /** Fija la fecha de la decisión al insertar. */
    @PrePersist
    void alCrear() {
        if (decididoEn == null) {
            decididoEn = LocalDateTime.now();
        }
    }
}
