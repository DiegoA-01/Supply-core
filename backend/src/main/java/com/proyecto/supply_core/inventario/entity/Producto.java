package com.proyecto.supply_core.inventario.entity;

import java.math.BigDecimal;

import com.proyecto.supply_core.catalogo.entity.CategoriaProducto;
import com.proyecto.supply_core.catalogo.entity.UnidadMedida;

import org.hibernate.annotations.TenantId;
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
 * Producto del catálogo (tabla {@code producto}, UC-02). El stock NO vive aquí: está por bodega
 * en {@link StockProductoBodega} y solo lo cambia {@code MovimientoInventarioService}.
 * Nunca se borra: se desactiva y conserva su historial.
 */
@Entity
@Table(name = "producto")
@Getter
@Setter
@NoArgsConstructor
public class Producto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Empresa dueña del registro. La asigna Hibernate al guardar y filtra toda consulta ({@code @TenantId}). */
    @TenantId
    @Column(name = "empresa_id", nullable = false, updatable = false)
    private Long empresaId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "categoria_producto_id", nullable = false)
    private CategoriaProducto categoria;

    /** Unidad en la que se lleva el stock y se expresan los movimientos. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "unidad_medida_id", nullable = false)
    private UnidadMedida unidadMedida;

    /** Único, en mayúsculas. */
    @Column(nullable = false, length = 50)
    private String codigo;

    @Column(nullable = false, length = 200)
    private String nombre;

    @Column(length = 500)
    private String descripcion;

    /** Total entre todas las bodegas por debajo del cual se genera alerta (UC flujo paso 1). */
    @Column(name = "stock_minimo", nullable = false, precision = 18, scale = 4)
    private BigDecimal stockMinimo = BigDecimal.ZERO;

    @Column(name = "stock_maximo", precision = 18, scale = 4)
    private BigDecimal stockMaximo;

    /** Precio de referencia (para detectar precios atípicos en cotizaciones). */
    @Column(name = "costo_referencia", precision = 18, scale = 4)
    private BigDecimal costoReferencia;

    /** Costo promedio ponderado en la unidad del producto; lo actualiza la recepción (spec 008, V36). */
    @Column(name = "costo_promedio", precision = 18, scale = 4)
    private BigDecimal costoPromedio;

    @Column(nullable = false)
    private boolean activo = true;

    /** Días hábiles que tarda en llegar un pedido de este producto; {@code null} = el valor de la empresa. */
    @Column(name = "plazo_entrega_dias")
    private Integer plazoEntregaDias;

    /** Días hábiles de colchón para este producto; {@code null} = el valor de la empresa. */
    @Column(name = "dias_seguridad")
    private Integer diasSeguridad;

    /** Código de barras para el lector del punto de venta (único en la empresa). */
    @Column(name = "codigo_barras", length = 50)
    private String codigoBarras;

    /** Impuesto de venta; {@code null} si aún no se definió. */
    @jakarta.persistence.ManyToOne(fetch = jakarta.persistence.FetchType.LAZY)
    @jakarta.persistence.JoinColumn(name = "tarifa_impuesto_id")
    private com.proyecto.supply_core.venta.entity.TarifaImpuesto tarifaImpuesto;

    /** {@code false} para productos que solo se consumen internamente. */
    @Column(nullable = false)
    private boolean vendible = true;

    /** El precio de mostrador incluye el impuesto (costumbre en Colombia); Java separa base e impuesto. */
    @Column(name = "precio_incluye_impuesto", nullable = false)
    private boolean precioIncluyeImpuesto = true;

    /**
     * @return costo con que se mide el margen: el promedio ponderado, o el último de compra si aún no hay promedio
     */
    public BigDecimal costoVigente() {
        return costoPromedio != null ? costoPromedio : costoReferencia;
    }
}
