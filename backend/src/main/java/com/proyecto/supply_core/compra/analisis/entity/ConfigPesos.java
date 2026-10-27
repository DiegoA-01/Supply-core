package com.proyecto.supply_core.compra.analisis.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.TenantId;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Versión de la matriz de pesos (tabla {@code config_pesos_evaluacion}). Nunca se edita: cambiar los pesos
 * crea una versión nueva y cierra la anterior, y cada análisis guarda la versión que usó (snapshot).
 */
@Entity
@Table(name = "config_pesos_evaluacion")
@Getter
@Setter
@NoArgsConstructor
public class ConfigPesos {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Empresa dueña del registro. La asigna Hibernate al guardar y filtra toda consulta ({@code @TenantId}). */
    @TenantId
    @Column(name = "empresa_id", nullable = false, updatable = false)
    private Long empresaId;

    @Column(name = "vigente_desde", nullable = false, updatable = false)
    private LocalDateTime vigenteDesde;

    @Column(name = "vigente_hasta")
    private LocalDateTime vigenteHasta;

    @Column(nullable = false)
    private boolean activo = true;

    @OneToMany(mappedBy = "config", cascade = CascadeType.ALL)
    private List<Detalle> detalles = new ArrayList<>();

    /**
     * Agrega el peso de un criterio.
     *
     * @param criterio criterio
     * @param peso     peso (0–100)
     */
    public void agregar(CriterioEvaluacion criterio, BigDecimal peso) {
        Detalle d = new Detalle();
        d.setConfig(this);
        d.setCriterio(criterio);
        d.setPeso(peso);
        detalles.add(d);
    }

    /**
     * Peso de un criterio (tabla {@code config_pesos_evaluacion_detalle}).
     */
    @Entity
    @Table(name = "config_pesos_evaluacion_detalle")
    @Getter
    @Setter
    @NoArgsConstructor
    public static class Detalle {

        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        @ManyToOne(fetch = FetchType.LAZY, optional = false)
        @JoinColumn(name = "config_pesos_evaluacion_id", nullable = false, updatable = false)
        private ConfigPesos config;

        @ManyToOne(fetch = FetchType.EAGER, optional = false)
        @JoinColumn(name = "criterio_evaluacion_id", nullable = false, updatable = false)
        private CriterioEvaluacion criterio;

        @Column(nullable = false, precision = 5, scale = 2, updatable = false)
        private BigDecimal peso;
    }
}
