package com.proyecto.supply_core.recepcion.entity;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;

import org.hibernate.annotations.TenantId;

import com.proyecto.supply_core.compra.orden.entity.OrdenCompra;
import com.proyecto.supply_core.inventario.entity.Bodega;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * Una llegada de mercancía de una orden a una bodega (UC-13, tabla {@code recepcion}). Una orden puede tener
 * varias recepciones (entregas parciales). No se edita ni se borra: un error se corrige con otra recepción
 * o con la resolución del excedente.
 */
@Entity
@Table(name = "recepcion")
@Getter
@Setter
@NoArgsConstructor
public class Recepcion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Empresa dueña del registro. La asigna Hibernate al guardar y filtra toda consulta ({@code @TenantId}). */
    @TenantId
    @Column(name = "empresa_id", nullable = false, updatable = false)
    private Long empresaId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "orden_compra_id", nullable = false, updatable = false)
    private OrdenCompra orden;

    /** Almacenista que recibió. */
    @Column(name = "usuario_id", nullable = false, updatable = false)
    private Long usuarioId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bodega_id", nullable = false, updatable = false)
    private Bodega bodega;

    @Column(name = "recibido_en", nullable = false, updatable = false)
    private LocalDateTime recibidoEn;

    @Column(length = 500, updatable = false)
    private String observaciones;

    @OneToMany(mappedBy = "recepcion", cascade = CascadeType.ALL)
    @OrderBy("id")
    private Set<RecepcionLinea> lineas = new LinkedHashSet<>();

    /**
     * Agrega una línea enlazándola a esta recepción.
     *
     * @param linea línea nueva
     */
    public void agregarLinea(RecepcionLinea linea) {
        linea.setRecepcion(this);
        lineas.add(linea);
    }

    /** Fija la fecha de recepción al insertar. */
    @PrePersist
    void alCrear() {
        if (recibidoEn == null) {
            recibidoEn = LocalDateTime.now();
        }
    }
}
