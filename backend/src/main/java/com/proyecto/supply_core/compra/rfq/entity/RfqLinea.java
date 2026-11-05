package com.proyecto.supply_core.compra.rfq.entity;

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

/** Producto que se pide cotizar (tabla {@code rfq_linea}), copiado de la solicitud + especificaciones. */
@Entity
@Table(name = "rfq_linea")
@Getter
@Setter
@NoArgsConstructor
public class RfqLinea {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "rfq_id", nullable = false)
    private Rfq rfq;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "producto_id", nullable = false)
    private Producto producto;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "unidad_medida_id", nullable = false)
    private UnidadMedida unidadMedida;

    @Column(nullable = false, precision = 18, scale = 4)
    private BigDecimal cantidad;

    /** Detalle técnico para el proveedor (marca, medidas, calidad...). */
    @Column(length = 1000)
    private String especificaciones;
}
