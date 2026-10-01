package com.proyecto.supply_core.inventario.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.proyecto.supply_core.inventario.entity.Bodega;

/** Acceso a la tabla {@code bodega}. Las consultas traen la sede. */
public interface BodegaRepository extends JpaRepository<Bodega, Long> {

    /**
     * Todas las bodegas de la empresa por nombre.
     *
     * @param empresaId empresa
     * @return bodegas activas e inactivas
     */
    @EntityGraph(attributePaths = "sede")
    List<Bodega> findByEmpresaIdOrderByNombreAsc(Long empresaId);

    /**
     * Bodegas filtradas por estado.
     *
     * @param empresaId empresa
     * @param activo    estado
     * @return bodegas por nombre
     */
    @EntityGraph(attributePaths = "sede")
    List<Bodega> findByEmpresaIdAndActivoOrderByNombreAsc(Long empresaId, boolean activo);

    /**
     * Busca por id trayendo la sede.
     *
     * @param id id de la bodega
     * @return bodega, si existe
     */
    @EntityGraph(attributePaths = "sede")
    Optional<Bodega> findWithSedeById(Long id);

    /**
     * Indica si ya existe una bodega con ese nombre (sin distinguir mayúsculas).
     *
     * @param empresaId empresa
     * @param nombre    nombre a verificar
     * @return {@code true} si existe
     */
    boolean existsByEmpresaIdAndNombreIgnoreCase(Long empresaId, String nombre);

    /**
     * Igual que {@link #existsByEmpresaIdAndNombreIgnoreCase} excluyendo una bodega (ediciones).
     *
     * @param empresaId empresa
     * @param nombre    nombre a verificar
     * @param id        bodega que se está editando
     * @return {@code true} si otra lo tiene
     */
    boolean existsByEmpresaIdAndNombreIgnoreCaseAndIdNot(Long empresaId, String nombre, Long id);

    /**
     * Indica si una sede tiene bodegas activas (para no desactivar la sede).
     *
     * @param sedeId sede
     * @return {@code true} si tiene al menos una bodega activa
     */
    boolean existsBySedeIdAndActivoTrue(Long sedeId);
}
