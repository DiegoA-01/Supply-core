package com.proyecto.supply_core.empresa.repository;

import org.springframework.data.jpa.repository.JpaRepository;

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
}
