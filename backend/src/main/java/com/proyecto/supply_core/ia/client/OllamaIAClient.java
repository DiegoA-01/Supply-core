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

import com.proyecto.supply_core.ia.dto.ArchivoIA;
import com.proyecto.supply_core.ia.dto.ExtraccionIA;
import com.proyecto.supply_core.ia.dto.LineaContexto;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * IA local servida por Ollama ({@code POST /api/chat}), gratis y sin que los documentos salgan del equipo.
 * <ul>
 *   <li>Excel y PDF digitales se envían como texto; imágenes y PDF escaneados, como imágenes.</li>
 *   <li>La salida se fuerza con un esquema JSON ({@code format}) y se valida al convertirla.</li>
 *   <li>En equipos sin GPU puede tardar minutos por documento: por eso corre en segundo plano.</li>
 * </ul>
 */
public class OllamaIAClient implements IAClient {

    private static final Logger log = LoggerFactory.getLogger(OllamaIAClient.class);

    private final RestClient http;
    private final String modelo;
    private final JsonMapper json = JsonMapper.builder().build();

    /**
     * @param url             dirección de Ollama (p. ej. http://localhost:11434)
     * @param modelo          modelo instalado con visión (p. ej. qwen2.5vl:3b)
     * @param timeoutSegundos espera máxima por respuesta
     */
    public OllamaIAClient(String url, String modelo, int timeoutSegundos) {
        HttpClient cliente = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        JdkClientHttpRequestFactory fabrica = new JdkClientHttpRequestFactory(cliente);
        fabrica.setReadTimeout(Duration.ofSeconds(timeoutSegundos));
        this.http = RestClient.builder().baseUrl(url).requestFactory(fabrica).build();
        this.modelo = modelo;
    }

    /**
     * Disponible si está configurado (si el servidor está apagado, la llamada falla con un mensaje claro).
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
     * @return {@code ollama:<modelo>}
     */
    @Override
    public String modelo() {
        return "ollama:" + modelo;
    }

    /**
     * Lee la cotización forzando la salida al esquema JSON.
     *
     * @param archivo   documento original
     * @param productos líneas de la RFQ
     * @return datos extraídos
     * @throws IAException si Ollama no responde o devuelve algo que no cumple el esquema
     */
    @Override
    public ExtraccionIA extraer(ArchivoIA archivo, List<LineaContexto> productos) {
        String contexto = PromptsIA.productosPedidos(productos);
        List<String> imagenes = new ArrayList<>();
        String texto;
        if (archivo.esExcel()) {
            texto = "Contenido de la hoja de cálculo de la cotización:\n" + LectorArchivos.excelATexto(archivo.contenido());
        } else if (archivo.esWord()) {
            texto = "Contenido del documento de Word de la cotización:\n" + LectorArchivos.wordATexto(archivo.contenido());
        } else if (archivo.esPdf()) {
            String textoPdf = LectorArchivos.pdfATexto(archivo.contenido());
            if (textoPdf.isBlank()) { // PDF escaneado: se envían las páginas como imagen
                LectorArchivos.pdfAImagenes(archivo.contenido()).forEach(p -> imagenes.add(base64(p)));
                texto = "La cotización está en las imágenes adjuntas.";
            } else {
                texto = "Texto de la cotización (PDF):\n" + textoPdf;
            }
        } else if (archivo.esImagen()) {
            imagenes.add(base64(archivo.contenido()));
            texto = "La cotización está en la imagen adjunta.";
        } else {
            throw new IAException("Tipo de archivo no soportado por la IA: " + archivo.tipoMime());
        }

        Map<String, Object> usuario = new LinkedHashMap<>();
        usuario.put("role", "user");
        usuario.put("content", texto + "\n\n" + contexto);
        if (!imagenes.isEmpty()) {
            usuario.put("images", imagenes);
        }
        String contenido = chat(List.of(Map.of("role", "system", "content", PromptsIA.SISTEMA_EXTRACCION), usuario),
                esquemaExtraccion());
        try {
            return json.readValue(contenido, ExtraccionIA.class);
        } catch (JacksonException e) {
            throw new IAException("La respuesta de Ollama no cumple el formato esperado: " + e.getOriginalMessage(), e);
        }
    }

