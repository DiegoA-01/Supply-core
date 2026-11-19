package com.proyecto.supply_core.venta.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.proyecto.supply_core.venta.entity.ListaPrecio;

/** Listas de precios de la empresa ({@code @TenantId}). */
public interface ListaPrecioRepository extends JpaRepository<ListaPrecio, Long> {

    /**
     * @return listas de la empresa: primero la General, luego por nombre
     */
    List<ListaPrecio> findAllByOrderByEsGeneralDescNombreAsc();

    /**
     * @param nombre nombre
     * @param id     lista que se edita (o -1 al crear)
     * @return {@code true} si otra lista de la empresa ya usa el nombre
     */
    boolean existsByNombreIgnoreCaseAndIdNot(String nombre, Long id);
}
