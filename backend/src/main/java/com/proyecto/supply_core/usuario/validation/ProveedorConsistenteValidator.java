package com.proyecto.supply_core.usuario.validation;

import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import com.proyecto.supply_core.security.context.Roles;
import com.proyecto.supply_core.usuario.validation.ProveedorConsistente.ConRolesYProveedor;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/** Implementa {@link ProveedorConsistente}. */
public class ProveedorConsistenteValidator
        implements ConstraintValidator<ProveedorConsistente, ConRolesYProveedor> {

    /**
     * Aplica las tres reglas del usuario PROVEEDOR y reporta el error sobre el campo concreto.
     *
     * @param req     request a validar
     * @param context contexto de validación
     * @return {@code true} si la combinación es válida
     */
    @Override
    public boolean isValid(ConRolesYProveedor req, ConstraintValidatorContext context) {
        if (req == null || req.roles() == null) {
            return true;
        }
        Set<String> roles = req.roles().stream().filter(Objects::nonNull).map(CodigosRol::normalizar)
                .collect(Collectors.toSet());
        boolean esProveedor = roles.contains(Roles.PROVEEDOR);

        if (esProveedor && req.proveedorId() == null) {
            return error(context, "proveedorId", "es obligatorio para usuarios con rol PROVEEDOR");
        }
        if (esProveedor && roles.size() > 1) {
            return error(context, "roles", "un usuario PROVEEDOR no puede tener roles internos");
        }
        if (!esProveedor && req.proveedorId() != null) {
            return error(context, "proveedorId", "solo aplica a usuarios con rol PROVEEDOR");
        }
        return true;
    }

    /**
     * Reemplaza el mensaje genérico por uno sobre el campo, para que salga como "campo: mensaje".
     *
     * @param context contexto de validación
     * @param campo   nombre del campo afectado
     * @param mensaje texto del error
     * @return siempre {@code false}
     */
    private static boolean error(ConstraintValidatorContext context, String campo, String mensaje) {
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(mensaje).addPropertyNode(campo).addConstraintViolation();
        return false;
    }
}
