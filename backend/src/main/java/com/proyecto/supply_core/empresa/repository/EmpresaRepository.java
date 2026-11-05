package com.proyecto.supply_core.empresa.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.proyecto.supply_core.empresa.entity.Empresa;

/** Acceso a la tabla {@code empresa}. */
public interface EmpresaRepository extends JpaRepository<Empresa, Long> {

    /**
     * Indica si otra empresa ya usa el NIT.
     *
     * @param nit NIT a verificar
     * @param id  empresa que se está editando
     * @return {@code true} si otra empresa lo tiene
     */
    boolean existsByNitIgnoreCaseAndIdNot(String nit, Long id);

    /**
     * Indica si alguna empresa ya usa el NIT (alta de una empresa nueva).
     *
     * @param nit NIT a verificar
     * @return {@code true} si existe
     */
    boolean existsByNitIgnoreCase(String nit);

    /**
     * Resuelve el id de una empresa por su código público.
     *
     * @param codigo código público (p. ej. {@code demo})
     * @return id de la empresa, si existe
     */
    @Query("select e.id from Empresa e where lower(e.codigoPublico) = lower(:codigo)")
    Optional<Long> idPorCodigoPublico(@Param("codigo") String codigo);

    /**
     * Ids de todas las empresas (los jobs recorren cada una con su propio contexto).
     *
     * @return ids en orden ascendente
     */
    @Query("select e.id from Empresa e order by e.id")
    List<Long> todosLosIds();
}
