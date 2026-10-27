package com.proyecto.supply_core.compra.rfq.entity;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

import com.proyecto.supply_core.compra.rfq.enums.EstadoRfq;
import com.proyecto.supply_core.compra.solicitud.entity.SolicitudCompra;
import com.proyecto.supply_core.proveedor.entity.Proveedor;

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
 * Solicitud de cotización (UC-06, tabla {@code rfq}). Nace de una solicitud APROBADA, copia sus
 * líneas y se envía a los proveedores invitados. Nunca se borra: se cancela.
 */
@Entity
@Table(name = "rfq")
@Getter
@Setter
@NoArgsConstructor
public class Rfq {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Empresa dueña del registro. La asigna Hibernate al guardar y filtra toda consulta ({@code @TenantId}). */
    @TenantId
    @Column(name = "empresa_id", nullable = false, updatable = false)
    private Long empresaId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "solicitud_compra_id", nullable = false, updatable = false)
    private SolicitudCompra solicitud;

    /** Comprador que la creó. */
    @Column(name = "usuario_id", nullable = false, updatable = false)
    private Long usuarioId;

    @Column(nullable = false, unique = true, length = 30, updatable = false)
    private String consecutivo;

    @Column(name = "fecha_limite", nullable = false)
    private LocalDateTime fechaLimite;

    @Column(length = 1000)
    private String condiciones;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoRfq estado = EstadoRfq.BORRADOR;

    @Column(name = "abierta_en")
    private LocalDateTime abiertaEn;

    @Column(name = "cerrada_en")
    private LocalDateTime cerradaEn;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private LocalDateTime creadoEn;

    @OneToMany(mappedBy = "rfq", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id") // Set (no List): se carga junto con los invitados y una List se duplicaría
    private Set<RfqLinea> lineas = new LinkedHashSet<>();

    @OneToMany(mappedBy = "rfq", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<RfqProveedor> invitados = new HashSet<>();

    /**
     * Agrega una línea enlazándola a esta RFQ.
     *
     * @param linea línea nueva
     */
    public void agregarLinea(RfqLinea linea) {
        linea.setRfq(this);
        lineas.add(linea);
    }

    /**
     * Invita a un proveedor (si ya estaba invitado no hace nada).
     *
     * @param proveedor proveedor HABILITADO
     * @throws IllegalStateException si la RFQ aún no está guardada (la invitación usa su id)
     */
    public void invitar(Proveedor proveedor) {
        if (id == null) {
            throw new IllegalStateException("Guarde la RFQ antes de invitar proveedores");
        }
        if (!estaInvitado(proveedor.getId())) {
            invitados.add(new RfqProveedor(this, proveedor));
        }
    }

    /**
     * Indica si el proveedor está invitado.
     *
     * @param proveedorId proveedor
     * @return {@code true} si está en la lista de invitados
     */
    public boolean estaInvitado(Long proveedorId) {
        return invitados.stream().anyMatch(i -> i.getProveedor().getId().equals(proveedorId));
    }

    /** Fija la fecha de creación al insertar. */
    @PrePersist
    void alCrear() {
        if (creadoEn == null) {
            creadoEn = LocalDateTime.now();
        }
    }
}
