package com.proyecto.supply_core.inventario.validation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * Reglas del movimiento manual según su tipo (UC-03):
 * <ul>
 * <li>Solo tipos manuales (salida, ajustes, traslado); entrada por compra y devolución vienen de la recepción.</li>
 * <li>Salida y ajuste negativo: solo bodega de origen. Ajuste positivo: solo destino.
 * Traslado: origen y destino distintos.</li>
 * <li>Ajustes: motivo obligatorio.</li>
 * </ul>
 */
@Documented
@Constraint(validatedBy = MovimientoConsistenteValidator.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface MovimientoConsistente {

    /**
     * Mensaje genérico (el validador reporta mensajes específicos por campo).
     *
     * @return texto del error
     */
    String message() default "el movimiento no es consistente con su tipo";

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
