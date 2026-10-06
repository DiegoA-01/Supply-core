package com.proyecto.supply_core.ia.client;

import java.util.List;

import com.proyecto.supply_core.ia.dto.ArchivoIA;
import com.proyecto.supply_core.ia.dto.ExtraccionIA;
import com.proyecto.supply_core.ia.dto.LineaContexto;

/**
 * Proveedor de IA detrás de una interfaz propia: el dominio no sabe si responde Claude, un modelo
 * local de Ollama o nadie. Se elige con {@code app.ia.proveedor} (ver {@code IAConfig}).
 * <p>Regla del proyecto: la IA solo <b>lee</b> documentos y <b>redacta</b> explicaciones; nunca calcula
 * dinero ni decide. Todo lo que devuelve lo valida y lo calcula Java.</p>
 */
public interface IAClient {

    /**
     * Indica si hay una IA configurada. Si no, las cotizaciones por archivo se digitan a mano.
     *
     * @return {@code true} si se puede llamar a {@link #extraer}
     */
    boolean disponible();

    /**
     * Nombre del modelo, para guardarlo en {@code procesamiento_ia.modelo}.
     *
     * @return p. ej. {@code claude-opus-5-5} u {@code ollama:qwen2.5vl:3b}
     */
    String modelo();

    /**
     * Lee una cotización y devuelve sus datos con un nivel de confianza por campo.
     *
     * @param archivo   documento original (PDF, Excel o imagen)
     * @param productos productos pedidos en la RFQ, para que la IA relacione cada renglón con su línea
     * @return datos extraídos (texto tal cual; Java los convierte y valida)
     * @throws IAException si la IA no responde, rechaza la solicitud o devuelve algo inválido
     */
    ExtraccionIA extraer(ArchivoIA archivo, List<LineaContexto> productos);

    /**
     * Redacta en lenguaje natural por qué el ranking quedó así (ventajas y desventajas).
     *
     * @param resumen datos del ranking ya calculados por Java
     * @return explicación breve en español
     * @throws IAException si la IA no responde
     */
    String explicar(String resumen);
}
