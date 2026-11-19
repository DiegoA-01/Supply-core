package com.proyecto.supply_core.usuario.entity;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import org.hibernate.annotations.TenantId;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Usuario interno o del portal proveedor (tabla {@code usuario}).
 * Nunca se borra: se desactiva con {@code activo = false}.
 */
@Entity
@Table(name = "usuario")
@Getter
@Setter
@NoArgsConstructor
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // FKs de otros módulos como Long mientras esos módulos no existan (empresa: paso 4,
    // proveedor: paso 9). Se pasarán a @ManyToOne cuando se creen sus entidades.
    /** Empresa del usuario. La asigna Hibernate al guardar y filtra toda consulta ({@code @TenantId}). */
    @TenantId
    @Column(name = "empresa_id", nullable = false, updatable = false)
    private Long empresaId;

    @Column(name = "centro_costo_id")
    private Long centroCostoId;

    /** Solo para usuarios del portal proveedor (rol PROVEEDOR). */
    @Column(name = "proveedor_id")
    private Long proveedorId;

    @Column(name = "nombre_completo", nullable = false, length = 200)
    private String nombreCompleto;

    /** Siempre en minúsculas y sin espacios (lo normaliza el Service). Único dentro de la empresa. */
    @Column(nullable = false, length = 150)
    private String correo;

    /** Hash BCrypt; nunca la contraseña real. */
    @Column(name = "hash_password", nullable = false)
    private String hashPassword;

    @Column(nullable = false)
    private boolean activo = true;

    @Column(name = "ultimo_acceso")
    private LocalDateTime ultimoAcceso;

    /** Fallos de login seguidos; vuelve a cero al entrar bien o al bloquearse la cuenta. */
    @Column(name = "intentos_fallidos", nullable = false)
    private int intentosFallidos;

    /** Mientras sea futura, el login se rechaza aunque la clave sea correcta. */
    @Column(name = "bloqueado_hasta")
    private LocalDateTime bloqueadoHasta;

    /** Hash BCrypt del PIN de supervisor de 6 dígitos (V33); {@code null} si no ha fijado uno. */
    @Column(name = "pin_supervisor_hash", length = 100)
    private String pinSupervisorHash;

    /** Fallos seguidos del PIN; al llegar a 5 se bloquea el PIN 15 minutos (no la cuenta). */
    @Column(name = "pin_intentos_fallidos", nullable = false)
    private int pinIntentosFallidos;

    /** Mientras sea futura, el PIN se rechaza aunque sea correcto. */
    @Column(name = "pin_bloqueado_hasta")
    private LocalDateTime pinBloqueadoHasta;

    /**
     * Sube en uno al cambiar la clave o desactivar al usuario. El JWT lleva la versión con la que se emitió y deja de
     * valer cuando ya no coincide.
     */
    @Column(name = "version_sesion", nullable = false)
    private int versionSesion;

    /** Secreto TOTP en base32 del segundo factor. Dato sensible: nunca sale por la API salvo al configurarlo. */
    @Column(name = "mfa_secreto", length = 64)
    private String mfaSecreto;

    /** {@code true} cuando ya confirmó un código con ese secreto (desde ahí el login lo exige). */
    @Column(name = "mfa_activo", nullable = false)
    private boolean mfaActivo;

    /** Último intervalo de 30 s aceptado, para que un código no se use dos veces. */
    @Column(name = "mfa_ultimo_paso")
    private Long mfaUltimoPaso;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private LocalDateTime creadoEn;

    @ManyToMany
    @JoinTable(name = "usuario_rol",
            joinColumns = @JoinColumn(name = "usuario_id"),
            inverseJoinColumns = @JoinColumn(name = "rol_id"))
    private Set<Rol> roles = new HashSet<>();

    /** Fija la fecha de creación al insertar (si no se asignó antes). */
    @PrePersist
    void alCrear() {
        if (creadoEn == null) {
            creadoEn = LocalDateTime.now();
        }
    }

    /**
     * Códigos de los roles del usuario.
     *
     * @return conjunto de códigos (p. ej. {@code COMPRADOR})
     */
    public Set<String> codigosRoles() {
        return roles.stream().map(Rol::getCodigo).collect(Collectors.toSet());
    }
}
