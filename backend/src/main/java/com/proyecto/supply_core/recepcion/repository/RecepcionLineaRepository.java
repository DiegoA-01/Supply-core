package com.proyecto.supply_core.recepcion.repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.proyecto.supply_core.recepcion.entity.RecepcionLinea;

/** Acceso a la tabla {@code recepcion_linea}. */
public interface RecepcionLineaRepository extends JpaRepository<RecepcionLinea, Long> {

    /** Totales de lo recibido de una línea de la orden en todas sus recepciones. */
    interface AgregadoLinea {
        /**
         * @return id de la línea de la orden
         */
        Long getOrdenLineaId();

        /**
         * @return suma de lo aceptado (suma stock)
         */
        BigDecimal getAceptada();

        /**
         * @return suma de lo recibido
         */
        BigDecimal getRecibida();

        /**
         * @return suma de lo rechazado
         */
        BigDecimal getRechazada();

        /**
         * @return suma de lo que llegó de más
         */
        BigDecimal getExcedente();
    }

    /**
     * Totales por línea de la orden (solo las que tienen recepciones).
     *
     * @param ordenId orden
     * @return aceptado, recibido, rechazado y excedente por línea
     */
    @Query("""
            select l.ordenLinea.id as ordenLineaId, sum(l.cantidadAceptada) as aceptada,
                   sum(l.cantidadRecibida) as recibida, sum(l.cantidadRechazada) as rechazada,
                   sum(l.cantidadExcedente) as excedente
            from RecepcionLinea l where l.recepcion.orden.id = :ordenId group by l.ordenLinea.id
            """)
    List<AgregadoLinea> agregadoPorLinea(@Param("ordenId") Long ordenId);

    /**
     * Indica si la orden tiene líneas con excedente sin resolver.
     *
     * @param ordenId orden
     * @return {@code true} si alguna línea está EN_REVISION
     */
    @Query("""
            select count(l) > 0 from RecepcionLinea l
            where l.recepcion.orden.id = :ordenId
              and l.estado = com.proyecto.supply_core.recepcion.enums.EstadoRecepcionLinea.EN_REVISION
            """)
    boolean hayEnRevision(@Param("ordenId") Long ordenId);

    /**
     * Busca una línea con su recepción, orden, bodega, línea de orden y producto. El {@code join fetch} de la
     * recepción hace que una línea de otra empresa no aparezca (la recepción está filtrada por empresa).
     *
     * @param id línea de recepción
     * @return línea, si existe en la empresa
     */
    @Query("""
            select l from RecepcionLinea l
              join fetch l.recepcion r join fetch r.orden o join fetch o.proveedor join fetch r.bodega
              join fetch l.ordenLinea ol join fetch ol.producto
            where l.id = :id
            """)
    Optional<RecepcionLinea> findDetalleById(@Param("id") Long id);
}
