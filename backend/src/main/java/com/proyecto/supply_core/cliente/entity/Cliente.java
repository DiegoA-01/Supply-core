package com.proyecto.supply_core.cliente.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.hibernate.annotations.TenantId;

import com.proyecto.supply_core.cliente.enums.TipoDocumento;
import com.proyecto.supply_core.venta.entity.ListaPrecio;

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

/**
 * Cliente de la empresa (tabla {@code cliente}, V32). Nunca se borra: se desactiva. El consumidor final de cada
 * empresa no se edita ni se desactiva. Los datos de contacto solo se guardan con la autorización de tratamiento de
 * datos ({@code aceptaDatosEn}, Ley 1581 de 2012).
 */
@Entity
@Table(name = "cliente")
@Getter
@Setter
@NoArgsConstructor
public class Cliente {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Empresa dueña del registro. La asigna Hibernate al guardar y filtra toda consulta ({@code @TenantId}). */
    @TenantId
    @Column(name = "empresa_id", nullable = false, updatable = false)
    private Long empresaId;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_documento", nullable = false, length = 3)
    private TipoDocumento tipoDocumento;

    @Column(name = "numero_documento", nullable = false, length = 20)
    private String numeroDocumento;

    @Column(nullable = false, length = 200)
    private String nombre;

    @Column(length = 30)
    private String telefono;

    @Column(length = 150)
    private String correo;

    @Column(length = 250)
    private String direccion;

    /** Lista de precios del cliente; {@code null} = la General. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lista_precio_id")
    private ListaPrecio listaPrecio;

    @Column(name = "cupo_credito", nullable = false, precision = 18, scale = 2)
    private BigDecimal cupoCredito = BigDecimal.ZERO;

    @Column(name = "plazo_dias", nullable = false)
    private int plazoDias;

    @Column(name = "es_consumidor_final", nullable = false, updatable = false)
    private boolean esConsumidorFinal;

    @Column(nullable = false)
    private boolean activo = true;

    /** Cuándo autorizó el tratamiento de sus datos; {@code null} si no ha autorizado. */
    @Column(name = "acepta_datos_en")
    private LocalDateTime aceptaDatosEn;

    /** Hasta cuándo puede comprar a crédito aunque tenga cuentas vencidas (spec 007, V37); {@code null} = sin excepción. */
    @Column(name = "credito_excepcion_hasta")
    private java.time.LocalDate creditoExcepcionHasta;

    @Column(name = "credito_excepcion_motivo", length = 300)
    private String creditoExcepcionMotivo;

    @Column(name = "credito_excepcion_por_usuario_id")
    private Long creditoExcepcionPorUsuarioId;

    @Column(name = "creado_en", nullable = false, insertable = false, updatable = false)
    private LocalDateTime creadoEn;
}
