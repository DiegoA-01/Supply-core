package com.proyecto.supply_core.proveedor.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

import com.proyecto.supply_core.catalogo.entity.CategoriaProducto;
import com.proyecto.supply_core.proveedor.enums.EstadoProveedor;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * Proveedor (tabla {@code proveedor}). Sus usuarios del portal son filas de {@code usuario}
 * con {@code proveedor_id} y rol PROVEEDOR. Nunca se borra: cambia de {@link EstadoProveedor}.
 */
@Entity
@Table(name = "proveedor")
@Getter
@Setter
@NoArgsConstructor
public class Proveedor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "razon_social", nullable = false, length = 200)
    private String razonSocial;

    /** Sin puntos ni espacios; dígito de verificación opcional tras guion (900123456-7). */
    @Column(nullable = false, unique = true, length = 30)
    private String nit;

    @Column(name = "correo_contacto", length = 150)
    private String correoContacto;

    @Column(length = 30)
    private String telefono;

    @Column(length = 250)
    private String direccion;

    @Column(precision = 10, scale = 7)
    private BigDecimal latitud;

    @Column(precision = 10, scale = 7)
    private BigDecimal longitud;

    /** Promedio de evaluaciones (paso 9, UC-14); {@code null} mientras no haya datos. */
    @Column(name = "score_actual", precision = 5, scale = 2)
    private BigDecimal scoreActual;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoProveedor estado = EstadoProveedor.PENDIENTE;

    /** Motivo del rechazo o la suspensión. */
    @Column(name = "motivo_estado", length = 500)
    private String motivoEstado;

    /** Usuario (ADMIN_COMPRAS) que hizo la última revisión. */
    @Column(name = "revisado_por_usuario_id")
    private Long revisadoPorUsuarioId;

    @Column(name = "revisado_en")
    private LocalDateTime revisadoEn;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private LocalDateTime creadoEn;

    /** Categorías que el proveedor puede cotizar (UC-06: solo se invitan proveedores habilitados en la categoría). */
    @ManyToMany
    @JoinTable(name = "proveedor_categoria",
            joinColumns = @JoinColumn(name = "proveedor_id"),
            inverseJoinColumns = @JoinColumn(name = "categoria_producto_id"))
    private Set<CategoriaProducto> categorias = new HashSet<>();

    /** Fija la fecha de creación al insertar. */
    @PrePersist
    void alCrear() {
        if (creadoEn == null) {
            creadoEn = LocalDateTime.now();
        }
    }
}
