package com.proyecto.supply_core.pago.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import com.proyecto.supply_core.compra.orden.entity.OrdenCompra;
import com.proyecto.supply_core.pago.enums.EstadoPago;
import com.proyecto.supply_core.pago.enums.MetodoPago;
import com.proyecto.supply_core.pago.enums.TipoPago;

import org.hibernate.annotations.TenantId;
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
 * Pago de una orden de compra (UC-11, tabla {@code pago}). Los datos del pago no cambian; solo se adjunta
 * el comprobante (una vez) o se anula con motivo.
 */
@Entity
@Table(name = "pago")
@Getter
@Setter
@NoArgsConstructor
public class Pago {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Empresa dueña del registro. La asigna Hibernate al guardar y filtra toda consulta ({@code @TenantId}). */
    @TenantId
    @Column(name = "empresa_id", nullable = false, updatable = false)
    private Long empresaId;

    /** Versión de la orden vigente cuando se pagó. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "orden_compra_id", nullable = false, updatable = false)
    private OrdenCompra orden;

    /** ANTICIPO (antes de recibir, con tope) o PAGO_FINAL (contra factura, después de recibir). */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private TipoPago tipo = TipoPago.ANTICIPO;

    /** ADMINISTRATIVO que lo registró. */
    @Column(name = "usuario_id", nullable = false, updatable = false)
    private Long usuarioId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50, updatable = false)
    private MetodoPago metodo;

    @Column(nullable = false, precision = 18, scale = 2, updatable = false)
    private BigDecimal valor;

    @Column(name = "fecha_pago", nullable = false, updatable = false)
    private LocalDate fechaPago;

    /** Número de transferencia, cheque o similar. */
    @Column(length = 100, updatable = false)
    private String referencia;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoPago estado = EstadoPago.REGISTRADO;

    /** {@code true} mientras no tenga comprobante. */
    @Column(name = "evidencia_pendiente", nullable = false)
    private boolean evidenciaPendiente = true;

    @Column(name = "comprobante_documento_id")
    private Long comprobanteDocumentoId;

    @Column(name = "motivo_anulacion", length = 500)
    private String motivoAnulacion;

    @Column(name = "anulado_por_usuario_id")
    private Long anuladoPorUsuarioId;

    @Column(name = "anulado_en")
    private LocalDateTime anuladoEn;

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