    /**
     * Redacta la explicación del ranking.
     *
     * @param resumen ranking calculado por Java
     * @return explicación
     */
    @Override
    public String explicar(String resumen) {
        return chat(List.of(Map.of("role", "system", "content", PromptsIA.SISTEMA_EXPLICACION),
                Map.of("role", "user", "content", resumen)), null).trim();
    }

    /**
     * Llama a {@code /api/chat} sin streaming.
     *
     * @param mensajes mensajes del chat
     * @param formato  esquema JSON de salida o {@code null} para texto libre
     * @return contenido de la respuesta
     * @throws IAException si Ollama no responde
     */
    @SuppressWarnings("unchecked")
    private String chat(List<Map<String, Object>> mensajes, Map<String, Object> formato) {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("model", modelo);
        cuerpo.put("messages", mensajes);
        cuerpo.put("stream", false);
        cuerpo.put("options", Map.of("temperature", 0));
        if (formato != null) {
            cuerpo.put("format", formato);
        }
        try {
            Map<String, Object> respuesta = http.post().uri("/api/chat").contentType(MediaType.APPLICATION_JSON)
                    .body(cuerpo).retrieve().body(Map.class);
            Object mensaje = respuesta == null ? null : respuesta.get("message");
            if (!(mensaje instanceof Map<?, ?> m) || !(m.get("content") instanceof String contenido)) {
                throw new IAException("Ollama respondió sin contenido");
            }
            return contenido;
        } catch (RestClientException e) {
            log.warn("Falló la llamada a Ollama: {}", e.getMessage());
            throw new IAException("Ollama no respondió (¿está instalado y corriendo, y el modelo " + modelo
                    + " descargado?): " + e.getMessage(), e);
        }
    }

    /**
     * Esquema JSON equivalente a {@link ExtraccionIA}, para el parámetro {@code format} de Ollama.
     *
     * @return esquema
     */
    static Map<String, Object> esquemaExtraccion() {
        Map<String, Object> texto = Map.of("type", "string");
        Map<String, Object> numero = Map.of("type", "number");
        Map<String, Object> linea = Map.of("type", "object",
                "properties", Map.ofEntries(
                        Map.entry("rfqLineaId", Map.of("type", "integer")), Map.entry("descripcion", texto),
                        Map.entry("cantidad", texto), Map.entry("unidad", texto), Map.entry("factorConversion", texto),
                        Map.entry("precioUnitario", texto), Map.entry("descuentoPct", texto),
                        Map.entry("impuestoPct", texto), Map.entry("confianzaCantidad", numero),
                        Map.entry("confianzaUnidad", numero), Map.entry("confianzaPrecio", numero)),
                "required", List.of("rfqLineaId", "descripcion", "cantidad", "unidad", "factorConversion",
                        "precioUnitario", "descuentoPct", "impuestoPct", "confianzaCantidad", "confianzaUnidad",
                        "confianzaPrecio"));
        return Map.of("type", "object",
                "properties", Map.of("moneda", texto, "plazoEntregaDias", texto, "garantiaMeses", texto,
                        "validezHasta", texto, "condiciones", texto, "confianzaCabecera", numero,
                        "lineas", Map.of("type", "array", "items", linea), "totalImpreso", texto),
                "required", List.of("moneda", "plazoEntregaDias", "garantiaMeses", "validezHasta", "condiciones",
                        "confianzaCabecera", "lineas", "totalImpreso"));
    }

    /**
     * Bytes a base64.
     *
     * @param bytes contenido
     * @return base64 sin saltos de línea
     */
    private static String base64(byte[] bytes) {
        return Base64.getEncoder().encodeToString(bytes);
    }
}
