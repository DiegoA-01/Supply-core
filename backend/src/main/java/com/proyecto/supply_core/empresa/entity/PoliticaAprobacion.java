package com.proyecto.supply_core.empresa.entity;

import java.math.BigDecimal;

import com.proyecto.supply_core.empresa.enums.TipoPolitica;
import com.proyecto.supply_core.usuario.entity.Rol;

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
 * Política de aprobación (tabla {@code politica_aprobacion}): para un tipo de operación y un
 * rango de montos {@code [montoDesde, montoHasta)}, indica qué rol debe aprobar.
 * {@code montoHasta = null} significa "sin tope". Entre políticas activas del mismo tipo los
 * rangos no se superponen.
 */
@Entity
@Table(name = "politica_aprobacion")
@Getter
@Setter
@NoArgsConstructor
public class PoliticaAprobacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "empresa_id", nullable = false)
    private Long empresaId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoPolitica tipo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "rol_aprobador_id", nullable = false)
    private Rol rolAprobador;

    /** Inclusive. */
    @Column(name = "monto_desde", nullable = false, precision = 18, scale = 2)
    private BigDecimal montoDesde;

    /** Exclusivo; {@code null} = sin tope. */
    @Column(name = "monto_hasta", precision = 18, scale = 2)
    private BigDecimal montoHasta;

    @Column(nullable = false)
    private boolean activo = true;

    /**
     * Indica si el monto cae en el rango {@code [montoDesde, montoHasta)}.
     *
     * @param monto monto a evaluar
     * @return {@code true} si esta política aplica a ese monto
     */
    public boolean cubre(BigDecimal monto) {
        return monto.compareTo(montoDesde) >= 0 && (montoHasta == null || monto.compareTo(montoHasta) < 0);
    }

    /**
     * Indica si el rango de esta política se cruza con otro rango (ambos semiabiertos).
     *
     * @param desde inicio del otro rango (inclusive)
     * @param hasta fin del otro rango (exclusivo; {@code null} = sin tope)
     * @return {@code true} si comparten algún monto
     */
    public boolean seSuperponeCon(BigDecimal desde, BigDecimal hasta) {
        boolean empiezaAntesDeQueTermineElOtro = hasta == null || montoDesde.compareTo(hasta) < 0;
        boolean elOtroEmpiezaAntesDeQueTermine = montoHasta == null || desde.compareTo(montoHasta) < 0;
        return empiezaAntesDeQueTermineElOtro && elOtroEmpiezaAntesDeQueTermine;
    }
}
