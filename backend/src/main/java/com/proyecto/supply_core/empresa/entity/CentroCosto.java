package com.proyecto.supply_core.empresa.entity;

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
 * Centro de costo (tabla {@code centro_costo}). Toda solicitud de compra se carga a uno (UC-04).
 * Nunca se borra: se desactiva.
 */
@Entity
@Table(name = "centro_costo")
@Getter
@Setter
@NoArgsConstructor
public class CentroCosto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "empresa_id", nullable = false)
    private Long empresaId;

    /** Único, en mayúsculas (p. ej. {@code ADM-01}). */
    @Column(nullable = false, unique = true, length = 30)
    private String codigo;

    @Column(nullable = false, length = 150)
    private String nombre;

    @Column(nullable = false)
    private boolean activo = true;
}
