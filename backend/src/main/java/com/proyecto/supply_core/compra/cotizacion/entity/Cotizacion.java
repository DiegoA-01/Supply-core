package com.proyecto.supply_core.compra.cotizacion.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.proyecto.supply_core.compra.cotizacion.enums.EstadoCotizacion;
import com.proyecto.supply_core.compra.cotizacion.enums.OrigenCotizacion;
import com.proyecto.supply_core.compra.rfq.entity.Rfq;
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
 * Cotización de un proveedor para una RFQ (UC-07, tabla {@code cotizacion}). Una por proveedor y RFQ.
 * Los montos ({@code subtotal}, {@code impuestos}, {@code total}) los calcula Java, nunca el proveedor ni la IA.
 */
@Entity
@Table(name = "cotizacion")
@Getter
@Setter
@NoArgsConstructor
public class Cotizacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Empresa dueña del registro. La asigna Hibernate al guardar y filtra toda consulta ({@code @TenantId}). */
    @TenantId
    @Column(name = "empresa_id", nullable = false, updatable = false)
    private Long empresaId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "rfq_id", nullable = false, updatable = false)
    private Rfq rfq;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "proveedor_id", nullable = false, updatable = false)
    private Proveedor proveedor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoCotizacion estado = EstadoCotizacion.BORRADOR;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrigenCotizacion origen = OrigenCotizacion.FORMULARIO;

    @Column(nullable = false, length = 3)
    private String moneda = "COP";

    @Column(name = "plazo_entrega_dias")
    private Integer plazoEntregaDias;

    @Column(name = "garantia_meses")
    private Integer garantiaMeses;

    @Column(name = "validez_hasta")
    private LocalDate validezHasta;

    @Column(name = "condiciones_comerciales", length = 1000)
    private String condicionesComerciales;

    /** Suma de los netos de las líneas (después de descuento, antes de impuestos). */
    @Column(precision = 18, scale = 2)
    private BigDecimal subtotal;

    @Column(precision = 18, scale = 2)
    private BigDecimal impuestos;

    @Column(precision = 18, scale = 2)
    private BigDecimal total;

    /** Archivo original que subió el proveedor (se conserva siempre). */
    @Column(name = "documento_original_id")
    private Long documentoOriginalId;

    @Column(name = "enviado_en")
    private LocalDateTime enviadoEn;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private LocalDateTime creadoEn;

    @OneToMany(mappedBy = "cotizacion", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<CotizacionLinea> lineas = new ArrayList<>();

    /**
     * Agrega una línea enlazándola a esta cotización.
     *
     * @param linea línea nueva
     */
    public void agregarLinea(CotizacionLinea linea) {
        linea.setCotizacion(this);
        lineas.add(linea);
    }

    /** Fija la fecha de creación al insertar. */
    @PrePersist
    void alCrear() {
        if (creadoEn == null) {
            creadoEn = LocalDateTime.now();
        }
    }
}
