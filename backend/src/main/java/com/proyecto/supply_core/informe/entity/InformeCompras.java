package com.proyecto.supply_core.informe.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.TenantId;
import org.hibernate.type.SqlTypes;

import com.proyecto.supply_core.informe.enums.EstadoInforme;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Informe de compras (tabla {@code informe_compras}): lo pide el administrador de la empresa y lo envía el área de
 * compras con su análisis. Al enviarse guarda una foto fija del periodo; después no se edita ni se borra.
 */
@Entity
@Table(name = "informe_compras")
@Getter
@Setter
@NoArgsConstructor
public class InformeCompras {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Empresa dueña del registro. La asigna Hibernate al guardar y filtra toda consulta ({@code @TenantId}). */
    @TenantId
    @Column(name = "empresa_id", nullable = false, updatable = false)
    private Long empresaId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoInforme estado;

    @Column(name = "periodo_desde", nullable = false)
    private LocalDate periodoDesde;

    @Column(name = "periodo_hasta", nullable = false)
    private LocalDate periodoHasta;

    /** Lo que el administrador escribió al pedirlo ({@code null} si compras lo envió sin pedido). */
    @Column(name = "mensaje_pedido", length = 500, updatable = false)
    private String mensajePedido;

    @Column(name = "pedido_por_usuario_id", updatable = false)
    private Long pedidoPorUsuarioId;

    @Column(name = "pedido_en", updatable = false)
    private LocalDateTime pedidoEn;

    /** Análisis y recomendaciones que escribe el área de compras. */
    @Column(columnDefinition = "TEXT")
    private String analisis;

    /** Foto fija del periodo (JSON de {@code InformeContenido}); la arma Java al enviarlo. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "contenido")
    private String contenido;

    @Column(name = "enviado_por_usuario_id")
    private Long enviadoPorUsuarioId;

    @Column(name = "enviado_en")
    private LocalDateTime enviadoEn;

    /** Primera vez que el administrador lo abrió. */
    @Column(name = "leido_en")
    private LocalDateTime leidoEn;

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
