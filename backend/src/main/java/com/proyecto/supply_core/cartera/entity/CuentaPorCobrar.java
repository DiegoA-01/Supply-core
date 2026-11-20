package com.proyecto.supply_core.cartera.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import com.proyecto.supply_core.cartera.enums.EstadoCuenta;

import org.hibernate.annotations.TenantId;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Cuenta por cobrar de una venta a crédito (tabla {@code cuenta_por_cobrar}, spec 007). */
@Entity
@Table(name = "cuenta_por_cobrar")
@Getter
@Setter
@NoArgsConstructor
public class CuentaPorCobrar {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Empresa dueña del registro. La asigna Hibernate al guardar y filtra toda consulta ({@code @TenantId}). */
    @TenantId
    @Column(name = "empresa_id", nullable = false, updatable = false)
    private Long empresaId;

    @Column(name = "venta_id", nullable = false, updatable = false)
    private Long ventaId;

    @Column(name = "cliente_id", nullable = false, updatable = false)
    private Long clienteId;

    @Column(nullable = false, updatable = false, precision = 18, scale = 2)
    private BigDecimal valor;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal saldo;

    @Column(name = "vence_en", nullable = false, updatable = false)
    private LocalDate venceEn;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private EstadoCuenta estado = EstadoCuenta.ABIERTA;

    @Column(name = "creado_en", nullable = false, insertable = false, updatable = false)
    private LocalDateTime creadoEn;

    @Version
    private int version;
}
