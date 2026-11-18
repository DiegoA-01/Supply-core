package com.proyecto.supply_core.security.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Restringe un endpoint (o todo un controller) a los roles indicados; basta con tener uno.
 * <p>Uso: {@code @RequiereRol({Roles.COMPRADOR, Roles.APROBADOR})}.
 * La anotación del método reemplaza a la de la clase. Sin anotación, el endpoint solo exige
 * estar autenticado.</p>
 */
@Documented
@Target({ ElementType.METHOD, ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiereRol {

    /**
     * Roles aceptados (constantes de {@code Roles}).
     *
     * @return códigos de rol
     */
    String[] value();
}
