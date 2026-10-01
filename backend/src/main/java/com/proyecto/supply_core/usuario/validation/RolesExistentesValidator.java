package com.proyecto.supply_core.usuario.validation;

import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.hibernate.validator.constraintvalidation.HibernateConstraintValidatorContext;

import com.proyecto.supply_core.usuario.entity.Rol;
import com.proyecto.supply_core.usuario.repository.RolRepository;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/** Implementa {@link RolesExistentes} con una sola consulta a la BD. */
public class RolesExistentesValidator implements ConstraintValidator<RolesExistentes, Set<String>> {

    private final RolRepository rolRepository;

    /**
     * @param rolRepository acceso a roles
     */
    public RolesExistentesValidator(RolRepository rolRepository) {
        this.rolRepository = rolRepository;
    }

    /**
     * Comprueba que cada código exista y, si no, informa cuáles faltan.
     *
     * @param roles   códigos recibidos
     * @param context contexto de validación (se le agrega el parámetro {@code inexistentes})
     * @return {@code true} si todos existen o la lista está vacía (lo vacío lo reporta {@code @NotEmpty})
     */
    @Override
    public boolean isValid(Set<String> roles, ConstraintValidatorContext context) {
        if (roles == null || roles.isEmpty()) {
            return true;
        }
        Set<String> pedidos = roles.stream().filter(Objects::nonNull).map(CodigosRol::normalizar)
                .filter(c -> !c.isEmpty()).collect(Collectors.toCollection(TreeSet::new));
        if (pedidos.isEmpty()) {
            return true; // los elementos vacíos los reporta @NotBlank
        }
        Set<String> existentes = rolRepository.findByCodigoIn(pedidos).stream().map(Rol::getCodigo)
                .collect(Collectors.toSet());
        pedidos.removeAll(existentes);
        if (pedidos.isEmpty()) {
            return true;
        }
        context.unwrap(HibernateConstraintValidatorContext.class)
                .addMessageParameter("inexistentes", String.join(", ", pedidos));
        return false;
    }
}
