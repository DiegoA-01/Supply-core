package com.proyecto.supply_core.empresa.validation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/** El id debe corresponder a una sede existente y activa ({@code null} se acepta; usar {@code @NotNull} si es obligatorio). */
@Documented
@Constraint(validatedBy = SedeActivaValidator.class)
@Target({ ElementType.FIELD, ElementType.RECORD_COMPONENT, ElementType.PARAMETER })
@Retention(RetentionPolicy.RUNTIME)
public @interface SedeActiva {

    /**
     * Mensaje de error.
     *
     * @return texto que acompaña al nombre del campo
     */
    String message() default "no existe o está inactiva";

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
