package com.proyecto.supply_core.proveedor.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.proyecto.supply_core.proveedor.entity.EvaluacionProveedor;

/** Acceso a la tabla {@code evaluacion_proveedor}. */
public interface EvaluacionProveedorRepository extends JpaRepository<EvaluacionProveedor, Long> {

    /**
     * Evaluaciones de un proveedor.
     *
     * @param proveedorId proveedor
     * @param pageable    página y orden
     * @return página de evaluaciones
     */
    Page<EvaluacionProveedor> findByProveedorId(Long proveedorId, Pageable pageable);

    /**
     * Indica si la orden ya fue evaluada para ese proveedor.
     *
     * @param proveedorId   proveedor
     * @param ordenCompraId orden
     * @return {@code true} si ya existe
     */
    boolean existsByProveedorIdAndOrdenCompraId(Long proveedorId, Long ordenCompraId);

    /**
     * Promedio de los scores con datos (los {@code null} no cuentan).
     *
     * @param proveedorId proveedor
     * @return promedio o {@code null} si no hay ninguno
     */
    @Query("select avg(e.score) from EvaluacionProveedor e where e.proveedorId = :proveedorId and e.score is not null")
    Double promedioScore(@Param("proveedorId") Long proveedorId);
}
