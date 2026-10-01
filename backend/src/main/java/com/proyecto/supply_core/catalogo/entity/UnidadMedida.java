package com.proyecto.supply_core.catalogo.entity;

import java.math.BigDecimal;

import com.proyecto.supply_core.catalogo.enums.Magnitud;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Unidad de medida (tabla {@code unidad_medida}).
 * <p>{@code factorConversionBase} = cuántas unidades base de su {@link Magnitud} equivale una
 * unidad (Docena = 12 UND, Gramo = 0.001 KG). Código, magnitud y factor no cambian tras crearse:
 * cambiarlos alteraría las cantidades ya registradas. Nunca se borra: se desactiva.</p>
 */
@Entity
@Table(name = "unidad_medida")
@Getter
@Setter
@NoArgsConstructor
public class UnidadMedida {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Único, en mayúsculas (UND, KG, CAJA24...). */
    @Column(nullable = false, unique = true, length = 20, updatable = false)
    private String codigo;

    @Column(nullable = false, length = 80)
    private String nombre;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private Magnitud magnitud;

    @Column(name = "factor_conversion_base", nullable = false, precision = 18, scale = 6, updatable = false)
    private BigDecimal factorConversionBase;

    @Column(nullable = false)
    private boolean activo = true;
}
