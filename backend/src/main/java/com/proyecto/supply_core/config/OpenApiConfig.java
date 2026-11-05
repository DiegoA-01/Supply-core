package com.proyecto.supply_core.config;

import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;

/**
 * Documentación OpenAPI. Swagger queda en {@code /swagger-ui.html} con el botón "Authorize"
 * para pegar el JWT obtenido en {@code POST /api/auth/login}. Solo contiene anotaciones.
 */
@Configuration
@OpenAPIDefinition(
        info = @Info(title = "Supply-Core API", version = "v1",
                description = "Abastecimiento, inventario y compras con análisis de cotizaciones por IA"),
        security = @SecurityRequirement(name = "bearerAuth"))
@SecurityScheme(name = "bearerAuth", type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT")
public class OpenApiConfig {
}
