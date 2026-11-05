package com.proyecto.supply_core.plataforma.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.proyecto.supply_core.plataforma.entity.UsuarioPlataforma;

/** Acceso a la tabla {@code usuario_plataforma}. */
public interface UsuarioPlataformaRepository extends JpaRepository<UsuarioPlataforma, Long> {

    /**
     * Busca por correo sin distinguir mayúsculas.
     *
     * @param correo correo de login
     * @return usuario, si existe
     */
    Optional<UsuarioPlataforma> findByCorreoIgnoreCase(String correo);
}
