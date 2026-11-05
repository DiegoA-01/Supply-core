package com.proyecto.supply_core.ia.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import com.proyecto.supply_core.ia.config.IAConfig;
import com.proyecto.supply_core.ia.config.IAProperties;
import com.proyecto.supply_core.ia.dto.ArchivoIA;
import com.proyecto.supply_core.ia.dto.LineaContexto;

class IAClientesTest {

    private static final List<LineaContexto> PRODUCTOS = List.of(
            new LineaContexto(500L, "LAP", "Lápiz HB", new BigDecimal("48"), "UND", "Caja x12"));

    @Test
    void laConfiguracionEligeElCliente() {
        IAConfig config = new IAConfig();
        assertInstanceOf(SinIAClient.class, config.iaClient(props(IAProperties.Proveedor.NINGUNO, null)));
        assertInstanceOf(OllamaIAClient.class, config.iaClient(props(IAProperties.Proveedor.OLLAMA, null)));
        // Claude sin API key: la app arranca igual, sin IA
        assertInstanceOf(SinIAClient.class, config.iaClient(props(IAProperties.Proveedor.CLAUDE, " ")));
        assertInstanceOf(ClaudeIAClient.class, config.iaClient(props(IAProperties.Proveedor.CLAUDE, "sk-ant-prueba")));
    }

    @Test
    void sinIaNoEstaDisponible() {
        SinIAClient sin = new SinIAClient();
        assertFalse(sin.disponible());
        assertThrows(IAException.class, () -> sin.extraer(null, PRODUCTOS));
    }

    @Test
    void ollamaApagadoDaUnErrorClaro() {
        OllamaIAClient ollama = new OllamaIAClient("http://localhost:1", "qwen2.5vl:3b", 5);
        IAException e = assertThrows(IAException.class,
                () -> ollama.extraer(new ArchivoIA(new byte[] { 1, 2 }, "image/png", "c.png"), PRODUCTOS));
        assertTrue(e.getMessage().contains("¿está instalado y corriendo"));
        assertEquals("ollama:qwen2.5vl:3b", ollama.modelo());
    }

    @Test
    void esquemaDeOllamaExigeLosCamposDelRegistro() {
        Map<String, Object> esquema = OllamaIAClient.esquemaExtraccion();
        assertTrue(((List<?>) esquema.get("required")).containsAll(List.of("lineas", "confianzaCabecera")));
    }

    @Test
    void excelSeConvierteATextoParaLaIa() throws Exception {
        byte[] xlsx;
        try (XSSFWorkbook libro = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            var hoja = libro.createSheet("Cotizacion");
            var fila = hoja.createRow(0);
            fila.createCell(0).setCellValue("Lápiz HB");
            fila.createCell(1).setCellValue(12000);
            libro.write(out);
            xlsx = out.toByteArray();
        }
        String texto = LectorArchivos.excelATexto(xlsx);
        assertTrue(texto.contains("Hoja: Cotizacion"));
        assertTrue(texto.contains("Lápiz HB | 12000"));
        assertTrue(PromptsIA.productosPedidos(PRODUCTOS).contains("500 | LAP | Lápiz HB | 48 | UND | Caja x12"));
    }

    private static IAProperties props(IAProperties.Proveedor proveedor, String apiKey) {
        return new IAProperties(proveedor, new BigDecimal("0.80"), new IAProperties.Claude(apiKey, "claude-opus-5-5"),
                new IAProperties.Ollama("http://localhost:11434", "qwen2.5vl:3b", 60));
    }
}
