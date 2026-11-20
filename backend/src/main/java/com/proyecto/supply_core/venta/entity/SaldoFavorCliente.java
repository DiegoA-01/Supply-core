package com.proyecto.supply_core.venta.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

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

/** Movimiento del saldo a favor de un cliente (tabla {@code saldo_favor_cliente}, spec 006). Solo se inserta. */
@Entity
@Table(name = "saldo_favor_cliente")
@Getter
@Setter
@NoArgsConstructor
public class SaldoFavorCliente {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Empresa dueña del registro. La asigna Hibernate al guardar y filtra toda consulta ({@code @TenantId}). */
    @TenantId
    @Column(name = "empresa_id", nullable = false, updatable = false)
    private Long empresaId;

    @Column(name = "cliente_id", nullable = false, updatable = false)
    private Long clienteId;

    @Column(name = "devolucion_id", updatable = false)
    private Long devolucionId;

    /** Venta que consumió el saldo (movimiento negativo) o cuya anulación lo devolvió (spec 007). */
    @Column(name = "venta_id", updatable = false)
    private Long ventaId;

    /** Positivo = abono; negativo = consumo (spec 007). */
    @Column(nullable = false, updatable = false, precision = 18, scale = 2)
    private BigDecimal monto;

    @Column(updatable = false, length = 300)
    private String motivo;

    @Column(name = "creado_en", nullable = false, updatable = false, insertable = false)
    private LocalDateTime creadoEn;
}
