package com.proyecto.supply_core.ia.client;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import com.proyecto.supply_core.ia.dto.ArchivoIA;
import com.proyecto.supply_core.ia.dto.ExtraccionIA;
import com.proyecto.supply_core.ia.dto.LineaContexto;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * IA de Google (API de Gemini, {@code generateContent}) para leer cotizaciones de cualquier formato.
 * <ul>
 *   <li>PDF (digital o escaneado) e imágenes (fotos de una cotización) se envían tal cual: Gemini los lee con su
 *       propia visión.</li>
 *   <li>Excel y Word se convierten a texto en Java antes de enviarlos (todas las hojas, tablas incluidas): así no se
 *       pierde nada ni se depende de cómo cada formato lo trate el modelo.</li>
 *   <li>La salida se fuerza a un esquema JSON ({@code responseJsonSchema}) y Java la valida y calcula todo el dinero.</li>
 *   <li>La clave va en la cabecera {@code x-goog-api-key}, nunca en la dirección, y no se escribe en el log.</li>
 * </ul>
 * <p><b>Privacidad:</b> los documentos salen del equipo hacia Google. Con una clave del nivel gratuito Google puede usar
 * lo enviado para mejorar sus productos; para datos reales de clientes conviene una clave con facturación activada.
 * Si Gemini responde 429 o 503 se reintenta una vez; si sigue fallando la lectura queda en error y se puede digitar a
 * mano.</p>
 */
public class GeminiIAClient implements IAClient {

    /** Dirección base de la API. */
    static final String URL_BASE = "https://generativelanguage.googleapis.com/v1beta";

    private static final Logger log = LoggerFactory.getLogger(GeminiIAClient.class);
    private static final long ESPERA_REINTENTO_MS = 2_000;

    private final RestClient http;
    private final String modelo;
    private final long esperaReintentoMs;
    private final JsonMapper json = JsonMapper.builder().build();

    /**
     * @param apiKey          clave de la API de Google AI Studio (secreta)
     * @param modelo          modelo (p. ej. {@code gemini-2.5-flash})
     * @param timeoutSegundos espera máxima por respuesta
     */
    public GeminiIAClient(String apiKey, String modelo, int timeoutSegundos) {
        this(apiKey, modelo, timeoutSegundos, URL_BASE, ESPERA_REINTENTO_MS);
    }

    /**
     * Constructor con dirección y espera configurables (para probar con un servidor local).
     *
     * @param apiKey            clave de la API
     * @param modelo            modelo
     * @param timeoutSegundos   espera máxima por respuesta
     * @param urlBase           dirección base de la API
     * @param esperaReintentoMs milisegundos de espera antes del reintento
     */
    GeminiIAClient(String apiKey, String modelo, int timeoutSegundos, String urlBase, long esperaReintentoMs) {
        HttpClient cliente = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        JdkClientHttpRequestFactory fabrica = new JdkClientHttpRequestFactory(cliente);
        fabrica.setReadTimeout(Duration.ofSeconds(timeoutSegundos));
        this.http = RestClient.builder().baseUrl(urlBase).requestFactory(fabrica)
                .defaultHeader("x-goog-api-key", apiKey).build();
        this.modelo = modelo;
        this.esperaReintentoMs = esperaReintentoMs;
    }

    /**
     * Disponible si está configurado (si la clave no sirve, la llamada falla con un mensaje claro).
     *
     * @return {@code true}
     */
    @Override
    public boolean disponible() {
        return true;
    }

    /**
     * Modelo configurado.
     *
     * @return {@code gemini:<modelo>}
     */
    @Override
    public String modelo() {
        return "gemini:" + modelo;
    }

    /**
     * Lee la cotización forzando la salida al esquema JSON.
     *
     * @param archivo   documento original (PDF, imagen, Excel o Word)
     * @param productos líneas de la RFQ
     * @return datos extraídos
     * @throws IAException si Gemini no responde, rechaza la solicitud o devuelve algo que no cumple el esquema
     */
    @Override
    public ExtraccionIA extraer(ArchivoIA archivo, List<LineaContexto> productos) {
        List<Map<String, Object>> partes = new ArrayList<>();
        if (archivo.esPdf() || archivo.esImagen()) {
            partes.add(Map.of("inline_data", Map.of("mime_type", archivo.tipoMime(), "data",
                    Base64.getEncoder().encodeToString(archivo.contenido()))));
        } else if (archivo.esExcel()) {
            partes.add(Map.of("text", "Contenido de la hoja de cálculo de la cotización:\n"
                    + LectorArchivos.excelATexto(archivo.contenido())));
        } else if (archivo.esWord()) {
            partes.add(Map.of("text", "Contenido del documento de Word de la cotización:\n"
                    + LectorArchivos.wordATexto(archivo.contenido())));
        } else {
            throw new IAException("Tipo de archivo no soportado por la IA: " + archivo.tipoMime());
        }
        partes.add(Map.of("text", PromptsIA.productosPedidos(productos)));

        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("systemInstruction", Map.of("parts", List.of(Map.of("text", PromptsIA.SISTEMA_EXTRACCION))));
        cuerpo.put("contents", List.of(Map.of("role", "user", "parts", partes)));
        cuerpo.put("generationConfig", Map.of("temperature", 0, "responseMimeType", "application/json",
                "responseJsonSchema", OllamaIAClient.esquemaExtraccion()));
        String contenido = generar(cuerpo);
        try {
            return json.readValue(contenido, ExtraccionIA.class);
        } catch (JacksonException e) {
            throw new IAException("La respuesta de Gemini no cumple el formato esperado: " + e.getOriginalMessage(), e);
        }
    }

