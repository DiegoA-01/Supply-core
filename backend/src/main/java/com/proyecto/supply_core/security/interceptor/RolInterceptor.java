package com.proyecto.supply_core.security.interceptor;

import java.lang.annotation.Annotation;

import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import com.proyecto.supply_core.common.exception.AccesoDenegadoException;
import com.proyecto.supply_core.plataforma.service.AccesoEmpresaService;
import com.proyecto.supply_core.security.annotation.RequiereModulo;
import com.proyecto.supply_core.security.annotation.RequiereRol;
import com.proyecto.supply_core.security.annotation.UsuarioInterno;
import com.proyecto.supply_core.security.context.SesionActual;
import com.proyecto.supply_core.security.context.UsuarioAutenticado;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Aplica el doble permiso de una empresa: {@link RequiereModulo} (lo decide el dueño de la plataforma) y
 * {@link RequiereRol} / {@link UsuarioInterno} (lo decide el administrador de la empresa).
 * <p>Antes de mirar el rol revisa la suscripción de la empresa en toda petición autenticada (menos
 * {@code /api/auth/**}, para que una empresa suspendida pueda cerrar su sesión): suspendida o cancelada no entra y
 * morosa solo consulta. La consola de la plataforma ({@code /api/plataforma/**}) no pasa por aquí: su token no es de
 * una empresa.</p>
 * <p>Para cada anotación, la del método tiene prioridad sobre la de la clase. Si hay varias, deben cumplirse todas.</p>
 */
@Component
public class RolInterceptor implements HandlerInterceptor {

    private static final String PREFIJO_AUTH = "/api/auth/";
    private static final String PREFIJO_PLATAFORMA = "/api/plataforma/";

    private final AccesoEmpresaService accesoEmpresa;

    /**
     * @param accesoEmpresa suscripción y módulos de la empresa del usuario
     */
    public RolInterceptor(AccesoEmpresaService accesoEmpresa) {
        this.accesoEmpresa = accesoEmpresa;
    }

    /**
     * Verifica suscripción, módulo y permisos antes de ejecutar el controller.
     *
     * @param request  petición
     * @param response respuesta
     * @param handler  método del controller que atenderá la petición
     * @return {@code true} para continuar
     * @throws AccesoDenegadoException si la empresa o el usuario no cumplen alguna de las reglas
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod metodo)) {
            return true; // recursos estáticos, etc.
        }
        String ruta = request.getRequestURI().substring(request.getContextPath().length());
        if (ruta.startsWith(PREFIJO_PLATAFORMA)) {
            return true;
        }
        UsuarioAutenticado usuario = SesionActual.opcional().orElse(null);
        if (usuario != null) {
            if (!ruta.startsWith(PREFIJO_AUTH)) {
                accesoEmpresa.exigirSuscripcion(usuario.empresaId(), request.getMethod());
            }
            RequiereModulo modulo = buscar(metodo, RequiereModulo.class);
            if (modulo != null) {
                accesoEmpresa.exigirModulo(usuario.empresaId(), modulo.value());
            }
        }
        RequiereRol regla = buscar(metodo, RequiereRol.class);
        boolean soloInternos = buscar(metodo, UsuarioInterno.class) != null;
        if (regla == null && !soloInternos) {
            return true; // basta con estar autenticado (lo garantiza el AuthFilter)
        }
        UsuarioAutenticado sesion = SesionActual.usuario();
        if (soloInternos && sesion.esProveedor()) {
            throw new AccesoDenegadoException("Esta información es solo para usuarios internos");
        }
        if (regla != null && !sesion.tieneAlgunRol(regla.value())) {
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
