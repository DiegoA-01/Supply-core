package com.proyecto.supply_core.plataforma.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Excepción de módulo para una empresa (tabla {@code empresa_modulo}). {@code habilitado = true} le da un módulo que
 * su plan no incluye; {@code false} le quita uno que sí incluye. Sin fila, manda el plan.
 */
@Entity
@Table(name = "empresa_modulo")
@Getter
@Setter
@NoArgsConstructor
public class EmpresaModulo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "empresa_id", nullable = false, updatable = false)
    private Long empresaId;

    @Column(name = "modulo_codigo", nullable = false, updatable = false, length = 40)
    private String moduloCodigo;

    @Column(nullable = false)
    private boolean habilitado;
}
