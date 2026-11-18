package com.proyecto.supply_core.ia.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.proyecto.supply_core.ia.entity.ExtraccionCampo;

/** Acceso a la tabla {@code extraccion_campo}. */
public interface ExtraccionCampoRepository extends JpaRepository<ExtraccionCampo, Long> {

    /**
     * Datos leídos en un procesamiento.
     *
     * @param procesamientoId procesamiento
     * @return campos en orden de creación
     */
    List<ExtraccionCampo> findByProcesamiento_IdOrderByIdAsc(Long procesamientoId);

    /**
     * Un campo solo si pertenece al procesamiento.
     *
     * @param id              campo
     * @param procesamientoId procesamiento
     * @return campo, si pertenece
     */
    Optional<ExtraccionCampo> findByIdAndProcesamiento_Id(Long id, Long procesamientoId);

    /**
     * Borra los campos de un procesamiento (antes de releer o digitar, porque apuntan a líneas que se reemplazan).
     *
     * @param procesamientoId procesamiento
     * @return cantidad borrada
     */
    @Modifying(flushAutomatically = true, clearAutomatically = false)
    @Query("delete from ExtraccionCampo c where c.procesamiento.id = :procesamientoId")
    int borrarDe(@Param("procesamientoId") Long procesamientoId);
}
