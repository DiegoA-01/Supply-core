package com.proyecto.supply_core.security.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Exige que el módulo esté activo para la empresa del usuario (lo decide el dueño de la plataforma). Se suma a
 * {@link RequiereRol}, que decide el administrador de la empresa: para entrar hay que cumplir las dos. Con el módulo
 * apagado responde 403 {@code MODULO_NO_CONTRATADO}.
 * <p>Uso: {@code @RequiereModulo(Modulos.COMPRAS)}. La del método reemplaza a la de la clase.</p>
 */
@Documented
@Target({ ElementType.METHOD, ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiereModulo {

    /**
     * Módulo exigido (constante de {@code Modulos}).
     *
     * @return código del módulo
     */
    String value();
}
