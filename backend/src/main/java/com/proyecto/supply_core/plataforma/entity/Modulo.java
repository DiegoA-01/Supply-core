package com.proyecto.supply_core.plataforma.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Módulo que el dueño puede activar a una empresa (tabla {@code modulo}). Es un catálogo global sembrado por
 * Flyway; los códigos están en {@code Modulos}.
 */
@Entity
@Table(name = "modulo")
@Getter
@NoArgsConstructor
public class Modulo {

    @Id
    @Column(length = 40)
    private String codigo;

    @Column(nullable = false, length = 100)
    private String nombre;

    @Column(length = 250)
    private String descripcion;
}
