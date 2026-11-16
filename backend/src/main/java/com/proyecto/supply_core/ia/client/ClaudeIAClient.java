package com.proyecto.supply_core.ia.client;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.errors.AnthropicException;
import com.anthropic.models.messages.Base64ImageSource;
import com.anthropic.models.messages.Base64PdfSource;
import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.DocumentBlockParam;
import com.anthropic.models.messages.ImageBlockParam;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.StructuredMessage;
import com.anthropic.models.messages.StructuredMessageCreateParams;
import com.anthropic.models.messages.TextBlockParam;
import com.proyecto.supply_core.ia.dto.ArchivoIA;
import com.proyecto.supply_core.ia.dto.ExtraccionIA;
import com.proyecto.supply_core.ia.dto.LineaContexto;

/**
 * IA de Anthropic con el SDK oficial ({@code com.anthropic:anthropic-java}).
 * <ul>
 *   <li>PDF e imágenes se envían directo (Claude los lee); Excel se convierte a texto antes.</li>
 *   <li>La salida usa structured outputs: el SDK deriva el esquema JSON de {@link ExtraccionIA} y
 *       devuelve el objeto ya tipado.</li>
 *   <li>Si Claude rechaza la solicitud ({@code stop_reason = refusal}) o se corta, se lanza
 *       {@link IAException} y el procesamiento queda para revisión humana.</li>
 * </ul>
 */
public class ClaudeIAClient implements IAClient {

    private static final Logger log = LoggerFactory.getLogger(ClaudeIAClient.class);
    /** Tope de tokens de salida (una cotización extensa cabe holgadamente). */
    private static final long MAX_TOKENS = 16000L;

    private final AnthropicClient client;
    private final String modelo;

    /**
     * @param apiKey clave de la API de Anthropic
     * @param modelo id del modelo (p. ej. {@code claude-opus-5-5})
     */
    public ClaudeIAClient(String apiKey, String modelo) {
        this(AnthropicOkHttpClient.builder().apiKey(apiKey).build(), modelo);
    }

    /**
     * Constructor para pruebas.
     *
     * @param client cliente del SDK
     * @param modelo id del modelo
     */
    ClaudeIAClient(AnthropicClient client, String modelo) {
        this.client = client;
        this.modelo = modelo;
    }

    /**
     * Disponible si se configuró la API key.
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
     * @return id del modelo
     */
    @Override
    public String modelo() {
        return modelo;
    }

    /**
     * Lee la cotización con structured outputs.
     *
     * @param archivo   documento original
     * @param productos líneas de la RFQ
     * @return datos extraídos
     * @throws IAException si la API falla, rechaza o la respuesta llega incompleta
     */
    @Override
    public ExtraccionIA extraer(ArchivoIA archivo, List<LineaContexto> productos) {
        List<ContentBlockParam> bloques = new ArrayList<>();
        if (archivo.esPdf()) {
            bloques.add(ContentBlockParam.ofDocument(DocumentBlockParam.builder()
                    .source(Base64PdfSource.builder().data(base64(archivo)).build())
                    .title(archivo.nombre())
                    .build()));
        } else if (archivo.esImagen()) {
            bloques.add(ContentBlockParam.ofImage(ImageBlockParam.builder()
                    .source(Base64ImageSource.builder()
                            .mediaType(Base64ImageSource.MediaType.of(archivo.tipoMime()))
                            .data(base64(archivo))
                            .build())
                    .build()));
        } else if (archivo.esExcel()) {
            bloques.add(texto("Contenido de la hoja de cálculo de la cotización:\n"
                    + LectorArchivos.excelATexto(archivo.contenido())));
        } else if (archivo.esWord()) {
            bloques.add(texto("Contenido del documento de Word de la cotización:\n"
                    + LectorArchivos.wordATexto(archivo.contenido())));
        } else {
            throw new IAException("Tipo de archivo no soportado por la IA: " + archivo.tipoMime());
        }
        bloques.add(texto(PromptsIA.productosPedidos(productos)));

        StructuredMessageCreateParams<ExtraccionIA> params = MessageCreateParams.builder()
                .model(modelo)
                .maxTokens(MAX_TOKENS)
                .system(PromptsIA.SISTEMA_EXTRACCION)
                .outputConfig(ExtraccionIA.class)
                .addUserMessageOfBlockParams(bloques)
                .build();
        try {
            StructuredMessage<ExtraccionIA> respuesta = client.messages().create(params);
            verificarFin(respuesta.stopReason().orElse(null));
            return respuesta.content().stream()
                    .flatMap(c -> c.text().stream())
                    .map(t -> t.text())
                    .findFirst()
                    .orElseThrow(() -> new IAException("Claude no devolvió datos"));
        } catch (AnthropicException e) {
            log.warn("Falló la llamada a Claude: {}", e.getMessage());
            throw new IAException("Claude no respondió: " + e.getMessage(), e);
        }
    }

    /**
     * Redacta la explicación del ranking.
     *
     * @param resumen ranking calculado por Java
     * @return explicación
     * @throws IAException si la API falla
     */
    @Override
    public String explicar(String resumen) {
        MessageCreateParams params = MessageCreateParams.builder()
                .model(modelo)
                .maxTokens(4000L)
                .system(PromptsIA.SISTEMA_EXPLICACION)
                .addUserMessage(resumen)
                .build();
        try {
            Message respuesta = client.messages().create(params);
            verificarFin(respuesta.stopReason().orElse(null));
            return respuesta.content().stream().flatMap(c -> c.text().stream()).map(t -> t.text())
                    .reduce("", String::concat).trim();
        } catch (AnthropicException e) {
            throw new IAException("Claude no respondió: " + e.getMessage(), e);
        }
    }

    /**
     * Rechaza respuestas que no terminaron normalmente.
     *
     * @param motivo stop reason de la respuesta
     * @throws IAException si fue rechazo o se cortó por tokens
     */
    private static void verificarFin(StopReason motivo) {
        if (StopReason.REFUSAL.equals(motivo)) {
            throw new IAException("Claude rechazó procesar el documento; revíselo manualmente");
        }
        if (StopReason.MAX_TOKENS.equals(motivo)) {
            throw new IAException("La respuesta de Claude se cortó (documento muy extenso)");
        }
    }

    /**
     * Contenido en base64 sin saltos de línea.
     *
     * @param archivo documento
     * @return base64
     */
    private static String base64(ArchivoIA archivo) {
        return Base64.getEncoder().encodeToString(archivo.contenido());
    }

    /**
     * Bloque de texto.
     *
     * @param texto contenido
     * @return bloque
     */
    private static ContentBlockParam texto(String texto) {
        return ContentBlockParam.ofText(TextBlockParam.builder().text(texto).build());
    }
}
