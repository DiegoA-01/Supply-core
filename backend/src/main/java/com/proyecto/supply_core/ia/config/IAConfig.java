package com.proyecto.supply_core.ia.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

import com.proyecto.supply_core.ia.client.ClaudeIAClient;
import com.proyecto.supply_core.ia.client.GeminiIAClient;
import com.proyecto.supply_core.ia.client.IAClient;
import com.proyecto.supply_core.ia.client.OllamaIAClient;
import com.proyecto.supply_core.ia.client.SinIAClient;

/**
 * Elige la implementación de {@link IAClient} según {@code app.ia.proveedor} y activa {@code @Async}
 * (la lectura de cotizaciones corre en segundo plano).
 * <p>Si se pide Claude o Gemini sin API key, se usa el modo sin IA y se avisa en el log (la app arranca igual).</p>
 */
@Configuration
@EnableAsync
@EnableConfigurationProperties(IAProperties.class)
public class IAConfig {

    private static final Logger log = LoggerFactory.getLogger(IAConfig.class);

    /**
     * Cliente de IA configurado.
     *
     * @param props configuración {@code app.ia.*}
     * @return implementación elegida
     */
    @Bean
    public IAClient iaClient(IAProperties props) {
        return switch (props.proveedor()) {
            case CLAUDE -> {
                if (props.claude().apiKey() == null || props.claude().apiKey().isBlank()) {
                    log.warn("app.ia.proveedor=claude pero falta la API key (ANTHROPIC_API_KEY): se usa el modo sin IA");
                    yield new SinIAClient();
                }
                log.info("IA: Claude ({})", props.claude().modelo());
                yield new ClaudeIAClient(props.claude().apiKey(), props.claude().modelo());
            }
            case GEMINI -> {
                if (props.gemini().apiKey() == null || props.gemini().apiKey().isBlank()) {
                    log.warn("app.ia.proveedor=gemini pero falta la API key (GEMINI_API_KEY): se usa el modo sin IA");
                    yield new SinIAClient();
                }
                log.info("IA: Gemini ({})", props.gemini().modelo());
                yield new GeminiIAClient(props.gemini().apiKey(), props.gemini().modelo(),
                        props.gemini().timeoutSegundos());
            }
            case OLLAMA -> {
                log.info("IA: Ollama local ({} en {})", props.ollama().modelo(), props.ollama().url());
                yield new OllamaIAClient(props.ollama().url(), props.ollama().modelo(),
                        props.ollama().timeoutSegundos());
            }
            case NINGUNO -> {
                log.info("IA: ninguna (las cotizaciones por archivo se digitan a mano)");
                yield new SinIAClient();
            }
        };
    }
}
