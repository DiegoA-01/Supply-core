package com.proyecto.supply_core.empresa.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Empresa cliente del SaaS (tabla {@code empresa}); es el tenant al que pertenecen todos los datos de negocio.
 * Cada empresa edita solo la suya; el alta y la baja las hará la consola de la plataforma (fase 2).
 */
@Entity
@Table(name = "empresa")
@Getter
@Setter
@NoArgsConstructor
public class Empresa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "razon_social", nullable = false, length = 200)
    private String razonSocial;

    @Column(nullable = false, unique = true, length = 30)
    private String nit;

    /** Identifica a la empresa en las rutas públicas (p. ej. el autorregistro de proveedores). */
    @Column(name = "codigo_publico", nullable = false, unique = true, length = 40)
    private String codigoPublico;

    @Column(name = "direccion_principal", length = 250)
    private String direccionPrincipal;

    /** Código ISO 4217 (p. ej. COP). */
    @Column(name = "moneda_base", nullable = false, length = 3, columnDefinition = "CHAR(3)")
    private String monedaBase;

    /** Tope de los anticipos como porcentaje del total de la orden (0 a 100). */
    @Column(name = "tope_anticipo_pct", nullable = false, precision = 5, scale = 2)
    private BigDecimal topeAnticipoPct = new BigDecimal("50.00");

    /** Diferencia aceptada entre la factura y lo recibido, en porcentaje del total esperado (0 a 100). */
    @Column(name = "tolerancia_factura_pct", nullable = false, precision = 5, scale = 2)
    private BigDecimal toleranciaFacturaPct = new BigDecimal("1.00");

    /** Diferencia aceptada entre la factura y lo recibido, en monto fijo (se acepta si cumple cualquiera de las dos). */
    @Column(name = "tolerancia_factura_monto", nullable = false, precision = 18, scale = 2)
    private BigDecimal toleranciaFacturaMonto = new BigDecimal("0.00");

    /** Horas que tiene una solicitud pendiente para decidirse antes de escalar (0 = sin SLA). */
    @Column(name = "sla_aprobacion_horas", nullable = false)
    private int slaAprobacionHoras = 24;

    /** Días hábiles que suele tardar un pedido en llegar; lo usan los productos sin plazo propio. */
    @Column(name = "plazo_entrega_dias_defecto", nullable = false)
    private int plazoEntregaDiasDefecto = 5;

    /** Días hábiles de colchón por retrasos o picos de consumo; lo usan los productos sin valor propio. */
    @Column(name = "dias_seguridad_defecto", nullable = false)
    private int diasSeguridadDefecto = 2;

    /** Días de calendario que debe cubrir lo que se pide, contados desde que llega. */
    @Column(name = "dias_cobertura_defecto", nullable = false)
    private int diasCoberturaDefecto = 30;

    /** El dueño delegó en el jefe de compras fijar los precios de venta. */
    @Column(name = "precios_delegados_compras", nullable = false)
    private boolean preciosDelegadosCompras;

    @Column(name = "creado_en", nullable = false, updatable = false, insertable = false)
    private LocalDateTime creadoEn;
}
