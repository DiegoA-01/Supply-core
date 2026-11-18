package com.proyecto.supply_core.proveedor.validation;

import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.hibernate.validator.constraintvalidation.HibernateConstraintValidatorContext;

import com.proyecto.supply_core.catalogo.entity.CategoriaProducto;
import com.proyecto.supply_core.catalogo.repository.CategoriaProductoRepository;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/** Implementa {@link CategoriasActivas} con una sola consulta a la BD. */
public class CategoriasActivasValidator implements ConstraintValidator<CategoriasActivas, Set<Long>> {

    private final CategoriaProductoRepository categoriaRepository;

    /**
     * @param categoriaRepository acceso a categorías de producto
     */
    public CategoriasActivasValidator(CategoriaProductoRepository categoriaRepository) {
        this.categoriaRepository = categoriaRepository;
    }

    /**
     * Comprueba que cada id exista y esté activo; si no, informa cuáles fallan.
     *
     * @param ids     ids recibidos
     * @param context contexto de validación (se le agrega el parámetro {@code invalidas})
     * @return {@code true} si todos sirven o la lista está vacía
     */
    @Override
    public boolean isValid(Set<Long> ids, ConstraintValidatorContext context) {
        if (ids == null || ids.isEmpty()) {
            return true;
        }
        Set<Long> pedidos = ids.stream().filter(Objects::nonNull).collect(Collectors.toCollection(TreeSet::new));
        if (pedidos.isEmpty()) {
            return true;
        }
        Set<Long> validas = categoriaRepository.findByIdInAndActivoTrue(pedidos).stream()
                .map(CategoriaProducto::getId).collect(Collectors.toSet());
        pedidos.removeAll(validas);
        if (pedidos.isEmpty()) {
            return true;
        }
        context.unwrap(HibernateConstraintValidatorContext.class)
                .addMessageParameter("invalidas", pedidos.toString());
        return false;
    }
}
