package com.proyecto.supply_core.despacho.entity;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.proyecto.supply_core.despacho.enums.EstadoDespacho;
import com.proyecto.supply_core.inventario.entity.Bodega;

import org.hibernate.annotations.TenantId;
import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Despacho o remisión de salida (tabla {@code despacho}): lo que se vende o entrega sale de una bodega
 * con un solo documento de varias líneas. No lleva precios ni facturación. Nunca se borra: se anula.
 */
@Entity
@Table(name = "despacho")
@Getter
@Setter
@NoArgsConstructor
public class Despacho {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Empresa dueña del registro. La asigna Hibernate al guardar y filtra toda consulta ({@code @TenantId}). */
    @TenantId
    @Column(name = "empresa_id", nullable = false, updatable = false)
    private Long empresaId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bodega_id", nullable = false)
    private Bodega bodega;

    /** Quién lo creó. */
    @Column(name = "usuario_id", nullable = false, updatable = false)
    private Long usuarioId;

    @Column(nullable = false, unique = true, length = 30, updatable = false)
    private String consecutivo;

    /** Cliente o destinatario (texto libre, opcional). */
    @Column(length = 200)
    private String cliente;

    /** Número de factura, ticket de caja u otro documento externo (opcional). */
    @Column(name = "referencia_externa", length = 100)
    private String referenciaExterna;

    @Column(length = 500)
    private String observaciones;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoDespacho estado = EstadoDespacho.BORRADOR;

    @Column(name = "confirmado_en")
    private LocalDateTime confirmadoEn;

    @Column(name = "anulado_en")
    private LocalDateTime anuladoEn;

    @Column(name = "anulado_por_usuario_id")
    private Long anuladoPorUsuarioId;

    @Column(name = "motivo_anulacion", length = 500)
    private String motivoAnulacion;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private LocalDateTime creadoEn;

    @OneToMany(mappedBy = "despacho", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<DespachoLinea> lineas = new ArrayList<>();

    /**
     * Agrega una línea enlazándola a este despacho.
     *
     * @param linea línea nueva
     */
    public void agregarLinea(DespachoLinea linea) {
        linea.setDespacho(this);
        lineas.add(linea);
    }

    /** Fija la fecha de creación al insertar. */
    @PrePersist
    void alCrear() {
        if (creadoEn == null) {
            creadoEn = LocalDateTime.now();
        }
    }
}
