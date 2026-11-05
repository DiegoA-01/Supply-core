package com.proyecto.supply_core.compra.orden.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.proyecto.supply_core.compra.orden.enums.EstadoOrden;
import com.proyecto.supply_core.inventario.entity.Bodega;
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
 * Orden de compra (UC-10, tabla {@code orden_compra}). Inmutable en su contenido: cambiar cantidades o
 * entrega crea una versión nueva (mismo consecutivo, {@code version + 1}) y la anterior queda REEMPLAZADA.
 * Solo cambia su estado.
 */
@Entity
@Table(name = "orden_compra")
@Getter
@Setter
@NoArgsConstructor
public class OrdenCompra {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Empresa dueña del registro. La asigna Hibernate al guardar y filtra toda consulta ({@code @TenantId}). */
    @TenantId
    @Column(name = "empresa_id", nullable = false, updatable = false)
    private Long empresaId;

    /** Adjudicación de origen; {@code null} en una orden directa de contrato (paso 18-b). */
    @Column(name = "adjudicacion_id", updatable = false)
    private Long adjudicacionId;

    /** Contrato de origen; solo en una orden directa. */
    @Column(name = "contrato_id", updatable = false)
    private Long contratoId;

    /** Solicitud de origen; solo en una orden directa (en las demás se llega por adjudicación y RFQ). */
    @Column(name = "solicitud_id", updatable = false)
    private Long solicitudId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "proveedor_id", nullable = false, updatable = false)
    private Proveedor proveedor;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bodega_destino_id", nullable = false, updatable = false)
    private Bodega bodegaDestino;

    /** Comprador que la emitió. */
    @Column(name = "usuario_id", updatable = false)
    private Long usuarioId;

    @Column(nullable = false, length = 30, updatable = false)
    private String consecutivo;

    @Column(nullable = false, updatable = false)
    private int version = 1;

    /** Versión anterior ({@code null} en la versión 1). */
    @Column(name = "orden_padre_id", updatable = false)
    private Long ordenPadreId;

    @Column(name = "motivo_version", length = 500, updatable = false)
    private String motivoVersion;

    @Column(nullable = false, precision = 18, scale = 2, updatable = false)
    private BigDecimal subtotal;

    @Column(nullable = false, precision = 18, scale = 2, updatable = false)
    private BigDecimal impuestos;

    @Column(nullable = false, precision = 18, scale = 2, updatable = false)
    private BigDecimal total;

    @Column(name = "lugar_entrega", nullable = false, length = 250, updatable = false)
    private String lugarEntrega;

    /** Base para medir la puntualidad del proveedor (UC-14). */
    @Column(name = "fecha_entrega_esperada", nullable = false, updatable = false)
    private LocalDate fechaEntregaEsperada;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoOrden estado = EstadoOrden.EMITIDA;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private LocalDateTime creadoEn;

    @OneToMany(mappedBy = "orden", cascade = CascadeType.ALL)
    @OrderBy("id")
    private List<OrdenCompraLinea> lineas = new ArrayList<>();

    /**
     * Agrega una línea enlazándola a esta orden.
     *
     * @param linea línea nueva
     */
    public void agregarLinea(OrdenCompraLinea linea) {
        linea.setOrden(this);
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
