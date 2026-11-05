package com.proyecto.supply_core.presupuesto.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.hibernate.annotations.TenantId;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Presupuesto de un centro de costo para un mes (tabla {@code presupuesto}, paso 18-a).
 * <p>Hay a lo sumo uno por centro de costo y mes. Nunca se borra; se corrige su monto.</p>
 */
@Entity
@Table(name = "presupuesto")
@Getter
@Setter
@NoArgsConstructor
public class Presupuesto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Empresa dueña del registro. La asigna Hibernate al guardar y filtra toda consulta ({@code @TenantId}). */
    @TenantId
    @Column(name = "empresa_id", nullable = false, updatable = false)
    private Long empresaId;

    @Column(name = "centro_costo_id", nullable = false, updatable = false)
    private Long centroCostoId;

    @Column(nullable = false, updatable = false)
    private Integer anio;

    /** Mes del 1 al 12. */
    @Column(nullable = false, updatable = false)
    private Integer mes;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal monto;

    @Column(name = "creado_por_usuario_id", nullable = false, updatable = false)
    private Long creadoPorUsuarioId;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private LocalDateTime creadoEn;

    @Column(name = "actualizado_en")
    private LocalDateTime actualizadoEn;

    /** Fija la fecha de creación al insertar. */
    @PrePersist
    void alCrear() {
        if (creadoEn == null) {
            creadoEn = LocalDateTime.now();
        }
    }

    /** Fija la fecha de actualización al modificar. */
    @PreUpdate
    void alActualizar() {
        actualizadoEn = LocalDateTime.now();
    }
}
