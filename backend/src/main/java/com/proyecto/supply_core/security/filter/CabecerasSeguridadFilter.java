package com.proyecto.supply_core.security.filter;

import java.io.IOException;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Agrega las cabeceras de seguridad a toda respuesta de la API, también a las que otros filtros cortan (401, 429).
 * <ul>
 *   <li>{@code X-Content-Type-Options: nosniff} y {@code X-Frame-Options: DENY}: el navegador no adivina tipos ni
 *       deja incrustar la API en un marco.</li>
 *   <li>{@code Content-Security-Policy: default-src 'none'; frame-ancestors 'none'}: una respuesta de la API nunca
 *       carga scripts, estilos ni imágenes.</li>
 *   <li>{@code Cache-Control: no-store}: ni el navegador ni un proxy guardan datos de la empresa.</li>
 *   <li>{@code Strict-Transport-Security}: solo cuando la petición llegó por HTTPS.</li>
 *   <li>{@code Referrer-Policy}, {@code Permissions-Policy} y {@code Cross-Origin-Resource-Policy}.</li>
 * </ul>
 * <p>La política restrictiva se aplica solo a {@code /api/}: la documentación Swagger necesita cargar sus scripts.</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CabecerasSeguridadFilter extends OncePerRequestFilter {

    /** Política de contenido de la API: no carga nada. */
    static final String POLITICA_CONTENIDO = "default-src 'none'; frame-ancestors 'none'";
    /** Un año, subdominios incluidos. */
    static final String TRANSPORTE_SEGURO = "max-age=31536000; includeSubDomains";

    /**
     * Pone las cabeceras antes de seguir con la cadena, para que lleguen aunque un filtro posterior responda solo.
     *
     * @param request  petición entrante
     * @param response respuesta
     * @param chain    resto de la cadena de filtros
     * @throws ServletException si falla un filtro posterior
     * @throws IOException      si falla la escritura de la respuesta
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("X-Frame-Options", "DENY");
        response.setHeader("Referrer-Policy", "no-referrer");
        response.setHeader("Permissions-Policy", "geolocation=(), microphone=(), camera=()");
        response.setHeader("Cross-Origin-Resource-Policy", "same-origin");
        if (request.isSecure()) {
            response.setHeader("Strict-Transport-Security", TRANSPORTE_SEGURO);
        }
        String ruta = request.getRequestURI().substring(request.getContextPath().length());
        if (ruta.startsWith("/api/")) {
            response.setHeader("Content-Security-Policy", POLITICA_CONTENIDO);
            response.setHeader("Cache-Control", "no-store");
        }
        chain.doFilter(request, response);
    }
}
