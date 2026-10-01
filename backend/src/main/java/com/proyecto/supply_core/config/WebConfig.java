package com.proyecto.supply_core.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.proyecto.supply_core.security.interceptor.RolInterceptor;

/** Configuración MVC: CORS para el frontend Angular y control de roles ({@code @RequiereRol}). */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final String[] origenesPermitidos;
    private final RolInterceptor rolInterceptor;

    /**
     * @param origenesPermitidos orígenes del frontend ({@code app.cors.allowed-origins}, separados por coma)
     * @param rolInterceptor     interceptor que aplica {@code @RequiereRol}
     */
    public WebConfig(@Value("${app.cors.allowed-origins}") String[] origenesPermitidos,
            RolInterceptor rolInterceptor) {
        this.origenesPermitidos = origenesPermitidos;
        this.rolInterceptor = rolInterceptor;
    }

    /**
     * Registra el control de roles para toda la API.
     *
     * @param registry registro de interceptores de Spring MVC
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(rolInterceptor).addPathPatterns("/api/**");
    }

    /**
     * Permite que el frontend (otro origen) llame a la API enviando el header Authorization.
     *
     * @param registry registro CORS de Spring MVC
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(origenesPermitidos)
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("Authorization", "Content-Type")
                .maxAge(3600);
    }
}
