package com.proyecto.supply_core.inventario.repository;

import java.util.ArrayList;
import java.util.List;

import org.springframework.data.jpa.domain.Specification;

import com.proyecto.supply_core.inventario.dto.MovimientoFiltro;
import com.proyecto.supply_core.inventario.entity.MovimientoInventario;

import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;

/** Construye la consulta del kardex a partir de los filtros opcionales. */
public final class MovimientoSpecs {

    /** Clase utilitaria: no se instancia. */
    private MovimientoSpecs() {
    }

    /**
     * Combina con AND los filtros presentes y carga producto y bodegas en la misma consulta.
     *
     * @param f filtros ya validados
     * @return especificación para {@code findAll(spec, pageable)}
     */
    public static Specification<MovimientoInventario> conFiltro(MovimientoFiltro f) {
        return (root, query, cb) -> {
            // fetch solo en la consulta de datos (la de conteo no admite fetch)
            if (query != null && query.getResultType() != Long.class && query.getResultType() != long.class) {
                root.fetch("producto", JoinType.INNER);
                root.fetch("bodegaOrigen", JoinType.LEFT);
                root.fetch("bodegaDestino", JoinType.LEFT);
            }
            List<Predicate> c = new ArrayList<>();
            if (f.productoId() != null) {
                c.add(cb.equal(root.get("producto").get("id"), f.productoId()));
            }
            if (f.bodegaId() != null) {
                c.add(cb.or(cb.equal(root.get("bodegaOrigen").get("id"), f.bodegaId()),
                        cb.equal(root.get("bodegaDestino").get("id"), f.bodegaId())));
            }
            if (f.tipo() != null) {
                c.add(cb.equal(root.get("tipo"), f.tipo()));
            }
            if (f.desde() != null) {
                c.add(cb.greaterThanOrEqualTo(root.get("creadoEn"), f.desde()));
            }
            if (f.hasta() != null) {
                c.add(cb.lessThanOrEqualTo(root.get("creadoEn"), f.hasta()));
            }
            return cb.and(c.toArray(Predicate[]::new));
        };
    }
}
