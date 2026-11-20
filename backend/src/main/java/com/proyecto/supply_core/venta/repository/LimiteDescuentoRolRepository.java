package com.proyecto.supply_core.venta.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.proyecto.supply_core.venta.entity.LimiteDescuentoRol;

/** Límites de descuento por rol de la empresa del contexto ({@code @TenantId}). */
public interface LimiteDescuentoRolRepository extends JpaRepository<LimiteDescuentoRol, Long> {

    /**
     * @param rolCodigo código del rol
     * @return límite fijado por la empresa para ese rol, si lo fijó
     */
    Optional<LimiteDescuentoRol> findByRolCodigo(String rolCodigo);
}
