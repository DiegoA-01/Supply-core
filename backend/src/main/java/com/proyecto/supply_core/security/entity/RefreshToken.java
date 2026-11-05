package com.proyecto.supply_core.security.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Refresh token de una sesión (tabla {@code refresh_token}). Solo se guarda el hash SHA-256 del valor
 * que recibe el cliente. Los tokens de una misma sesión comparten {@code familia}.
 */
@Entity
@Table(name = "refresh_token")
@Getter
@Setter
@NoArgsConstructor
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    /** Identifica la sesión: todos los tokens rotados de un login comparten la familia. */
    @Column(nullable = false, length = 36)
    private String familia;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private LocalDateTime creadoEn;

    @Column(name = "expira_en", nullable = false)
    private LocalDateTime expiraEn;

    /** Momento en que se canjeó por uno nuevo; presentarlo otra vez indica posible robo. */
    @Column(name = "usado_en")
    private LocalDateTime usadoEn;

    @Column(name = "revocado_en")
    private LocalDateTime revocadoEn;

    /** Fija la fecha de creación al insertar (si no se asignó antes). */
    @PrePersist
    void alCrear() {
        if (creadoEn == null) {
            creadoEn = LocalDateTime.now();
        }
    }
}
