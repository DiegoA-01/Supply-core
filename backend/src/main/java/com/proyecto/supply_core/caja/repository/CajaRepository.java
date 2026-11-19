package com.proyecto.supply_core.caja.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.proyecto.supply_core.caja.entity.Caja;

/** Cajas de la empresa ({@code @TenantId}). */
public interface CajaRepository extends JpaRepository<Caja, Long> {

    /**
     * @param activa filtro por estado ({@code null} = todas)
     * @return cajas con su sede y su bodega, por nombre
     */
    @Query("""
            select c from Caja c join fetch c.sede join fetch c.bodega
            where (:activa is null or c.activa = :activa) order by c.nombre
            """)
    List<Caja> listar(@Param("activa") Boolean activa);

    /**
     * @param id caja
     * @return la caja con su sede y su bodega
     */
    @Query("select c from Caja c join fetch c.sede join fetch c.bodega where c.id = :id")
    Optional<Caja> conDetalle(@Param("id") Long id);

    /**
     * @param nombre nombre
     * @param id     caja que se edita (o -1 al crear)
     * @return {@code true} si otra caja de la empresa ya usa ese nombre
     */
    boolean existsByNombreIgnoreCaseAndIdNot(String nombre, Long id);

    /**
     * @param prefijo prefijo de consecutivo
     * @param id      caja que se edita (o -1 al crear)
     * @return {@code true} si otra caja de la empresa ya usa ese prefijo
     */
    boolean existsByPrefijoConsecutivoIgnoreCaseAndIdNot(String prefijo, Long id);
}
