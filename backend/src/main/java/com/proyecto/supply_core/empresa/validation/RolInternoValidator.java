package com.proyecto.supply_core.empresa.validation;

import com.proyecto.supply_core.security.context.Roles;
import com.proyecto.supply_core.usuario.repository.RolRepository;
import com.proyecto.supply_core.usuario.validation.CodigosRol;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/** Implementa {@link RolInterno}. Spring lo crea e inyecta el repositorio. */
public class RolInternoValidator implements ConstraintValidator<RolInterno, String> {

    private final RolRepository rolRepository;

    /**
     * @param rolRepository acceso a roles
     */
    public RolInternoValidator(RolRepository rolRepository) {
        this.rolRepository = rolRepository;
    }

    /**
     * Comprueba que el rol exista y no sea PROVEEDOR.
     *
     * @param codigo  código recibido (cualquier mayúscula/espacio)
     * @param context contexto de validación
     * @return {@code true} si es un rol interno o está vacío (el vacío lo reporta {@code @NotBlank})
     */
    @Override
    public boolean isValid(String codigo, ConstraintValidatorContext context) {
        if (codigo == null || codigo.isBlank()) {
            return true;
        }
        String normalizado = CodigosRol.normalizar(codigo);
        return !Roles.PROVEEDOR.equals(normalizado) && rolRepository.findByCodigo(normalizado).isPresent();
    }
}
