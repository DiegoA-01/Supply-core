package com.proyecto.supply_core.ia.entity;

import java.time.LocalDateTime;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.proyecto.supply_core.ia.enums.EstadoProcesamiento;

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
 * Lectura de una cotización por archivo (tabla {@code procesamiento_ia}, una por cotización).
 * Guarda la respuesta cruda de la IA para trazabilidad.
 */
@Entity
@Table(name = "procesamiento_ia")
@Getter
@Setter
@NoArgsConstructor
public class ProcesamientoIa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cotizacion_id", nullable = false, unique = true, updatable = false)
    private Long cotizacionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoProcesamiento estado = EstadoProcesamiento.PENDIENTE;

    @Column(length = 80)
    private String modelo;

    @Column(nullable = false)
    private int intentos;

    /** Respuesta de la IA ya convertida al esquema (JSON). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "respuesta_json")
    private String respuestaJson;

    @Column(length = 1000)
    private String error;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private LocalDateTime creadoEn;

    @Column(name = "finalizado_en")
    private LocalDateTime finalizadoEn;

    /** Fija la fecha de creación al insertar. */
    @PrePersist
    void alCrear() {
        if (creadoEn == null) {
            creadoEn = LocalDateTime.now();
        }
    }
}
