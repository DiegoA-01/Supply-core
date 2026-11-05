package com.proyecto.supply_core.delegacion.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.hibernate.annotations.TenantId;

import com.proyecto.supply_core.delegacion.enums.EstadoDelegacion;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Delegación de aprobación (paso 18-c, tabla {@code delegacion_aprobacion}): durante un rango de fechas, el
 * delegado puede decidir lo que el delegante podría decidir por sus roles. Nunca se borra: se revoca.
 */
@Entity
@Table(name = "delegacion_aprobacion")
@Getter
@Setter
@NoArgsConstructor
public class DelegacionAprobacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Empresa dueña del registro. La asigna Hibernate al guardar y filtra toda consulta ({@code @TenantId}). */
    @TenantId
    @Column(name = "empresa_id", nullable = false, updatable = false)
    private Long empresaId;

    @Column(name = "delegante_usuario_id", nullable = false, updatable = false)
    private Long deleganteId;

    @Column(name = "delegado_usuario_id", nullable = false, updatable = false)
    private Long delegadoId;

    @Column(name = "vigente_desde", nullable = false, updatable = false)
    private LocalDate vigenteDesde;

    @Column(name = "vigente_hasta", nullable = false, updatable = false)
    private LocalDate vigenteHasta;

    @Column(length = 250, updatable = false)
    private String motivo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoDelegacion estado = EstadoDelegacion.ACTIVA;

    @Column(name = "revocada_en")
    private LocalDateTime revocadaEn;

    @Column(name = "revocada_por_usuario_id")
    private Long revocadaPorUsuarioId;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private LocalDateTime creadoEn;

    /**
     * Indica si la delegación se puede usar en una fecha: está ACTIVA y la fecha cae dentro de su rango.
     *
     * @param fecha día a evaluar
     * @return {@code true} si vale ese día
     */
    public boolean esVigente(LocalDate fecha) {
        return estado == EstadoDelegacion.ACTIVA && !fecha.isBefore(vigenteDesde) && !fecha.isAfter(vigenteHasta);
    }

    /** Fija la fecha de creación al insertar. */
    @PrePersist
    void alCrear() {
        if (creadoEn == null) {
            creadoEn = LocalDateTime.now();
        }
    }
}
