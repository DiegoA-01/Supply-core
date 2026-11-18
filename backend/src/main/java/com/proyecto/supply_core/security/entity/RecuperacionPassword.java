package com.proyecto.supply_core.security.entity;

import java.time.LocalDateTime;

import org.hibernate.annotations.TenantId;

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
 * Token de un solo uso para restablecer la contraseña olvidada (tabla {@code recuperacion_password}).
 * En la base solo queda el hash SHA-256 del valor que viaja por correo.
 */
@Entity
@Table(name = "recuperacion_password")
@Getter
@Setter
@NoArgsConstructor
public class RecuperacionPassword {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Empresa del usuario. La asigna Hibernate al guardar y filtra toda consulta ({@code @TenantId}). */
    @TenantId
    @Column(name = "empresa_id", nullable = false, updatable = false)
    private Long empresaId;

    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(name = "expira_en", nullable = false)
    private LocalDateTime expiraEn;

    /** Momento en que se usó o se reemplazó por uno más nuevo; {@code null} mientras siga pendiente. */
    @Column(name = "usado_en")
    private LocalDateTime usadoEn;

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
