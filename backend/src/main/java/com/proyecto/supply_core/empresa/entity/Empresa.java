package com.proyecto.supply_core.empresa.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Empresa dueña del sistema (tabla {@code empresa}). Hay una sola, sembrada en V2
 * (ver {@code EmpresaUnica}); por eso no se crea ni se borra por API, solo se edita.
 */
@Entity
@Table(name = "empresa")
@Getter
@Setter
@NoArgsConstructor
public class Empresa {

    @Id
    private Long id;

    @Column(name = "razon_social", nullable = false, length = 200)
    private String razonSocial;

    @Column(nullable = false, unique = true, length = 30)
    private String nit;

    @Column(name = "direccion_principal", length = 250)
    private String direccionPrincipal;

    /** Código ISO 4217 (p. ej. COP). */
    @Column(name = "moneda_base", nullable = false, length = 3, columnDefinition = "CHAR(3)")
    private String monedaBase;

    @Column(name = "creado_en", nullable = false, updatable = false, insertable = false)
    private LocalDateTime creadoEn;
}
