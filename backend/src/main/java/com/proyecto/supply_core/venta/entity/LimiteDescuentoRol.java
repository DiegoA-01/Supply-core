package com.proyecto.supply_core.venta.entity;

import java.math.BigDecimal;

import org.hibernate.annotations.TenantId;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Descuento máximo sin autorización de un rol en la empresa (tabla {@code limite_descuento_rol}, spec 004). */
@Entity
@Table(name = "limite_descuento_rol")
@Getter
@Setter
@NoArgsConstructor
public class LimiteDescuentoRol {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Empresa dueña del registro. La asigna Hibernate al guardar y filtra toda consulta ({@code @TenantId}). */
    @TenantId
    @Column(name = "empresa_id", nullable = false, updatable = false)
    private Long empresaId;

    @Column(name = "rol_codigo", nullable = false, updatable = false, length = 30)
    private String rolCodigo;

    @Column(name = "descuento_maximo_pct", nullable = false, precision = 5, scale = 2)
    private BigDecimal descuentoMaximoPct;
}
