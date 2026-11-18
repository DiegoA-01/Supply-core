package com.proyecto.supply_core.plataforma.entity;

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
 * Usuario de la plataforma, el dueño del software (tabla {@code usuario_plataforma}). No pertenece a ninguna
 * empresa, entra por su propio login con MFA obligatorio y no puede leer datos de negocio. Nunca se borra.
 */
@Entity
@Table(name = "usuario_plataforma")
@Getter
@Setter
@NoArgsConstructor
public class UsuarioPlataforma {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 150)
    private String correo;

    @Column(nullable = false, length = 150)
    private String nombre;

    @Column(name = "hash_password", nullable = false, length = 100)
    private String hashPassword;

    @Column(nullable = false)
    private boolean activo = true;

    /** Secreto TOTP en base32. Es un dato sensible: nunca sale por la API salvo en el primer login para configurarlo. */
    @Column(name = "mfa_secreto", length = 64)
    private String mfaSecreto;

    /** {@code true} cuando ya confirmó un código con ese secreto. */
    @Column(name = "mfa_activo", nullable = false)
    private boolean mfaActivo;

    /** Último intervalo de 30 s aceptado, para que un código no se use dos veces. */
    @Column(name = "mfa_ultimo_paso")
    private Long mfaUltimoPaso;

    @Column(name = "intentos_fallidos", nullable = false)
    private int intentosFallidos;

    @Column(name = "bloqueado_hasta")
    private LocalDateTime bloqueadoHasta;

    @Column(name = "ultimo_acceso")
    private LocalDateTime ultimoAcceso;

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
