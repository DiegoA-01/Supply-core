package com.proyecto.supply_core.empresa.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.proyecto.supply_core.empresa.entity.Sede;

/** Acceso a la tabla {@code sede}. */
public interface SedeRepository extends JpaRepository<Sede, Long> {

    /**
     * Todas las sedes de la empresa ordenadas por nombre.
     *
     * @param empresaId empresa
     * @return sedes activas e inactivas
     */
    List<Sede> findByEmpresaIdOrderByNombreAsc(Long empresaId);

    /**
     * Sedes de la empresa filtradas por estado.
     *
     * @param empresaId empresa
     * @param activo    estado
     * @return sedes ordenadas por nombre
     */
    List<Sede> findByEmpresaIdAndActivoOrderByNombreAsc(Long empresaId, boolean activo);

    /**
     * Indica si ya existe una sede con ese nombre (sin distinguir mayúsculas).
     *
     * @param empresaId empresa
     * @param nombre    nombre a verificar
     * @return {@code true} si existe
     */
    boolean existsByEmpresaIdAndNombreIgnoreCase(Long empresaId, String nombre);

    /**
     * Igual que {@link #existsByEmpresaIdAndNombreIgnoreCase} pero excluyendo una sede (ediciones).
     *
     * @param empresaId empresa
     * @param nombre    nombre a verificar
     * @param id        sede que se está editando
     * @return {@code true} si otra sede lo tiene
     */
    boolean existsByEmpresaIdAndNombreIgnoreCaseAndIdNot(Long empresaId, String nombre, Long id);

    /**
     * Indica si el registro existe y está activo (validaciones de requests).
     *
     * @param id id a verificar
     * @return {@code true} si existe y está activo
     */
    boolean existsByIdAndActivoTrue(Long id);
}
