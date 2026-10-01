package com.proyecto.supply_core.catalogo.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.proyecto.supply_core.catalogo.entity.CategoriaProducto;

/** Acceso a la tabla {@code categoria_producto}. */
public interface CategoriaProductoRepository extends JpaRepository<CategoriaProducto, Long> {

    /**
     * Categorías activas entre los ids indicados.
     *
     * @param ids ids buscados
     * @return las que existen y están activas
     */
    List<CategoriaProducto> findByIdInAndActivoTrue(Collection<Long> ids);

    /**
     * Todas las categorías activas, ordenadas por nombre.
     *
     * @return categorías activas
     */
    List<CategoriaProducto> findByActivoTrueOrderByNombreAsc();

    /**
     * Todas las categorías ordenadas por nombre.
     *
     * @return categorías activas e inactivas
     */
    List<CategoriaProducto> findAllByOrderByNombreAsc();

    /**
     * Categorías filtradas por estado.
     *
     * @param activo estado
     * @return categorías ordenadas por nombre
     */
    List<CategoriaProducto> findByActivoOrderByNombreAsc(boolean activo);

    /**
     * Indica si ya existe una categoría con ese nombre (sin distinguir mayúsculas).
     *
     * @param nombre nombre a verificar
     * @return {@code true} si existe
     */
    boolean existsByNombreIgnoreCase(String nombre);

    /**
     * Igual que {@link #existsByNombreIgnoreCase} excluyendo una categoría (ediciones).
     *
     * @param nombre nombre a verificar
     * @param id     categoría que se está editando
     * @return {@code true} si otra la tiene
     */
    boolean existsByNombreIgnoreCaseAndIdNot(String nombre, Long id);

    /**
     * Indica si el registro existe y está activo (validaciones de requests).
     *
     * @param id id a verificar
     * @return {@code true} si existe y está activo
     */
    boolean existsByIdAndActivoTrue(Long id);
}
