package com.proyecto.supply_core.venta.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.proyecto.supply_core.caja.enums.MedioPago;
import com.proyecto.supply_core.venta.enums.FormaReembolso;

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
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Devolución de un cliente (tabla {@code devolucion_venta}, spec 006): consecutivo propio, motivo, forma de reembolso
 * y quién la autorizó. No se edita ni se borra.
 */
@Entity
@Table(name = "devolucion_venta")
@Getter
@Setter
@NoArgsConstructor
public class DevolucionVenta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Empresa dueña del registro. La asigna Hibernate al guardar y filtra toda consulta ({@code @TenantId}). */
    @TenantId
    @Column(name = "empresa_id", nullable = false, updatable = false)
    private Long empresaId;

    @Column(nullable = false, updatable = false, length = 20)
    private String consecutivo;

    @Column(name = "venta_id", nullable = false, updatable = false)
    private Long ventaId;

    @Column(name = "turno_id", nullable = false, updatable = false)
    private Long turnoId;

    @Column(nullable = false, updatable = false, length = 300)
    private String motivo;

    @Enumerated(EnumType.STRING)
    @Column(name = "forma_reembolso", nullable = false, updatable = false, length = 20)
    private FormaReembolso formaReembolso;

    /** Medio por el que salió el dinero ({@code null} si quedó como saldo a favor). */
    @Enumerated(EnumType.STRING)
    @Column(name = "medio_reembolso", updatable = false, length = 20)
    private MedioPago medioReembolso;

    @Column(name = "total_reembolso", nullable = false, updatable = false, precision = 18, scale = 2)
    private BigDecimal totalReembolso;

    @Column(name = "usuario_id", nullable = false, updatable = false)
    private Long usuarioId;

    @Column(name = "autorizado_por_usuario_id", nullable = false, updatable = false)
    private Long autorizadoPorUsuarioId;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private LocalDateTime creadoEn;

    @OneToMany(mappedBy = "devolucion", cascade = CascadeType.ALL)
    @OrderBy("id")
    private List<DevolucionVentaLinea> lineas = new ArrayList<>();
}
