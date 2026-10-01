package com.proyecto.supply_core.security.interceptor;

import java.lang.annotation.Annotation;

import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import com.proyecto.supply_core.common.exception.AccesoDenegadoException;
import com.proyecto.supply_core.security.annotation.RequiereRol;
import com.proyecto.supply_core.security.annotation.UsuarioInterno;
import com.proyecto.supply_core.security.context.SesionActual;
import com.proyecto.supply_core.security.context.UsuarioAutenticado;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Aplica {@link RequiereRol} y {@link UsuarioInterno}.
 * <p>Para cada anotación, la del método tiene prioridad sobre la de la clase. Si hay ambas,
 * deben cumplirse las dos.</p>
 */
@Component
public class RolInterceptor implements HandlerInterceptor {

    /**
     * Verifica los permisos antes de ejecutar el controller.
     *
     * @param request  petición
     * @param response respuesta
     * @param handler  método del controller que atenderá la petición
     * @return {@code true} para continuar
     * @throws AccesoDenegadoException si el usuario no cumple alguna de las reglas
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod metodo)) {
            return true; // recursos estáticos, etc.
        }
        RequiereRol regla = buscar(metodo, RequiereRol.class);
        boolean soloInternos = buscar(metodo, UsuarioInterno.class) != null;
        if (regla == null && !soloInternos) {
            return true; // basta con estar autenticado (lo garantiza el AuthFilter)
        }
        UsuarioAutenticado usuario = SesionActual.usuario();
        if (soloInternos && usuario.esProveedor()) {
            throw new AccesoDenegadoException("Esta información es solo para usuarios internos");
        }
        if (regla != null && !usuario.tieneAlgunRol(regla.value())) {
            throw new AccesoDenegadoException("No tiene permisos para esta operación");
        }
        return true;
    }

    /**
     * Busca la anotación primero en el método y luego en la clase del controller.
     *
     * @param metodo método del controller
     * @param tipo   anotación buscada
     * @param <A>    tipo de anotación
     * @return la anotación más específica o {@code null}
     */
    private static <A extends Annotation> A buscar(HandlerMethod metodo, Class<A> tipo) {
        A enMetodo = AnnotatedElementUtils.findMergedAnnotation(metodo.getMethod(), tipo);
        return enMetodo != null ? enMetodo : AnnotatedElementUtils.findMergedAnnotation(metodo.getBeanType(), tipo);
    }
}
