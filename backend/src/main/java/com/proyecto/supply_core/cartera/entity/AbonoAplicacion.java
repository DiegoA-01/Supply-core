package com.proyecto.supply_core.cartera.entity;

import java.math.BigDecimal;

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

/** Parte de un abono aplicada a una cuenta (tabla {@code abono_aplicacion}); hereda la empresa del abono. */
@Entity
@Table(name = "abono_aplicacion")
@Getter
@Setter
@NoArgsConstructor
public class AbonoAplicacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "abono_id", nullable = false, updatable = false)
    private AbonoCliente abono;

    @Column(name = "cuenta_id", nullable = false, updatable = false)
    private Long cuentaId;

    @Column(nullable = false, updatable = false, precision = 18, scale = 2)
    private BigDecimal valor;
}
