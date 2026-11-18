package com.proyecto.supply_core.plataforma.entity;

import java.time.LocalDateTime;

import com.proyecto.supply_core.plataforma.enums.EstadoSuscripcion;

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
 * Suscripción de una empresa (tabla {@code suscripcion}): su plan y su estado. Hay una por empresa. La tabla
 * guarda {@code empresa_id} pero no usa {@code @TenantId}: la administra la plataforma, que trabaja sobre todas.
 */
@Entity
@Table(name = "suscripcion")
@Getter
@Setter
@NoArgsConstructor
public class Suscripcion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "empresa_id", nullable = false, unique = true, updatable = false)
    private Long empresaId;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "plan_id", nullable = false)
    private Plan plan;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoSuscripcion estado = EstadoSuscripcion.PRUEBA;

    @Column(name = "motivo_estado", length = 500)
    private String motivoEstado;

    @Column(nullable = false, updatable = false)
    private LocalDateTime desde;

    @Column(name = "actualizada_en")
    private LocalDateTime actualizadaEn;

    /** Fija la fecha de inicio al insertar. */
    @PrePersist
    void alCrear() {
        if (desde == null) {
            desde = LocalDateTime.now();
        }
    }
}
