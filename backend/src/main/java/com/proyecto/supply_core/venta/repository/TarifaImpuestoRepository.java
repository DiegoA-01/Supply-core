package com.proyecto.supply_core.venta.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.proyecto.supply_core.venta.entity.TarifaImpuesto;

/** Impuestos de venta de la empresa ({@code @TenantId}). */
public interface TarifaImpuestoRepository extends JpaRepository<TarifaImpuesto, Long> {

    /**
     * @return impuestos de la empresa, de mayor a menor porcentaje
     */
    List<TarifaImpuesto> findAllByOrderByPorcentajeDescNombreAsc();

    /**
     * @param codigo código (IVA19, EXENTO…)
     * @return {@code true} si la empresa ya lo tiene
     */
    boolean existsByCodigoIgnoreCase(String codigo);

    /**
     * @param codigo código exacto (IVA19…)
     * @return el impuesto de la empresa con ese código, si existe
     */
    java.util.Optional<TarifaImpuesto> findByCodigo(String codigo);
}
