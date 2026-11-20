package com.proyecto.supply_core.reporte.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.TenantId;
import org.hibernate.type.SqlTypes;

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
 * Resultado del cuadre diario de una empresa (tabla {@code cuadre_diario}, spec 009): foto fija de lo que se revisó un
 * día. Hay una sola fila por empresa y fecha; volver a revisar el día la reemplaza.
 */
@Entity
@Table(name = "cuadre_diario")
@Getter
@Setter
@NoArgsConstructor
public class CuadreDiario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Empresa dueña del registro. La asigna Hibernate al guardar y filtra toda consulta ({@code @TenantId}). */
    @TenantId
    @Column(name = "empresa_id", nullable = false, updatable = false)
    private Long empresaId;

    @Column(nullable = false)
    private LocalDate fecha;

    /** Lo que las ventas del día cobraron por medios que entran a la caja. */
    @Column(name = "ventas_confirmadas", nullable = false, precision = 18, scale = 2)
    private BigDecimal ventasConfirmadas;

    /** Lo que la caja registró como venta por esas mismas ventas. */
    @Column(name = "ventas_en_caja", nullable = false, precision = 18, scale = 2)
    private BigDecimal ventasEnCaja;

    /** Unidades vendidas el día, en la unidad de cada producto. */
    @Column(name = "unidades_vendidas", nullable = false, precision = 18, scale = 4)
    private BigDecimal unidadesVendidas;

    /** Salidas de inventario por esas ventas. */
    @Column(name = "unidades_salida", nullable = false, precision = 18, scale = 4)
    private BigDecimal unidadesSalida;

    /** Turnos que llevaban más de 24 horas abiertos al revisar. */
    @Column(name = "turnos_abiertos_24h", nullable = false)
    private int turnosAbiertos24h;

    @Column(nullable = false)
    private boolean cuadra;

    /** Ventas y turnos que no cuadran (JSON). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "json")
    private String detalle;

    @Column(name = "revisado_en", nullable = false)
    private LocalDateTime revisadoEn;
}
