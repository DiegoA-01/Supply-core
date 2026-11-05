package com.proyecto.supply_core.security.context;

import java.util.Optional;

import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

import com.proyecto.supply_core.common.exception.NoAutenticadoException;

/**
 * Acceso al usuario de la plataforma de la petición actual: {@code PlataformaActual.usuario().id()}. Lo llena el
 * {@code AuthFilter} en las rutas {@code /api/plataforma/**}. Es independiente de {@link SesionActual}.
 */
public final class PlataformaActual {

    /** Nombre del atributo de request donde el filtro guarda el {@link PlataformaAutenticada}. */
    public static final String ATRIBUTO = "supplycore.plataforma";

    /** Clase utilitaria: no se instancia. */
    private PlataformaActual() {
    }

    /**
     * Devuelve el usuario de la plataforma autenticado.
     *
     * @return usuario de la petición
     * @throws NoAutenticadoException si la petición no tiene sesión de plataforma
     */
    public static PlataformaAutenticada usuario() {
        return opcional().orElseThrow(() -> new NoAutenticadoException("Debe iniciar sesión"));
    }

    /**
     * Devuelve el usuario de la plataforma si existe.
     *
     * @return usuario o vacío
     */
    public static Optional<PlataformaAutenticada> opcional() {
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(
                (PlataformaAutenticada) attrs.getAttribute(ATRIBUTO, RequestAttributes.SCOPE_REQUEST));
    }
}
