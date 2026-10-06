package com.proyecto.supply_core.compra.orden.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.proyecto.supply_core.compra.orden.entity.OrdenEstadoHistorial;

/** Acceso a la tabla {@code orden_compra_estado_historial}. */
public interface OrdenEstadoHistorialRepository extends JpaRepository<OrdenEstadoHistorial, Long> {

    /**
     * Historial de una orden, del más antiguo al más reciente.
     *
     * @param ordenId orden
     * @return cambios de estado
     */
    List<OrdenEstadoHistorial> findByOrdenIdOrderByCreadoEnAscIdAsc(Long ordenId);
}
