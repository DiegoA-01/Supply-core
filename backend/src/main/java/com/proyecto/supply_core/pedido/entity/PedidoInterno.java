package com.proyecto.supply_core.pedido.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.hibernate.annotations.TenantId;

import com.proyecto.supply_core.empresa.entity.Sede;
import com.proyecto.supply_core.inventario.entity.Producto;
import com.proyecto.supply_core.pedido.enums.EstadoPedidoInterno;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Pedido interno ("Necesito este producto", tabla {@code pedido_interno}): un usuario interno avisa a compras de algo
 * que hace falta. Puede ser un producto del catálogo o una descripción libre de algo que aún no se maneja.
 */
@Entity
@Table(name = "pedido_interno")
@Getter
@Setter
@NoArgsConstructor
public class PedidoInterno {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Empresa dueña del registro. La asigna Hibernate al guardar y filtra toda consulta ({@code @TenantId}). */
    @TenantId
    @Column(name = "empresa_id", nullable = false, updatable = false)
    private Long empresaId;

    @Column(nullable = false, length = 30, updatable = false)
    private String consecutivo;

    /** Quien lo pidió. */
    @Column(name = "usuario_id", nullable = false, updatable = false)
    private Long usuarioId;

    /** Producto del catálogo, o {@code null} si se describe algo que aún no se maneja. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "producto_id", updatable = false)
    private Producto producto;

    @Column(length = 300, updatable = false)
    private String descripcion;

    @Column(precision = 18, scale = 4, updatable = false)
    private BigDecimal cantidad;

    /** Sede donde hace falta, opcional. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sede_id", updatable = false)
    private Sede sede;

    @Column(length = 500, updatable = false)
    private String nota;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoPedidoInterno estado = EstadoPedidoInterno.PENDIENTE;

    /** Lo que respondió compras. */
    @Column(length = 500)
    private String respuesta;

    @Column(name = "atendido_por_usuario_id")
    private Long atendidoPorUsuarioId;

    @Column(name = "atendido_en")
    private LocalDateTime atendidoEn;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private LocalDateTime creadoEn;

    /** Fija la fecha de creación al guardar. */
    @PrePersist
    void alCrear() {
        if (creadoEn == null) {
            creadoEn = LocalDateTime.now();
        }
    }
}
