package com.proyecto.supply_core.security.context;

import java.util.Optional;

import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

import com.proyecto.supply_core.common.exception.NoAutenticadoException;

/**
 * Acceso al usuario de la petición actual desde cualquier capa:
 * {@code SesionActual.usuario().id()}. Lo llena el {@code AuthFilter}.
 */
public final class SesionActual {

    /** Nombre del atributo de request donde el filtro guarda el {@link UsuarioAutenticado}. */
    public static final String ATRIBUTO = "supplycore.usuario";

    /** Clase utilitaria: no se instancia. */
    private SesionActual() {
    }

    /**
     * Devuelve el usuario autenticado.
     *
     * @return usuario de la petición
     * @throws NoAutenticadoException si la petición no tiene sesión
     */
    public static UsuarioAutenticado usuario() {
        return opcional().orElseThrow(() -> new NoAutenticadoException("Debe iniciar sesión"));
    }

    /**
     * Devuelve el usuario autenticado si existe (útil fuera de una petición web, p. ej. jobs).
     *
     * @return usuario o vacío
     */
    public static Optional<UsuarioAutenticado> opcional() {
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(
                (UsuarioAutenticado) attrs.getAttribute(ATRIBUTO, RequestAttributes.SCOPE_REQUEST));
    }
}
