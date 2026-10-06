package com.proyecto.supply_core.compra.rfq.entity;

import java.io.Serializable;
import java.time.LocalDateTime;

import com.proyecto.supply_core.proveedor.entity.Proveedor;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Proveedor invitado a una RFQ (tabla {@code rfq_proveedor}). Entity propia porque guarda
 * {@code invitado_en}. Igualdad por clave compuesta (para el {@code Set} de invitados).
 * <p>La RFQ debe estar guardada (con id) antes de invitar: la clave se arma con ambos ids.</p>
 */
@Entity
@Table(name = "rfq_proveedor")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(of = "id")
public class RfqProveedor {

    @EmbeddedId
    private Id id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "rfq_id", insertable = false, updatable = false)
    private Rfq rfq;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "proveedor_id", insertable = false, updatable = false)
    private Proveedor proveedor;

    @Column(name = "invitado_en", nullable = false, updatable = false)
    private LocalDateTime invitadoEn;

    /**
     * Crea la invitación.
     *
     * @param rfq       RFQ
     * @param proveedor proveedor invitado
     */
    RfqProveedor(Rfq rfq, Proveedor proveedor) {
        this.rfq = rfq;
        this.proveedor = proveedor;
        this.id = new Id(rfq.getId(), proveedor.getId());
    }

    /** Fija la fecha de invitación al insertar. */
    @PrePersist
    void alCrear() {
        if (invitadoEn == null) {
            invitadoEn = LocalDateTime.now();
        }
    }

    /**
     * Clave compuesta (rfq_id, proveedor_id).
     *
     * @param rfqId       RFQ
     * @param proveedorId proveedor
     */
    @Embeddable
    public record Id(@Column(name = "rfq_id") Long rfqId, @Column(name = "proveedor_id") Long proveedorId)
            implements Serializable {
    }
}
