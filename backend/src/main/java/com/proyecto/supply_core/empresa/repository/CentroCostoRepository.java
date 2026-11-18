package com.proyecto.supply_core.empresa.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.proyecto.supply_core.empresa.entity.CentroCosto;

/** Acceso a la tabla {@code centro_costo}. */
public interface CentroCostoRepository extends JpaRepository<CentroCosto, Long> {

    /**
     * Todos los centros de costo de la empresa ordenados por código.
     *
     * @param empresaId empresa
     * @return centros activos e inactivos
     */
    List<CentroCosto> findByEmpresaIdOrderByCodigoAsc(Long empresaId);

    /**
     * Centros de costo filtrados por estado.
     *
     * @param empresaId empresa
     * @param activo    estado
     * @return centros ordenados por código
     */
    List<CentroCosto> findByEmpresaIdAndActivoOrderByCodigoAsc(Long empresaId, boolean activo);

    /**
     * Indica si el código ya está registrado.
     *
     * @param codigo código en mayúsculas
     * @return {@code true} si existe
     */
    boolean existsByCodigoIgnoreCase(String codigo);

    /**
     * Indica si otro centro de costo usa el código (ediciones).
     *
     * @param codigo código en mayúsculas
     * @param id     centro que se está editando
     * @return {@code true} si otro lo tiene
     */
    boolean existsByCodigoIgnoreCaseAndIdNot(String codigo, Long id);

    /**
     * Indica si el centro existe y está activo (validación de usuarios y solicitudes).
     *
     * @param id id del centro
     * @return {@code true} si existe y está activo
     */
    boolean existsByIdAndActivoTrue(Long id);
}
