package com.proyecto.supply_core.caja.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.proyecto.supply_core.caja.entity.TurnoCaja;
import com.proyecto.supply_core.caja.enums.EstadoTurno;

import jakarta.persistence.LockModeType;

/** Turnos de caja ({@code @TenantId}). */
public interface TurnoCajaRepository extends JpaRepository<TurnoCaja, Long> {

    /**
     * @param cajeroId cajero
     * @return su turno abierto, si tiene
     */
    @Query("select t from TurnoCaja t join fetch t.caja c join fetch c.bodega "
            + "where t.cajeroUsuarioId = :cajeroId and t.estado = com.proyecto.supply_core.caja.enums.EstadoTurno.ABIERTO")
    Optional<TurnoCaja> abiertoDe(@Param("cajeroId") Long cajeroId);

    /**
     * @param cajaId caja
     * @param estado estado buscado
     * @return {@code true} si la caja tiene un turno en ese estado
     */
    boolean existsByCajaIdAndEstado(Long cajaId, EstadoTurno estado);

    /**
     * @param id turno
     * @return el turno bloqueado para cerrarlo o aprobarlo sin carreras
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from TurnoCaja t join fetch t.caja where t.id = :id")
    Optional<TurnoCaja> bloquear(@Param("id") Long id);

    /**
     * Turno abierto del cajero, bloqueado en la misma lectura. Debe ser la primera lectura de la transacción: en MySQL
     * (lectura repetible) la foto se toma en la primera lectura normal, así que lo que se lea después de esperar el
     * bloqueo ya incluye lo que otra petición del mismo cajero confirmó mientras tanto (por ejemplo, un reenvío de la
     * misma venta). Si el turno se cerró durante la espera, no lo devuelve.
     *
     * @param cajeroId cajero
     * @return el turno abierto, bloqueado
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from TurnoCaja t join fetch t.caja "
            + "where t.cajeroUsuarioId = :cajeroId and t.estado = com.proyecto.supply_core.caja.enums.EstadoTurno.ABIERTO")
    Optional<TurnoCaja> abiertoDeBloqueado(@Param("cajeroId") Long cajeroId);

    /**
     * @param id turno
     * @return el turno con su caja
     */
    @Query("select t from TurnoCaja t join fetch t.caja c join fetch c.sede join fetch c.bodega where t.id = :id")
    Optional<TurnoCaja> conCaja(@Param("id") Long id);

    /**
     * @param estado     filtro por estado ({@code null} = todos)
     * @param pendientes solo los que esperan aprobación
     * @param cajaId     filtro por caja ({@code null} = todas)
     * @param pageable   página
     * @return turnos, los más recientes primero
     */
    @Query(value = """
            select t from TurnoCaja t join fetch t.caja c
            where (:estado is null or t.estado = :estado)
              and (:pendientes = false or (t.requiereAprobacion = true
                   and t.estado = com.proyecto.supply_core.caja.enums.EstadoTurno.CERRADO))
              and (:cajaId is null or c.id = :cajaId)
            order by t.abiertoEn desc
            """, countQuery = """
            select count(t) from TurnoCaja t
            where (:estado is null or t.estado = :estado)
              and (:pendientes = false or (t.requiereAprobacion = true
                   and t.estado = com.proyecto.supply_core.caja.enums.EstadoTurno.CERRADO))
              and (:cajaId is null or t.caja.id = :cajaId)
            """)
    Page<TurnoCaja> buscar(@Param("estado") EstadoTurno estado, @Param("pendientes") boolean pendientes,
            @Param("cajaId") Long cajaId, Pageable pageable);
}
