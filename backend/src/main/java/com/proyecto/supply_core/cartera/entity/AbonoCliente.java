package com.proyecto.supply_core.cartera.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.proyecto.supply_core.caja.enums.MedioPago;

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

/** Abono de un cliente a su cartera (tabla {@code abono_cliente}, spec 007). Entra a la caja del turno. */
@Entity
@Table(name = "abono_cliente")
@Getter
@Setter
@NoArgsConstructor
public class AbonoCliente {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Empresa dueña del registro. La asigna Hibernate al guardar y filtra toda consulta ({@code @TenantId}). */
    @TenantId
    @Column(name = "empresa_id", nullable = false, updatable = false)
    private Long empresaId;

    @Column(nullable = false, updatable = false, length = 20)
    private String consecutivo;

    @Column(name = "cliente_id", nullable = false, updatable = false)
    private Long clienteId;

    @Column(name = "turno_id", nullable = false, updatable = false)
    private Long turnoId;

    @Column(nullable = false, updatable = false, precision = 18, scale = 2)
    private BigDecimal valor;

    @Enumerated(EnumType.STRING)
    @Column(name = "medio_pago", nullable = false, updatable = false, length = 20)
    private MedioPago medioPago;

    @Column(updatable = false, length = 40)
    private String referencia;

    @Column(name = "usuario_id", nullable = false, updatable = false)
    private Long usuarioId;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private LocalDateTime creadoEn;

    @OneToMany(mappedBy = "abono", cascade = CascadeType.ALL)
    @OrderBy("id")
    private List<AbonoAplicacion> aplicaciones = new ArrayList<>();
}
