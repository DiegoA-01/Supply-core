package com.proyecto.supply_core.security.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Restringe un endpoint (o todo un controller) a usuarios internos de la empresa: cualquier rol
 * menos PROVEEDOR. Se combina con {@link RequiereRol}: si ambos están presentes, deben cumplirse los dos.
 * <p>Uso típico: el controller lleva {@code @UsuarioInterno} (todos los internos consultan) y los
 * métodos de escritura llevan además {@code @RequiereRol(Roles.ADMIN_SISTEMA)}.</p>
 */
@Documented
@Target({ ElementType.METHOD, ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
public @interface UsuarioInterno {
}
