package com.proyecto.supply_core.caja.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.TenantId;

import com.proyecto.supply_core.caja.enums.MedioPago;
import com.proyecto.supply_core.caja.enums.TipoMovimientoCaja;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Libro de la caja (tabla {@code movimiento_caja}, V33): cada peso que entra (positivo) o sale (negativo). Es
 * inmutable: se crea y no se edita ni se borra.
 */
@Entity
@Immutable
@Table(name = "movimiento_caja")
@Getter
@Setter
@NoArgsConstructor
public class MovimientoCaja {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Empresa dueña del registro. La asigna Hibernate al guardar y filtra toda consulta ({@code @TenantId}). */
    @TenantId
    @Column(name = "empresa_id", nullable = false, updatable = false)
    private Long empresaId;

    @Column(name = "turno_id", nullable = false)
    private Long turnoId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private TipoMovimientoCaja tipo;

    @Enumerated(EnumType.STRING)
    @Column(name = "medio_pago", nullable = false, length = 20)
    private MedioPago medioPago;

    /** Positivo entra, negativo sale. */
    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal monto;

    @Column(name = "referencia_tipo", length = 30)
    private String referenciaTipo;

    @Column(name = "referencia_id")
    private Long referenciaId;

    @Column(length = 300)
    private String motivo;

    /** Número del recibo o factura de un gasto. */
    @Column(length = 100)
    private String soporte;

    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    @Column(name = "autorizado_por_usuario_id")
    private Long autorizadoPorUsuarioId;

    @Column(name = "creado_en", nullable = false, insertable = false, updatable = false)
    private LocalDateTime creadoEn;
}
