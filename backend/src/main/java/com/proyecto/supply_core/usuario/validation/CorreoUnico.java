package com.proyecto.supply_core.usuario.validation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * El correo no puede estar registrado por otro usuario (sin distinguir mayúsculas ni espacios).
 * Solo para altas: en la edición el Service excluye al propio usuario.
 */
@Documented
@Constraint(validatedBy = CorreoUnicoValidator.class)
@Target({ ElementType.FIELD, ElementType.RECORD_COMPONENT, ElementType.PARAMETER })
@Retention(RetentionPolicy.RUNTIME)
public @interface CorreoUnico {

    /**
     * Mensaje de error.
     *
     * @return texto que acompaña al nombre del campo
     */
    String message() default "ya está registrado por otro usuario";

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
