package com.proyecto.supply_core.inventario.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.proyecto.supply_core.inventario.entity.MovimientoInventario;

/** Acceso a la tabla {@code movimiento_inventario} (kardex). Filtros con {@code MovimientoSpecs}. */
public interface MovimientoInventarioRepository
        extends JpaRepository<MovimientoInventario, Long>, JpaSpecificationExecutor<MovimientoInventario> {

    /**
     * Indica si un producto ya tiene movimientos (para no cambiarle la unidad).
     *
     * @param productoId producto
     * @return {@code true} si tiene al menos uno
     */
    boolean existsByProductoId(Long productoId);
}
