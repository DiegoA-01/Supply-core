package com.proyecto.supply_core.usuario.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.proyecto.supply_core.usuario.entity.Rol;

/** Acceso a la tabla {@code rol} (solo lectura en la práctica: los roles se siembran en V2). */
public interface RolRepository extends JpaRepository<Rol, Long> {

    /**
     * Busca un rol por su código exacto.
     *
     * @param codigo código en mayúsculas
     * @return rol, si existe
     */
    Optional<Rol> findByCodigo(String codigo);

    /**
     * Busca varios roles por código.
     *
     * @param codigos códigos en mayúsculas
     * @return roles encontrados (los inexistentes simplemente no aparecen)
     */
    List<Rol> findByCodigoIn(Collection<String> codigos);

    /**
     * Lista todos los roles ordenados por nombre.
     *
     * @return roles
     */
    List<Rol> findAllByOrderByNombreAsc();
}
