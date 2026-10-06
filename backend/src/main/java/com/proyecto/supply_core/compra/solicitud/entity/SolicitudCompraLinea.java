package com.proyecto.supply_core.compra.solicitud.entity;

import java.math.BigDecimal;

import com.proyecto.supply_core.catalogo.entity.UnidadMedida;
import com.proyecto.supply_core.inventario.entity.Producto;

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

/**
 * Línea de una solicitud (tabla {@code solicitud_compra_linea}): producto, unidad y cantidad &gt; 0.
 * La unidad puede ser distinta a la del producto si mide lo mismo (p. ej. CAJA24 frente a UND).
 */
@Entity
@Table(name = "solicitud_compra_linea")
@Getter
@Setter
@NoArgsConstructor
public class SolicitudCompraLinea {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "solicitud_compra_id", nullable = false)
    private SolicitudCompra solicitud;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "producto_id", nullable = false)
    private Producto producto;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "unidad_medida_id", nullable = false)
    private UnidadMedida unidadMedida;

    @Column(nullable = false, precision = 18, scale = 4)
    private BigDecimal cantidad;
}
