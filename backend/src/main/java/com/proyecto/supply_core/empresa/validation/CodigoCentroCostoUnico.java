package com.proyecto.supply_core.empresa.validation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * El código no puede estar usado por otro centro de costo (sin distinguir mayúsculas).
 * Solo para altas: en la edición el Service excluye al propio centro.
 */
@Documented
@Constraint(validatedBy = CodigoCentroCostoUnicoValidator.class)
@Target({ ElementType.FIELD, ElementType.RECORD_COMPONENT, ElementType.PARAMETER })
@Retention(RetentionPolicy.RUNTIME)
public @interface CodigoCentroCostoUnico {

    /**
     * Mensaje de error.
     *
     * @return texto que acompaña al nombre del campo
     */
    String message() default "ya está registrado por otro centro de costo";

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
