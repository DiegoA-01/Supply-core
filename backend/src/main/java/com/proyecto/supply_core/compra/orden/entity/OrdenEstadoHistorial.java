package com.proyecto.supply_core.compra.orden.entity;

import java.time.LocalDateTime;

import org.hibernate.annotations.Immutable;

import com.proyecto.supply_core.compra.orden.enums.EstadoOrden;

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

/** Cambio de estado de una orden (tabla {@code orden_compra_estado_historial}). Inmutable. */
@Entity
@Immutable
@Table(name = "orden_compra_estado_historial")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrdenEstadoHistorial {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "orden_compra_id", nullable = false, updatable = false)
    private Long ordenId;

    @Column(nullable = false, length = 30, updatable = false)
    private String estado;

    @Column(name = "usuario_id", nullable = false, updatable = false)
    private Long usuarioId;

    @Column(length = 500, updatable = false)
    private String observacion;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private LocalDateTime creadoEn;

    /**
     * Crea un registro del historial.
     *
     * @param ordenId     orden
     * @param estado      estado nuevo
     * @param usuarioId   quién lo cambió (interno o del proveedor)
     * @param observacion comentario o motivo
     */
    public OrdenEstadoHistorial(Long ordenId, EstadoOrden estado, Long usuarioId, String observacion) {
        this.ordenId = ordenId;
        this.estado = estado.name();
        this.usuarioId = usuarioId;
        this.observacion = observacion;
    }

    /** Fija la fecha al insertar. */
    @PrePersist
    void alCrear() {
        if (creadoEn == null) {
            creadoEn = LocalDateTime.now();
        }
    }
}
