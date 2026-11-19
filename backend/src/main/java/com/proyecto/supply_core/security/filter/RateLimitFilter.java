package com.proyecto.supply_core.security.filter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

import com.proyecto.supply_core.common.exception.DemasiadasPeticionesException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Limita las peticiones por IP a las rutas públicas sensibles (login, renovación y registro de
 * proveedores) para frenar fuerza bruta y registros masivos. Ventana fija de un minuto, en memoria.
 * <p>Con varias instancias del backend el límite sería por instancia; para repartirlo haría falta
 * Redis. Usa {@code getRemoteAddr()}: detrás de un proxy de confianza hay que configurarlo para que
 * entregue la IP real del cliente.</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RateLimitFilter extends OncePerRequestFilter {

    private static final long VENTANA_MS = 60_000L;
    private static final List<String> RUTAS_LIMITADAS = List.of("/api/auth/login", "/api/auth/refresh",
            "/api/auth/recuperar", "/api/auth/restablecer", "/api/auth/mfa", "/api/portal/registro/**",
            "/api/plataforma/auth/**", "/api/autorizaciones");

    private final int limitePorMinuto;
    private final HandlerExceptionResolver exceptionResolver;
    private final AntPathMatcher matcher = new AntPathMatcher();
    private final Map<String, Ventana> ventanas = new ConcurrentHashMap<>();

    /** Contador de una IP dentro de la ventana actual. */
    private static final class Ventana {
        private long inicio;
        private int cuenta;
    }

    /**
     * @param limitePorMinuto   peticiones permitidas por IP y por minuto
     * @param exceptionResolver resolver de Spring MVC que invoca al {@code @RestControllerAdvice}
     */
    public RateLimitFilter(@Value("${app.sesion.limite-por-minuto:20}") int limitePorMinuto,
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver exceptionResolver) {
        this.limitePorMinuto = limitePorMinuto;
        this.exceptionResolver = exceptionResolver;
    }

    /**
     * Solo filtra las rutas sensibles enviadas por POST.
     *
     * @param request petición entrante
     * @return {@code true} si la petición no está limitada
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String ruta = request.getRequestURI().substring(request.getContextPath().length());
        return !"POST".equalsIgnoreCase(request.getMethod())
                || RUTAS_LIMITADAS.stream().noneMatch(p -> matcher.match(p, ruta));
    }

    /**
     * Cuenta la petición y responde 429 si la IP superó el límite de la ventana.
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
        if (excedeLimite(request.getRemoteAddr(), System.currentTimeMillis())) {
            response.setHeader("Retry-After", "60");
            exceptionResolver.resolveException(request, response, null, new DemasiadasPeticionesException(
                    "Demasiadas solicitudes, espere un minuto e intente de nuevo"));
            return;
        }
        chain.doFilter(request, response);
    }

    /**
     * Registra una petición de la IP y dice si ya pasó el límite.
     *
     * @param ip    dirección del cliente
     * @param ahora milisegundos actuales
     * @return {@code true} si debe rechazarse
     */
    private boolean excedeLimite(String ip, long ahora) {
        if (ventanas.size() > 10_000) { // evita que la memoria crezca sin límite
            ventanas.values().removeIf(v -> ahora - v.inicio > VENTANA_MS);
        }
        Ventana v = ventanas.computeIfAbsent(ip, k -> new Ventana());
        synchronized (v) {
            if (ahora - v.inicio > VENTANA_MS) {
                v.inicio = ahora;
                v.cuenta = 0;
            }
            v.cuenta++;
            return v.cuenta > limitePorMinuto;
        }
    }
}
