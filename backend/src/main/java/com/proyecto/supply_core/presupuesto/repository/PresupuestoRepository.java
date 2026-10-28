package com.proyecto.supply_core.presupuesto.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.proyecto.supply_core.presupuesto.entity.Presupuesto;

import jakarta.persistence.LockModeType;

/** Acceso a la tabla {@code presupuesto}. */
public interface PresupuestoRepository extends JpaRepository<Presupuesto, Long> {

    /**
     * Indica si el centro de costo ya tiene presupuesto en ese mes.
     *
     * @param centroCostoId centro de costo
     * @param anio          año
     * @param mes           mes del 1 al 12
     * @return {@code true} si existe
     */
    boolean existsByCentroCostoIdAndAnioAndMes(Long centroCostoId, Integer anio, Integer mes);

    /**
     * Busca el presupuesto del mes bloqueando la fila ({@code SELECT ... FOR UPDATE}): dos solicitudes del mismo
     * centro y mes que se envían a la vez se evalúan una después de la otra y no se pasan juntas del tope.
     *
     * @param centroCostoId centro de costo
     * @param anio          año
     * @param mes           mes del 1 al 12
     * @return presupuesto bloqueado, si existe
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Presupuesto p where p.centroCostoId = :centroCostoId and p.anio = :anio and p.mes = :mes")
    Optional<Presupuesto> bloquear(@Param("centroCostoId") Long centroCostoId, @Param("anio") Integer anio,
            @Param("mes") Integer mes);

    /**
     * Lista presupuestos con filtros opcionales ({@code null} = no filtrar), del más reciente al más antiguo.
     *
     * @param centroCostoId centro de costo
     * @param anio          año
     * @return presupuestos
     */
    @Query("""
            select p from Presupuesto p
            where (:centroCostoId is null or p.centroCostoId = :centroCostoId)
              and (:anio is null or p.anio = :anio)
            order by p.anio desc, p.mes desc, p.centroCostoId
            """)
    List<Presupuesto> buscar(@Param("centroCostoId") Long centroCostoId, @Param("anio") Integer anio);
}