    /**
     * Redacta la explicación del ranking.
     *
     * @param resumen ranking calculado por Java
     * @return explicación
     * @throws IAException si Gemini no responde
     */
    @Override
    public String explicar(String resumen) {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("systemInstruction", Map.of("parts", List.of(Map.of("text", PromptsIA.SISTEMA_EXPLICACION))));
        cuerpo.put("contents", List.of(Map.of("role", "user", "parts", List.of(Map.of("text", resumen)))));
        cuerpo.put("generationConfig", Map.of("temperature", 0));
        return generar(cuerpo).trim();
    }

    // ----------------------------------------------------------------- apoyo

    /**
     * Llama a {@code generateContent} (con un reintento si Gemini está saturado) y devuelve el texto generado.
     *
     * @param cuerpo solicitud
     * @return texto de la respuesta
     * @throws IAException si la llamada falla o la respuesta no sirve
     */
    @SuppressWarnings("unchecked")
    private String generar(Map<String, Object> cuerpo) {
        for (int intento = 1;; intento++) {
            try {
                Map<String, Object> respuesta = http.post().uri("/models/{modelo}:generateContent", modelo)
                        .contentType(MediaType.APPLICATION_JSON).body(cuerpo).retrieve().body(Map.class);
                return textoDe(respuesta);
            } catch (RestClientResponseException e) {
                int codigo = e.getStatusCode().value();
                if ((codigo == 429 || codigo == 503) && intento < 2) {
                    log.info("Gemini respondió {}; se reintenta en {} ms", codigo, esperaReintentoMs);
                    esperar();
                    continue;
                }
                log.warn("Falló la llamada a Gemini: HTTP {}", codigo);
                throw new IAException(mensajeDeError(codigo, e.getResponseBodyAsString()), e);
            } catch (RestClientException e) {
                log.warn("Falló la llamada a Gemini: {}", e.getMessage());
                throw new IAException("Gemini no respondió: " + e.getMessage(), e);
            }
        }
    }

    /** Espera antes de reintentar, respetando una interrupción del hilo. */
    private void esperar() {
        try {
            Thread.sleep(esperaReintentoMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IAException("La lectura con Gemini se interrumpió", e);
        }
    }

    /**
     * Saca el texto generado de la respuesta y rechaza las respuestas bloqueadas o incompletas.
     *
     * @param respuesta cuerpo de la respuesta
     * @return texto generado
     * @throws IAException si Gemini bloqueó la solicitud, cortó la respuesta o no devolvió texto
     */
    @SuppressWarnings("unchecked")
    private static String textoDe(Map<String, Object> respuesta) {
        if (respuesta == null) {
            throw new IAException("Gemini respondió sin contenido");
        }
        if (respuesta.get("promptFeedback") instanceof Map<?, ?> feedback && feedback.get("blockReason") != null) {
            throw new IAException("Gemini bloqueó la solicitud: " + feedback.get("blockReason"));
        }
        if (!(respuesta.get("candidates") instanceof List<?> candidatos) || candidatos.isEmpty()
                || !(candidatos.get(0) instanceof Map<?, ?> candidato)) {
            throw new IAException("Gemini no devolvió ninguna respuesta");
        }
        Object fin = candidato.get("finishReason");
        if (fin != null && !"STOP".equals(fin)) {
            throw new IAException("MAX_TOKENS".equals(fin) ? "La respuesta de Gemini llegó incompleta"
                    : "Gemini no completó la lectura (" + fin + ")");
        }
        StringBuilder texto = new StringBuilder();
        if (candidato.get("content") instanceof Map<?, ?> contenido && contenido.get("parts") instanceof List<?> partes) {
            for (Object parte : partes) {
                if (parte instanceof Map<?, ?> p && p.get("text") instanceof String t && !Boolean.TRUE.equals(p.get("thought"))) {
                    texto.append(t);
                }
            }
        }
        if (texto.isEmpty()) {
            throw new IAException("Gemini respondió sin contenido");
        }
        return texto.toString();
    }

    /**
     * Mensaje claro para un error HTTP de la API, sin repetir nada sensible.
     *
     * @param codigo estado HTTP
     * @param cuerpo cuerpo de la respuesta de error
     * @return mensaje para el comprador
     */
    private String mensajeDeError(int codigo, String cuerpo) {
        String detalle = "";
        try {
            Object error = json.readValue(cuerpo, Map.class).get("error");
            if (error instanceof Map<?, ?> e && e.get("message") instanceof String m) {
                detalle = ": " + m.substring(0, Math.min(m.length(), 200));
            }
        } catch (RuntimeException e) {
            // cuerpo que no es JSON (JacksonException es una RuntimeException): se muestra solo el código
        }
        return switch (codigo) {
            case 400 -> "Gemini rechazó la solicitud" + detalle;
            case 401, 403 -> "La API key de Gemini no es válida o no tiene permiso para el modelo " + modelo;
            case 404 -> "El modelo " + modelo + " no existe o no está disponible para esta API key";
            case 429 -> "Se superó la cuota de Gemini (límite por minuto o por día); intente de nuevo más tarde";
            default -> "Gemini respondió con error HTTP " + codigo + detalle;
        };
    }
}
