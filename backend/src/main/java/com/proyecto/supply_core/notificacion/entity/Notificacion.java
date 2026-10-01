package com.proyecto.supply_core.notificacion.entity;

import java.time.LocalDateTime;

import com.proyecto.supply_core.notificacion.enums.EventoNotificacion;

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
 * Notificación interna de un usuario (tabla {@code notificacion}).
 * <p>Solo cambia {@code leida}; el resto no se edita y nunca se borra.</p>
 */
@Entity
@Table(name = "notificacion")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notificacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Destinatario (FK a {@code usuario}, guardada plana para no acoplar módulos). */
    @Column(name = "usuario_id", nullable = false, updatable = false)
    private Long usuarioId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 80, updatable = false)
    private EventoNotificacion evento;

    @Column(nullable = false, length = 500, updatable = false)
    private String mensaje;

    @Column(nullable = false)
    private boolean leida;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private LocalDateTime creadoEn;

    /**
     * Crea una notificación no leída.
     *
     * @param usuarioId destinatario
     * @param evento    evento que la generó
     * @param mensaje   texto (máximo 500 caracteres)
     */
    public Notificacion(Long usuarioId, EventoNotificacion evento, String mensaje) {
        this.usuarioId = usuarioId;
        this.evento = evento;
        this.mensaje = mensaje;
    }

    /** Marca la notificación como leída (idempotente). */
    public void marcarLeida() {
        this.leida = true;
    }

    /** Fija la fecha de creación al insertar. */
    @PrePersist
    void alCrear() {
        if (creadoEn == null) {
            creadoEn = LocalDateTime.now();
        }
    }
}
