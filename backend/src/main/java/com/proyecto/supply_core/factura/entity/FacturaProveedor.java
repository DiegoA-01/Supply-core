package com.proyecto.supply_core.factura.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import org.hibernate.annotations.TenantId;

import com.proyecto.supply_core.compra.orden.entity.OrdenCompra;
import com.proyecto.supply_core.factura.enums.EstadoFactura;
import com.proyecto.supply_core.proveedor.entity.Proveedor;

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
 * Factura de un proveedor sobre una orden (tabla {@code factura_proveedor}). Los montos de la factura no se
 * editan: si están mal se anula con motivo y se registra otra. Junto a lo facturado se guarda lo esperado según
 * la recepción (cantidad aceptada x precio de la orden + impuesto) y la diferencia, para que el cruce quede
 * a la vista.
 */
@Entity
@Table(name = "factura_proveedor")
@Getter
@Setter
@NoArgsConstructor
public class FacturaProveedor {

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

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "proveedor_id", nullable = false, updatable = false)
    private Proveedor proveedor;

    /** ADMINISTRATIVO que la registró. */
    @Column(name = "usuario_id", nullable = false, updatable = false)
    private Long usuarioId;

    /** Número impreso en la factura del proveedor. */
    @Column(nullable = false, length = 60, updatable = false)
    private String numero;

    @Column(name = "fecha_factura", nullable = false, updatable = false)
    private LocalDate fechaFactura;

    @Column(nullable = false, precision = 18, scale = 2, updatable = false)
    private BigDecimal subtotal;

    @Column(nullable = false, precision = 18, scale = 2, updatable = false)
    private BigDecimal impuestos;

    @Column(nullable = false, precision = 18, scale = 2, updatable = false)
    private BigDecimal total;

    /** Neto que debería facturar el proveedor según lo aceptado en la recepción. */
    @Column(name = "esperado_subtotal", nullable = false, precision = 18, scale = 2)
    private BigDecimal esperadoSubtotal;

    @Column(name = "esperado_impuestos", nullable = false, precision = 18, scale = 2)
    private BigDecimal esperadoImpuestos;

    @Column(name = "esperado_total", nullable = false, precision = 18, scale = 2)
    private BigDecimal esperadoTotal;

    /** Total facturado menos total esperado (positivo = facturó de más). */
    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal diferencia;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoFactura estado;

    /** PDF de la factura (se adjunta una vez). */
    @Column(name = "documento_id")
    private Long documentoId;

    @Column(name = "motivo_aprobacion", length = 500)
    private String motivoAprobacion;

    @Column(name = "aprobado_por_usuario_id")
    private Long aprobadoPorUsuarioId;

    @Column(name = "aprobado_en")
    private LocalDateTime aprobadoEn;

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
