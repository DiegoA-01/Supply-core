package com.proyecto.supply_core.usuario.validation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Set;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * Regla entre campos: rol PROVEEDOR ⇔ {@code proveedorId}, y un PROVEEDOR no tiene roles internos.
 * Va sobre la clase (record) porque mira {@code roles} y {@code proveedorId} a la vez.
 */
@Documented
@Constraint(validatedBy = ProveedorConsistenteValidator.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ProveedorConsistente {

    /**
     * Mensaje genérico (el validador reporta mensajes específicos por campo).
     *
     * @return texto del error
     */
    String message() default "combinación inválida de rol PROVEEDOR y proveedorId";

    /**
     * Grupos de validación (estándar de Bean Validation).
     *
     * @return grupos
     */
    Class<?>[] groups() default {};

    /**
     * Metadatos adicionales (estándar de Bean Validation).
     *
     * @return payload
     */
    Class<? extends Payload>[] payload() default {};

    /** Contrato que implementan los requests que llevan roles + proveedorId. */
    interface ConRolesYProveedor {

        /**
         * Códigos de rol pedidos.
         *
         * @return roles
         */
        Set<String> roles();

        /**
         * Proveedor asociado.
         *
         * @return id del proveedor o {@code null}
         */
        Long proveedorId();
    }
}
