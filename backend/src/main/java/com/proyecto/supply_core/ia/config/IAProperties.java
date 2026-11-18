package com.proyecto.supply_core.ia.config;

import java.math.BigDecimal;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Configuración de la IA ({@code app.ia.*}). La API key de Claude nunca va en {@code application.yaml}:
 * se pone en {@code application-local.yaml} (gitignored) o en la variable {@code ANTHROPIC_API_KEY}.
 *
 * @param proveedor       quién lee las cotizaciones
 * @param umbralConfianza confianza mínima de un dato crítico para no pedir revisión humana
 * @param claude          datos de Claude
 * @param ollama          datos de Ollama
 * @param gemini          datos de Gemini
 */
@Validated
@ConfigurationProperties(prefix = "app.ia")
public record IAProperties(
        @NotNull(message = "es obligatorio") @DefaultValue("ninguno") Proveedor proveedor,
        @DecimalMin(value = "0", message = "debe estar entre 0 y 1")
        @DecimalMax(value = "1", message = "debe estar entre 0 y 1")
        @DefaultValue("0.80") BigDecimal umbralConfianza,
        @Valid @DefaultValue Claude claude,
        @Valid @DefaultValue Ollama ollama,
        @Valid @DefaultValue Gemini gemini) {

    /** Proveedores soportados. */
    public enum Proveedor {
        /** Sin IA: las cotizaciones por archivo se digitan a mano. */
        NINGUNO,
        /** Modelo local servido por Ollama (gratis, corre en el equipo). */
        OLLAMA,
        /** API de Anthropic (de pago). */
        CLAUDE,
        /** API de Google AI Studio (Gemini): lee PDF, imágenes, Excel y Word. */
        GEMINI
    }

    /**
     * Configuración de Claude.
     *
     * @param apiKey clave de la API (secreta)
     * @param modelo id del modelo
     */
    public record Claude(String apiKey, @DefaultValue("claude-opus-5-5") String modelo) {
    }

    /**
     * Configuración de Gemini (API de Google AI Studio; la suscripción Gemini Plus/Pro de la app no da acceso a la API).
     *
     * @param apiKey          clave de la API (secreta; variable {@code GEMINI_API_KEY})
     * @param modelo          id del modelo
     * @param timeoutSegundos tiempo máximo de espera por documento
     */
    public record Gemini(String apiKey, @DefaultValue("gemini-2.5-flash") String modelo,
            @Positive(message = "debe ser mayor que cero") @DefaultValue("180") int timeoutSegundos) {
    }

    /**
     * Configuración de Ollama.
     *
     * @param url             dirección del servidor de Ollama
     * @param modelo          modelo con visión instalado (ollama pull ...)
     * @param timeoutSegundos tiempo máximo de espera por documento
     */
    public record Ollama(@DefaultValue("http://localhost:11434") String url,
            @DefaultValue("qwen2.5vl:3b") String modelo,
            @Positive(message = "debe ser mayor que cero") @DefaultValue("600") int timeoutSegundos) {
    }
}
