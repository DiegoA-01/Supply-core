package com.proyecto.supply_core.compra.analisis.entity;

import org.hibernate.annotations.Immutable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Criterio de comparación (tabla {@code criterio_evaluacion}, sembrada en V2). Solo lectura. */
@Entity
@Immutable
@Table(name = "criterio_evaluacion")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CriterioEvaluacion {

    @Id
    private Long id;

    /** PRECIO, CALIDAD, ENTREGA, GARANTIA, HISTORIAL o RIESGO. */
    @Column(nullable = false, unique = true, length = 40)
    private String codigo;

    @Column(nullable = false, length = 100)
    private String nombre;
}
