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
                new IAProperties.Ollama("http://localhost:11434", "qwen2.5vl:3b", 60),
                new IAProperties.Gemini(apiKey, "gemini-2.5-flash", 60));
    }

    @Test
    void geminiSeActivaSoloConUnaApiKey() {
        IAConfig config = new IAConfig();

        assertInstanceOf(SinIAClient.class, config.iaClient(props(IAProperties.Proveedor.GEMINI, null)));
        assertInstanceOf(SinIAClient.class, config.iaClient(props(IAProperties.Proveedor.GEMINI, " ")));
        IAClient gemini = config.iaClient(props(IAProperties.Proveedor.GEMINI, "AIza-prueba"));
        assertInstanceOf(GeminiIAClient.class, gemini);
        assertEquals("gemini:gemini-2.5-flash", gemini.modelo());
    }

    @Test
    void wordSeConvierteATextoConSusTablasEnFilas() throws Exception {
        byte[] docx;
        try (org.apache.poi.xwpf.usermodel.XWPFDocument doc = new org.apache.poi.xwpf.usermodel.XWPFDocument();
                ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            doc.createParagraph().createRun().setText("Cotización de Tech SAS");
            var tabla = doc.createTable(2, 3);
            String[][] celdas = { { "Producto", "Cantidad", "Precio" }, { "Lápiz HB", "48", "480" } };
            for (int f = 0; f < 2; f++) {
                for (int c = 0; c < 3; c++) {
                    tabla.getRow(f).getCell(c).setText(celdas[f][c]);
                }
            }
            doc.write(out);
            docx = out.toByteArray();
        }

        String texto = LectorArchivos.wordATexto(docx);

        assertTrue(texto.startsWith("Cotización de Tech SAS"));
        assertTrue(texto.contains("Producto | Cantidad | Precio"));
        assertTrue(texto.contains("Lápiz HB | 48 | 480"));
        assertTrue(new ArchivoIA(docx, "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "c.docx").esWord());
        assertFalse(new ArchivoIA(docx, "application/pdf", "c.pdf").esWord());
        assertThrows(IAException.class, () -> LectorArchivos.wordATexto("no es un docx".getBytes()));
    }
}
