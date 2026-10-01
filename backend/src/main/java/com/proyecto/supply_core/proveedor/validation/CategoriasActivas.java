package com.proyecto.supply_core.proveedor.validation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/** Todos los ids deben corresponder a categorías de producto existentes y activas. */
@Documented
@Constraint(validatedBy = CategoriasActivasValidator.class)
@Target({ ElementType.FIELD, ElementType.RECORD_COMPONENT, ElementType.PARAMETER })
@Retention(RetentionPolicy.RUNTIME)
public @interface CategoriasActivas {

    /**
     * Mensaje de error; {@code {invalidas}} se reemplaza por los ids que no sirven.
     *
     * @return plantilla del mensaje
     */
    String message() default "contiene categorías inexistentes o inactivas: {invalidas}";

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
}
