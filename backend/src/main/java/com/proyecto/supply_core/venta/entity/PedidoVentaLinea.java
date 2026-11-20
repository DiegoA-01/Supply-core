package com.proyecto.supply_core.venta.entity;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

/** Línea de un pedido de venta (tabla {@code pedido_venta_linea}): producto, unidad, cantidad y descuento pedido. */
@Entity
@Table(name = "pedido_venta_linea")
@Getter
@Setter
@NoArgsConstructor
public class PedidoVentaLinea {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pedido_venta_id", nullable = false, updatable = false)
    private PedidoVenta pedido;

    @Column(name = "producto_id", nullable = false)
    private Long productoId;

    @Column(name = "unidad_medida_id", nullable = false)
    private Long unidadMedidaId;

    @Column(nullable = false, precision = 18, scale = 4)
    private BigDecimal cantidad;

    @Column(name = "descuento_pct", nullable = false, precision = 5, scale = 2)
    private BigDecimal descuentoPct = BigDecimal.ZERO;
}
