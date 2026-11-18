package com.proyecto.supply_core.plataforma.entity;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Plan comercial (tabla {@code plan}): un nombre y el conjunto de módulos que incluye. Es global, no de una
 * empresa. Un plan en uso no se borra: se desactiva.
 */
@Entity
@Table(name = "plan")
@Getter
@Setter
@NoArgsConstructor
public class Plan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Código en mayúsculas, único (p. ej. {@code PROFESIONAL}). */
    @Column(nullable = false, unique = true, length = 30)
    private String codigo;

    @Column(nullable = false, length = 100)
    private String nombre;

    @Column(nullable = false)
    private boolean activo = true;

    /** Códigos de los módulos incluidos (tabla {@code plan_modulo}). */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "plan_modulo", joinColumns = @JoinColumn(name = "plan_id"))
    @Column(name = "modulo_codigo", length = 40)
    private Set<String> modulos = new HashSet<>();

    @Column(name = "creado_en", nullable = false, updatable = false)
    private LocalDateTime creadoEn;

    /** Fija la fecha de creación al insertar. */
    @PrePersist
    void alCrear() {
        if (creadoEn == null) {
            creadoEn = LocalDateTime.now();
        }
    }
}
