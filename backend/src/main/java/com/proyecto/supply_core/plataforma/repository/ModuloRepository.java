package com.proyecto.supply_core.plataforma.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.proyecto.supply_core.plataforma.entity.Modulo;

/** Acceso a la tabla {@code modulo}. */
public interface ModuloRepository extends JpaRepository<Modulo, String> {

    /**
     * Todos los módulos por código.
     *
     * @return catálogo de módulos
     */
    List<Modulo> findAllByOrderByCodigoAsc();
}
