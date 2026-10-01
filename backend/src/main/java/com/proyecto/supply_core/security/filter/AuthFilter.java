package com.proyecto.supply_core.security.filter;

import java.io.IOException;
import java.util.List;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

import com.proyecto.supply_core.common.exception.NoAutenticadoException;
import com.proyecto.supply_core.security.context.SesionActual;
import com.proyecto.supply_core.security.jwt.JwtUtil;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Exige {@code Authorization: Bearer <jwt>} en todo {@code /api/**} salvo las rutas públicas.
 * Los errores se delegan al {@code GlobalExceptionHandler} para responder el mismo {@code ErrorResponse}.
 */
@Component
public class AuthFilter extends OncePerRequestFilter {

    private static final String PREFIJO = "Bearer ";
    /** Rutas sin token: login y autorregistro de proveedores (que no otorga acceso hasta ser aprobado). */
    private static final List<String> RUTAS_PUBLICAS = List.of("/api/auth/login", "/api/portal/registro/**");

    private final JwtUtil jwtUtil;
    private final HandlerExceptionResolver exceptionResolver;
    private final AntPathMatcher matcher = new AntPathMatcher();

    /**
     * @param jwtUtil           validador de tokens
     * @param exceptionResolver resolver de Spring MVC que invoca al {@code @RestControllerAdvice}
     */
    public AuthFilter(JwtUtil jwtUtil,
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver exceptionResolver) {
        this.jwtUtil = jwtUtil;
        this.exceptionResolver = exceptionResolver;
    }

    /**
     * Excluye lo que no es API, el preflight CORS y las rutas públicas.
     *
     * @param request petición entrante
     * @return {@code true} si el filtro no debe exigir token
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String ruta = request.getRequestURI().substring(request.getContextPath().length());
        return !ruta.startsWith("/api/")
                || "OPTIONS".equalsIgnoreCase(request.getMethod())
                || RUTAS_PUBLICAS.stream().anyMatch(p -> matcher.match(p, ruta));
    }

    /**
     * Valida el token y deja el usuario en la petición; si falla responde 401 sin continuar.
     *
     * @param request  petición entrante
     * @param response respuesta
     * @param chain    resto de la cadena de filtros
     * @throws ServletException si falla un filtro posterior
     * @throws IOException      si falla la escritura de la respuesta
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        try {
            String header = request.getHeader(HttpHeaders.AUTHORIZATION);
            if (header == null || !header.startsWith(PREFIJO)) {
                throw new NoAutenticadoException("Debe iniciar sesión");
            }
            request.setAttribute(SesionActual.ATRIBUTO,
                    jwtUtil.validar(header.substring(PREFIJO.length()).trim()));
        } catch (NoAutenticadoException ex) {
            exceptionResolver.resolveException(request, response, null, ex);
            return;
        }
        chain.doFilter(request, response);
    }
}
