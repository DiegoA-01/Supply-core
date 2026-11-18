package com.proyecto.supply_core.compra.analisis.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.proyecto.supply_core.compra.analisis.enums.EstadoAnalisis;
import com.proyecto.supply_core.compra.cotizacion.entity.Cotizacion;
import com.proyecto.supply_core.compra.rfq.entity.Rfq;

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
 * Análisis comparativo de las cotizaciones de una RFQ (UC-08, tabla {@code analisis_cotizacion}).
 * En BORRADOR se puede regenerar; PUBLICADO queda fijo y habilita la adjudicación (paso 14).
 */
@Entity
@Table(name = "analisis_cotizacion")
@Getter
@Setter
@NoArgsConstructor
public class Analisis {

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

    /** Versión de pesos usada (snapshot). */
    @Column(name = "config_pesos_evaluacion_id", nullable = false, updatable = false)
    private Long configPesosId;

    /** Comprador que lo generó. */
    @Column(name = "usuario_id", nullable = false, updatable = false)
    private Long usuarioId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoAnalisis estado = EstadoAnalisis.BORRADOR;

    /** Explicación del ranking completo redactada por la IA (opcional). */
    @Column(name = "explicacion_general", columnDefinition = "TEXT")
    private String explicacionGeneral;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private LocalDateTime creadoEn;

    @Column(name = "publicado_en")
    private LocalDateTime publicadoEn;

    @OneToMany(mappedBy = "analisis", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("posicionRanking")
    private List<Resultado> resultados = new ArrayList<>();

    @OneToMany(mappedBy = "analisis", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Detalle> detalles = new ArrayList<>();

    /** Fija la fecha de creación al insertar. */
    @PrePersist
    void alCrear() {
        if (creadoEn == null) {
            creadoEn = LocalDateTime.now();
        }
    }

    /**
     * Resultado de una cotización (tabla {@code analisis_cotizacion_resultado}).
     */
    @Entity
    @Table(name = "analisis_cotizacion_resultado")
    @Getter
    @Setter
    @NoArgsConstructor
    public static class Resultado {

        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        @ManyToOne(fetch = FetchType.LAZY, optional = false)
        @JoinColumn(name = "analisis_cotizacion_id", nullable = false, updatable = false)
        private Analisis analisis;

        @ManyToOne(fetch = FetchType.LAZY, optional = false)
        @JoinColumn(name = "cotizacion_id", nullable = false, updatable = false)
        private Cotizacion cotizacion;

        @Column(name = "costo_total", nullable = false, precision = 18, scale = 2)
        private BigDecimal costoTotal;

        @Column(name = "score_final", nullable = false, precision = 5, scale = 2)
        private BigDecimal scoreFinal;

        @Column(name = "posicion_ranking", nullable = false)
        private short posicionRanking;

        /** Explicación de esta cotización (la arma Java con los puntajes). */
        @Column(name = "explicacion_ia", columnDefinition = "TEXT")
        private String explicacion;

        @OneToMany(mappedBy = "resultado", cascade = CascadeType.ALL, orphanRemoval = true)
        private List<Anomalia> anomalias = new ArrayList<>();
    }

    /**
     * Puntaje de una cotización en un criterio (tabla {@code analisis_cotizacion_detalle}).
     */
    @Entity
    @Table(name = "analisis_cotizacion_detalle")
    @Getter
    @Setter
    @NoArgsConstructor
    public static class Detalle {

        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        @ManyToOne(fetch = FetchType.LAZY, optional = false)
        @JoinColumn(name = "analisis_cotizacion_id", nullable = false, updatable = false)
        private Analisis analisis;

        @Column(name = "cotizacion_id", nullable = false, updatable = false)
        private Long cotizacionId;

        @ManyToOne(fetch = FetchType.EAGER, optional = false)
        @JoinColumn(name = "criterio_evaluacion_id", nullable = false, updatable = false)
        private CriterioEvaluacion criterio;

        /** Peso copiado de la versión de pesos (snapshot). */
        @Column(name = "peso_aplicado", nullable = false, precision = 5, scale = 2)
        private BigDecimal pesoAplicado;

        @Column(nullable = false, precision = 5, scale = 2)
        private BigDecimal puntaje;

        @Column(name = "score_ponderado", nullable = false, precision = 6, scale = 2)
        private BigDecimal scorePonderado;
    }

    /**
     * Alerta sobre una cotización (tabla {@code anomalia_cotizacion}); nunca la excluye.
     */
    @Entity
    @Table(name = "anomalia_cotizacion")
    @Getter
    @Setter
    @NoArgsConstructor
    public static class Anomalia {

        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        @ManyToOne(fetch = FetchType.LAZY, optional = false)
        @JoinColumn(name = "analisis_resultado_id", nullable = false, updatable = false)
        private Resultado resultado;

        @Column(nullable = false, length = 50)
        private String tipo;

        /** BAJA, MEDIA o ALTA. */
        @Column(nullable = false)
        private String severidad;

        @Column(nullable = false, length = 500)
        private String descripcion;
    }
}
