package com.proyecto.supply_core.plataforma.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.proyecto.supply_core.plataforma.entity.EmpresaModulo;

/** Acceso a la tabla {@code empresa_modulo}. */
public interface EmpresaModuloRepository extends JpaRepository<EmpresaModulo, Long> {

    /**
     * Excepciones de módulo de una empresa.
     *
     * @param empresaId empresa
     * @return excepciones
     */
    List<EmpresaModulo> findByEmpresaId(Long empresaId);

    /**
     * Excepción de una empresa para un módulo.
     *
     * @param empresaId    empresa
     * @param moduloCodigo módulo
     * @return excepción, si existe
     */
    Optional<EmpresaModulo> findByEmpresaIdAndModuloCodigo(Long empresaId, String moduloCodigo);
}
