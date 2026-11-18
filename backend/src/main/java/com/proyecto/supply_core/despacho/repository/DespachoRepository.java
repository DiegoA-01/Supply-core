package com.proyecto.supply_core.despacho.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.proyecto.supply_core.despacho.entity.Despacho;
import com.proyecto.supply_core.despacho.enums.EstadoDespacho;

import jakarta.persistence.LockModeType;

/** Acceso a la tabla {@code despacho}. */
public interface DespachoRepository extends JpaRepository<Despacho, Long> {

    /**
     * Busca por id trayendo bodega, líneas, productos (con su unidad) y unidades.
     *
     * @param id id del despacho
     * @return despacho completo, si existe
     */
    @EntityGraph(attributePaths = { "bodega", "lineas", "lineas.producto", "lineas.producto.unidadMedida",
            "lineas.unidadMedida" })
    Optional<Despacho> findWithDetalleById(Long id);

    /**
     * Bloquea la fila hasta el fin de la transacción (evita confirmar o anular dos veces a la vez).
     *
     * @param id id del despacho
     * @return despacho bloqueado, si existe
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from Despacho d where d.id = :id")
    Optional<Despacho> bloquear(@Param("id") Long id);

    /**
     * Búsqueda paginada con filtros opcionales ({@code null} = no filtrar).
     *
     * @param texto    coincidencia en consecutivo, cliente o referencia externa
     * @param estado   estado
     * @param bodegaId bodega
     * @param pageable página y orden
     * @return página de despachos
     */
    @Query("""
            select d from Despacho d
            where (:texto is null or lower(d.consecutivo) like lower(concat('%', :texto, '%'))
                                  or lower(d.cliente) like lower(concat('%', :texto, '%'))
                                  or lower(d.referenciaExterna) like lower(concat('%', :texto, '%')))
              and (:estado is null or d.estado = :estado)
              and (:bodegaId is null or d.bodega.id = :bodegaId)
            """)
    Page<Despacho> buscar(@Param("texto") String texto, @Param("estado") EstadoDespacho estado,
            @Param("bodegaId") Long bodegaId, Pageable pageable);
}
