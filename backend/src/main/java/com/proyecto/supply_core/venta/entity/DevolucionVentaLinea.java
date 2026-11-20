package com.proyecto.supply_core.venta.entity;

import java.math.BigDecimal;

import com.proyecto.supply_core.venta.enums.EstadoProductoDevuelto;

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

/** Línea devuelta (tabla {@code devolucion_venta_linea}): hereda la empresa de su devolución. */
@Entity
@Table(name = "devolucion_venta_linea")
@Getter
@Setter
@NoArgsConstructor
public class DevolucionVentaLinea {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "devolucion_id", nullable = false, updatable = false)
    private DevolucionVenta devolucion;

    @Column(name = "venta_linea_id", nullable = false, updatable = false)
    private Long ventaLineaId;

    @Column(nullable = false, updatable = false, precision = 18, scale = 4)
    private BigDecimal cantidad;

    @Column(name = "cantidad_unidad_producto", nullable = false, updatable = false, precision = 18, scale = 4)
    private BigDecimal cantidadUnidadProducto;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_producto", nullable = false, updatable = false, length = 10)
    private EstadoProductoDevuelto estadoProducto;

    /** Bodega a la que reingresó ({@code null} si llegó dañado y no hay bodega de averías). */
    @Column(name = "bodega_id", updatable = false)
    private Long bodegaId;

    /** Valor devuelto, tomado de la foto fija de la línea vendida. */
    @Column(nullable = false, updatable = false, precision = 18, scale = 2)
    private BigDecimal valor;
}
