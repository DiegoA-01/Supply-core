package com.proyecto.supply_core.usuario.entity;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

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
    @Column(name = "empresa_id", nullable = false)
    private Long empresaId;

    @Column(name = "centro_costo_id")
    private Long centroCostoId;

    /** Solo para usuarios del portal proveedor (rol PROVEEDOR). */
    @Column(name = "proveedor_id")
    private Long proveedorId;

    @Column(name = "nombre_completo", nullable = false, length = 200)
    private String nombreCompleto;

    /** Siempre en minúsculas y sin espacios (lo normaliza el Service). */
    @Column(nullable = false, unique = true, length = 150)
    private String correo;

    /** Hash BCrypt; nunca la contraseña real. */
    @Column(name = "hash_password", nullable = false)
    private String hashPassword;

    @Column(nullable = false)
    private boolean activo = true;

    @Column(name = "ultimo_acceso")
    private LocalDateTime ultimoAcceso;

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
