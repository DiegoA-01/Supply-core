package com.proyecto.supply_core.venta.entity;

import java.math.BigDecimal;

import com.proyecto.supply_core.venta.enums.MedioPagoVenta;

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
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Pago de una venta (tabla {@code venta_pago}). En efectivo, {@code recibido} es lo que entregó el cliente y
 * {@code monto} lo que queda en la caja (recibido menos el cambio). De una tarjeta solo se guardan los últimos 4
 * dígitos y la autorización.
 */
@Entity
@Table(name = "venta_pago")
@Getter
@Setter
@NoArgsConstructor
public class VentaPago {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "venta_id", nullable = false, updatable = false)
    private Venta venta;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 20)
    private MedioPagoVenta medio;

    @Column(nullable = false, updatable = false, precision = 18, scale = 2)
    private BigDecimal monto;

    @Column(updatable = false, precision = 18, scale = 2)
    private BigDecimal recibido;

    @Column(updatable = false, length = 40)
    private String referencia;

    @Column(updatable = false, length = 4)
    private String ultimos4;
}
