package com.proyecto.supply_core.catalogo.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.proyecto.supply_core.catalogo.entity.UnidadMedida;
import com.proyecto.supply_core.catalogo.enums.Magnitud;

/** Acceso a la tabla {@code unidad_medida}. */
public interface UnidadMedidaRepository extends JpaRepository<UnidadMedida, Long> {

    /**
     * Todas las unidades ordenadas por magnitud y código.
     *
     * @return unidades activas e inactivas
     */
    List<UnidadMedida> findAllByOrderByMagnitudAscCodigoAsc();

    /**
     * Unidades filtradas por estado.
     *
     * @param activo estado
     * @return unidades ordenadas por magnitud y código
     */
    List<UnidadMedida> findByActivoOrderByMagnitudAscCodigoAsc(boolean activo);

    /**
     * Unidades de una magnitud (p. ej. para elegir a qué convertir).
     *
     * @param magnitud magnitud
     * @return unidades ordenadas por código
     */
    List<UnidadMedida> findByMagnitudOrderByCodigoAsc(Magnitud magnitud);

    /**
     * Indica si el código ya está registrado.
     *
     * @param codigo código en mayúsculas
     * @return {@code true} si existe
     */
    boolean existsByCodigoIgnoreCase(String codigo);

    /**
     * Indica si el registro existe y está activo (validaciones de requests).
     *
     * @param id id a verificar
     * @return {@code true} si existe y está activo
     */
    boolean existsByIdAndActivoTrue(Long id);
}
