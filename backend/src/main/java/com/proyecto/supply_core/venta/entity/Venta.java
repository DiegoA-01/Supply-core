package com.proyecto.supply_core.venta.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.proyecto.supply_core.venta.enums.EstadoVenta;

import org.hibernate.annotations.TenantId;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Venta confirmada (tabla {@code venta}, spec 004). Guarda la foto fija de los montos que calculó Java; nunca se
 * edita ni se borra. Las líneas y los pagos heredan la empresa de la venta.
 */
@Entity
@Table(name = "venta")
@Getter
@Setter
@NoArgsConstructor
public class Venta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Empresa dueña del registro. La asigna Hibernate al guardar y filtra toda consulta ({@code @TenantId}). */
    @TenantId
    @Column(name = "empresa_id", nullable = false, updatable = false)
    private Long empresaId;

    @Column(nullable = false, updatable = false, length = 20)
    private String consecutivo;

    @Column(name = "caja_id", nullable = false, updatable = false)
    private Long cajaId;

    @Column(name = "turno_id", nullable = false, updatable = false)
    private Long turnoId;

    @Column(name = "bodega_id", nullable = false, updatable = false)
    private Long bodegaId;

    @Column(name = "cliente_id", nullable = false, updatable = false)
    private Long clienteId;

    @Column(name = "vendedor_usuario_id", updatable = false)
    private Long vendedorUsuarioId;

    @Column(name = "cajero_usuario_id", nullable = false, updatable = false)
    private Long cajeroUsuarioId;

    @Column(name = "lista_precio_id", nullable = false, updatable = false)
    private Long listaPrecioId;

    @Column(name = "pedido_venta_id", updatable = false)
    private Long pedidoVentaId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private EstadoVenta estado = EstadoVenta.CONFIRMADA;

    @Column(nullable = false, updatable = false, precision = 18, scale = 2)
    private BigDecimal subtotal;

    @Column(name = "descuento_total", nullable = false, updatable = false, precision = 18, scale = 2)
    private BigDecimal descuentoTotal;

    @Column(name = "base_gravable", nullable = false, updatable = false, precision = 18, scale = 2)
    private BigDecimal baseGravable;

    @Column(nullable = false, updatable = false, precision = 18, scale = 2)
    private BigDecimal impuestos;

    @Column(nullable = false, updatable = false, precision = 18, scale = 2)
    private BigDecimal total;

    /** Ajuste del redondeo del efectivo a $50 (decisión D3): lo cobrado es {@code total + redondeo}. */
    @Column(nullable = false, updatable = false, precision = 18, scale = 2)
    private BigDecimal redondeo = BigDecimal.ZERO;

    @Column(nullable = false, updatable = false, precision = 18, scale = 2)
    private BigDecimal cambio = BigDecimal.ZERO;

    @Column(name = "es_credito", nullable = false, updatable = false)
    private boolean esCredito;

    @Column(name = "clave_idempotencia", nullable = false, updatable = false, length = 64)
    private String claveIdempotencia;

    @Column(name = "confirmada_en", nullable = false, updatable = false)
    private LocalDateTime confirmadaEn;

    @Column(name = "anulada_en")
    private LocalDateTime anuladaEn;

    @Column(name = "anulada_por_usuario_id")
    private Long anuladaPorUsuarioId;

    @Column(name = "autorizada_por_usuario_id")
    private Long autorizadaPorUsuarioId;

    @Column(name = "motivo_anulacion", length = 300)
    private String motivoAnulacion;

    @Column(name = "anulacion_autorizada_por_usuario_id")
    private Long anulacionAutorizadaPorUsuarioId;

    @Version
    private int version;

    @OneToMany(mappedBy = "venta", cascade = CascadeType.ALL)
    @OrderBy("id")
    private List<VentaLinea> lineas = new ArrayList<>();

    @OneToMany(mappedBy = "venta", cascade = CascadeType.ALL)
    @OrderBy("id")
    private List<VentaPago> pagos = new ArrayList<>();

    /**
     * @return lo que paga el cliente: el total más el ajuste del redondeo
     */
    public BigDecimal totalAPagar() {
        return total.add(redondeo);
    }
}
