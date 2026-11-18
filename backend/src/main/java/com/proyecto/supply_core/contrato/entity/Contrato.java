package com.proyecto.supply_core.contrato.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.TenantId;

import com.proyecto.supply_core.contrato.enums.EstadoContrato;
import com.proyecto.supply_core.proveedor.entity.Proveedor;

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
 * Contrato con un proveedor (paso 18-b, tabla {@code contrato_proveedor}). Fija la vigencia y, por producto, un
 * precio pactado y un tope de cantidad. Los precios no se editan: para cambiarlos se anula y se crea otro.
 * Solo se puede mover la fecha de fin o anular. Nunca se borra.
 */
@Entity
@Table(name = "contrato_proveedor")
@Getter
@Setter
@NoArgsConstructor
public class Contrato {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Empresa dueña del registro. La asigna Hibernate al guardar y filtra toda consulta ({@code @TenantId}). */
    @TenantId
    @Column(name = "empresa_id", nullable = false, updatable = false)
    private Long empresaId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "proveedor_id", nullable = false, updatable = false)
    private Proveedor proveedor;

    /** Número del contrato, único por empresa, en mayúsculas. */
    @Column(nullable = false, length = 40, updatable = false)
    private String numero;

    @Column(length = 250)
    private String descripcion;

    @Column(name = "vigente_desde", nullable = false, updatable = false)
    private LocalDate vigenteDesde;

    @Column(name = "vigente_hasta", nullable = false)
    private LocalDate vigenteHasta;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoContrato estado = EstadoContrato.ACTIVO;

    @Column(name = "motivo_anulacion", length = 500)
    private String motivoAnulacion;

    @Column(name = "anulado_por_usuario_id")
    private Long anuladoPorUsuarioId;

    @Column(name = "anulado_en")
    private LocalDateTime anuladoEn;

    @Column(name = "creado_por_usuario_id", nullable = false, updatable = false)
    private Long creadoPorUsuarioId;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private LocalDateTime creadoEn;

    @OneToMany(mappedBy = "contrato", cascade = CascadeType.ALL)
    @OrderBy("id")
    private List<ContratoPrecio> precios = new ArrayList<>();

    /**
     * Agrega un precio enlazándolo a este contrato.
     *
     * @param precio precio nuevo
     */
    public void agregarPrecio(ContratoPrecio precio) {
        precio.setContrato(this);
        precios.add(precio);
    }

    /**
     * Indica si el contrato se puede usar en una fecha: está ACTIVO y la fecha cae dentro de su vigencia.
     *
     * @param fecha día a evaluar
     * @return {@code true} si es vigente ese día
     */
    public boolean esVigente(LocalDate fecha) {
        return estado == EstadoContrato.ACTIVO && !fecha.isBefore(vigenteDesde) && !fecha.isAfter(vigenteHasta);
    }

    /** Fija la fecha de creación al insertar. */
    @PrePersist
    void alCrear() {
        if (creadoEn == null) {
            creadoEn = LocalDateTime.now();
        }
    }
}
