package com.proyecto.supply_core.proveedor.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.hibernate.annotations.Immutable;

import com.proyecto.supply_core.proveedor.dto.IndicadoresEvaluacion;

import org.hibernate.annotations.TenantId;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Evaluación de un proveedor en una orden de compra (UC-14, tabla {@code evaluacion_proveedor}).
 * <p>Inmutable: una por proveedor y orden (UNIQUE). Cada indicador va de 0 a 100 y es
 * {@code null} cuando no hay datos para medirlo (nunca un valor por defecto).
 * {@code ordenCompraId} se guarda plano: la entidad de orden llega en el paso 15.</p>
 */
@Entity
@Immutable
@Table(name = "evaluacion_proveedor")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EvaluacionProveedor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Empresa dueña del registro. La asigna Hibernate al guardar y filtra toda consulta ({@code @TenantId}). */
    @TenantId
    @Column(name = "empresa_id", nullable = false, updatable = false)
    private Long empresaId;

    @Column(name = "proveedor_id", nullable = false, updatable = false)
    private Long proveedorId;

    @Column(name = "orden_compra_id", nullable = false, updatable = false)
    private Long ordenCompraId;

    @Column(precision = 5, scale = 2, updatable = false)
    private BigDecimal puntualidad;

    @Column(name = "cumplimiento_cantidad", precision = 5, scale = 2, updatable = false)
    private BigDecimal cumplimientoCantidad;

    @Column(precision = 5, scale = 2, updatable = false)
    private BigDecimal incidencias;

    @Column(precision = 5, scale = 2, updatable = false)
    private BigDecimal calidad;

    @Column(precision = 5, scale = 2, updatable = false)
    private BigDecimal precio;

    @Column(precision = 5, scale = 2, updatable = false)
    private BigDecimal respuesta;

    /** Promedio de los indicadores con datos; {@code null} si ninguno tiene datos. */
    @Column(precision = 5, scale = 2, updatable = false)
    private BigDecimal score;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private LocalDateTime creadoEn;

    /**
     * Crea una evaluación lista para insertar.
     *
     * @param proveedorId   proveedor evaluado
     * @param ordenCompraId orden que se evalúa
     * @param i             indicadores (0–100 o {@code null})
     * @param score         score calculado
     */
    public EvaluacionProveedor(Long proveedorId, Long ordenCompraId, IndicadoresEvaluacion i, BigDecimal score) {
        this.proveedorId = proveedorId;
        this.ordenCompraId = ordenCompraId;
        this.puntualidad = i.puntualidad();
        this.cumplimientoCantidad = i.cumplimientoCantidad();
        this.incidencias = i.incidencias();
        this.calidad = i.calidad();
        this.precio = i.precio();
        this.respuesta = i.respuesta();
        this.score = score;
    }

    /** Fija la fecha de creación al insertar. */
    @PrePersist
    void alCrear() {
        if (creadoEn == null) {
            creadoEn = LocalDateTime.now();
        }
    }
}
