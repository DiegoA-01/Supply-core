package com.proyecto.supply_core.auditoria.entity;

import java.time.LocalDateTime;

import org.hibernate.annotations.Immutable;

import com.proyecto.supply_core.auditoria.enums.ResultadoAuditoria;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Registro de auditoría (tabla {@code auditoria}). Inmutable: solo se inserta, nunca se edita
 * ni se borra (UC-16: el auditor reconstruye quién hizo qué y cuándo sin alterar los registros).
 * <p>{@code entidad} + {@code entidadId} es una referencia polimórfica: se guardan planos, sin relación JPA.</p>
 */
@Entity
@Immutable
@Table(name = "auditoria")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Auditoria {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Quién ejecutó la acción; {@code null} si fue anónima (p. ej. login con correo inexistente). */
    @Column(name = "usuario_id", updatable = false)
    private Long usuarioId;

    @Column(nullable = false, length = 80, updatable = false)
    private String entidad;

    @Column(name = "entidad_id", updatable = false)
    private Long entidadId;

    @Column(nullable = false, length = 80, updatable = false)
    private String accion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private ResultadoAuditoria resultado;

    @Column(length = 45, updatable = false)
    private String ip;

    @Column(columnDefinition = "TEXT", updatable = false)
    private String detalle;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private LocalDateTime creadoEn;

    /**
     * Crea un registro listo para insertar.
     *
     * @param usuarioId quién lo hizo (puede ser {@code null})
     * @param entidad   tipo de entidad afectada
     * @param entidadId id del registro afectado (puede ser {@code null})
     * @param accion    acción realizada
     * @param resultado éxito o fallo
     * @param ip        IP de origen (puede ser {@code null})
     * @param detalle   contexto legible, sin datos sensibles (puede ser {@code null})
     */
    public Auditoria(Long usuarioId, String entidad, Long entidadId, String accion,
            ResultadoAuditoria resultado, String ip, String detalle) {
        this.usuarioId = usuarioId;
        this.entidad = entidad;
        this.entidadId = entidadId;
        this.accion = accion;
        this.resultado = resultado;
        this.ip = ip;
        this.detalle = detalle;
    }

    /** Fija la fecha de creación al insertar. */
    @PrePersist
    void alCrear() {
        if (creadoEn == null) {
            creadoEn = LocalDateTime.now();
        }
    }
}
