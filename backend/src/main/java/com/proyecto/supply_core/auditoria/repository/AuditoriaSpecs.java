package com.proyecto.supply_core.auditoria.repository;

import java.util.ArrayList;
import java.util.List;

import org.springframework.data.jpa.domain.Specification;

import com.proyecto.supply_core.auditoria.dto.AuditoriaFiltro;
import com.proyecto.supply_core.auditoria.entity.Auditoria;

import jakarta.persistence.criteria.Predicate;

/** Construye la consulta de auditoría a partir de los filtros opcionales. */
public final class AuditoriaSpecs {

    /** Clase utilitaria: no se instancia. */
    private AuditoriaSpecs() {
    }

    /**
     * Combina con AND los filtros presentes; los nulos se ignoran.
     *
     * @param f filtros ya validados
     * @return especificación para {@code findAll(spec, pageable)}
     */
    public static Specification<Auditoria> conFiltro(AuditoriaFiltro f) {
        return (root, query, cb) -> {
            List<Predicate> condiciones = new ArrayList<>();
            if (f.usuarioId() != null) {
                condiciones.add(cb.equal(root.get("usuarioId"), f.usuarioId()));
            }
            if (f.entidad() != null) {
                condiciones.add(cb.equal(root.get("entidad"), f.entidad().name()));
            }
            if (f.entidadId() != null) {
                condiciones.add(cb.equal(root.get("entidadId"), f.entidadId()));
            }
            if (f.accion() != null) {
                condiciones.add(cb.equal(root.get("accion"), f.accion().name()));
            }
            if (f.resultado() != null) {
                condiciones.add(cb.equal(root.get("resultado"), f.resultado()));
            }
            if (f.desde() != null) {
                condiciones.add(cb.greaterThanOrEqualTo(root.get("creadoEn"), f.desde()));
            }
            if (f.hasta() != null) {
                condiciones.add(cb.lessThanOrEqualTo(root.get("creadoEn"), f.hasta()));
            }
            return cb.and(condiciones.toArray(Predicate[]::new));
        };
    }
}
