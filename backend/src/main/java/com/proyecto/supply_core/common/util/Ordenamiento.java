package com.proyecto.supply_core.common.util;

import java.util.Set;
import java.util.TreeSet;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import com.proyecto.supply_core.common.exception.ParametroInvalidoException;

/**
 * Lista blanca de campos ordenables. Necesaria en consultas {@code @Query}, donde el {@code sort}
 * llega directo al JPQL: sin esto un campo inexistente da 500 y se podría ordenar por datos
 * sensibles (p. ej. {@code hashPassword}).
 */
public final class Ordenamiento {

    /** Clase utilitaria: no se instancia. */
    private Ordenamiento() {
    }

    /**
     * Verifica que cada campo del {@code sort} esté permitido.
     *
     * @param pageable   paginación recibida
     * @param permitidos nombres de atributos de la entidad que se pueden usar
     * @return el mismo {@code pageable} si es válido
     * @throws ParametroInvalidoException si se pide ordenar por un campo no permitido
     */
    public static Pageable validar(Pageable pageable, Set<String> permitidos) {
        for (Sort.Order orden : pageable.getSort()) {
            if (!permitidos.contains(orden.getProperty())) {
                throw new ParametroInvalidoException("No se puede ordenar por '" + orden.getProperty()
                        + "'. Campos permitidos: " + new TreeSet<>(permitidos));
            }
        }
        return pageable;
    }
}
