package com.proyecto.supply_core.recepcion.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.proyecto.supply_core.recepcion.entity.Recepcion;

/** Acceso a la tabla {@code recepcion}. */
public interface RecepcionRepository extends JpaRepository<Recepcion, Long> {

    /**
     * Busca por id con orden, proveedor, bodega y líneas (con su línea de orden y producto).
     *
     * @param id recepción
     * @return recepción, si existe en la empresa
     */
    @EntityGraph(attributePaths = { "orden", "orden.proveedor", "bodega", "lineas", "lineas.ordenLinea",
            "lineas.ordenLinea.producto" })
    Optional<Recepcion> findDetalleById(Long id);

    /**
     * Recepciones de una orden, de la más antigua a la más reciente.
     *
     * @param ordenId orden
     * @return recepciones con su detalle
     */
    @EntityGraph(attributePaths = { "orden", "orden.proveedor", "bodega", "lineas", "lineas.ordenLinea",
            "lineas.ordenLinea.producto" })
    List<Recepcion> findByOrden_IdOrderByRecibidoEnAscIdAsc(Long ordenId);
}
