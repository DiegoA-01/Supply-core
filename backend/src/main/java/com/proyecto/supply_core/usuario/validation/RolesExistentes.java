package com.proyecto.supply_core.usuario.validation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/** Todos los códigos de rol deben existir en la tabla {@code rol} (sin distinguir mayúsculas). */
@Documented
@Constraint(validatedBy = RolesExistentesValidator.class)
@Target({ ElementType.FIELD, ElementType.RECORD_COMPONENT, ElementType.PARAMETER })
@Retention(RetentionPolicy.RUNTIME)
public @interface RolesExistentes {

    /**
     * Mensaje de error; {@code {inexistentes}} se reemplaza por los códigos que no existen.
     *
     * @return plantilla del mensaje
     */
    String message() default "contiene roles inexistentes: {inexistentes}";

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
