package com.proyecto.supply_core.ia.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.List;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import com.proyecto.supply_core.ia.dto.ArchivoIA;
import com.proyecto.supply_core.ia.dto.ExtraccionIA;
import com.proyecto.supply_core.ia.dto.LineaContexto;
import com.proyecto.supply_core.ia.service.NumeroTexto;

/**
 * Prueba contra la API real de Gemini. NO corre sola: gasta cuota y envía un PDF de ejemplo (datos inventados) a Google.
 * Para correrla: definir {@code GEMINI_API_KEY} y {@code GEMINI_PRUEBA_VIVA=1}
 * ({@code ./mvnw test -Dtest=GeminiIAClientEnVivoTest}).
 */
@EnabledIfEnvironmentVariable(named = "GEMINI_PRUEBA_VIVA", matches = "1")
class GeminiIAClientEnVivoTest {

    @Test
    void leeUnPdfDeEjemploYDevuelveElTotalImpresoYLosRenglones() throws Exception {
        String modelo = System.getenv().getOrDefault("GEMINI_MODELO", "gemini-2.5-flash");
        GeminiIAClient cliente = new GeminiIAClient(System.getenv("GEMINI_API_KEY"), modelo, 120);

        ExtraccionIA r = cliente.extraer(new ArchivoIA(pdfDeEjemplo(), "application/pdf", "ejemplo.pdf"), List.of(
                new LineaContexto(500L, "LAP", "Lápiz HB", new BigDecimal("48"), "UND", "Caja x12"),
                new LineaContexto(501L, "BOR", "Borrador blanco", new BigDecimal("24"), "UND", null)));

        assertFalse(r.lineas().isEmpty());
        var lapiz = r.lineas().stream().filter(l -> l.rfqLineaId() == 500L).findFirst().orElseThrow();
        assertEquals(0, new BigDecimal("48").compareTo(NumeroTexto.leer(lapiz.cantidad()).orElseThrow()));
        assertEquals(0, new BigDecimal("480").compareTo(NumeroTexto.leer(lapiz.precioUnitario()).orElseThrow()));
        assertEquals(0, new BigDecimal("45981.60").compareTo(NumeroTexto.leer(r.totalImpreso()).orElseThrow()),
                "total impreso leído: " + r.totalImpreso());
        assertTrue(r.confianzaCabecera() >= 0);
    }

    /**
     * Cotización de ejemplo: 48 lápices a 480 (23.040) y 24 borradores a 650 (15.600); subtotal 38.640, IVA 19 % de
     * 7.341,60 y total impreso de 45.981,60.
     *
     * @return bytes del PDF
     */
    private static byte[] pdfDeEjemplo() throws Exception {
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage pagina = new PDPage();
            doc.addPage(pagina);
            PDType1Font fuente = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            String[] lineas = { "COTIZACION - Papeleria El Sol SAS", "Plazo de entrega: 3 dias. Pago a 30 dias.",
                    "Producto | Cantidad | Precio unit. | IVA | Subtotal", "Lapiz HB | 48 | 480 | 19% | 23.040",
                    "Borrador blanco | 24 | 650 | 19% | 15.600", "Subtotal: 38.640", "IVA 19%: 7.341,60",
                    "TOTAL A PAGAR: $ 45.981,60" };
            try (PDPageContentStream c = new PDPageContentStream(doc, pagina)) {
                c.beginText();
                c.setFont(fuente, 12);
                c.setLeading(18);
                c.newLineAtOffset(60, 720);
                for (String l : lineas) {
                    c.showText(l);
                    c.newLine();
                }
                c.endText();
            }
            doc.save(out);
            return out.toByteArray();
        }
    }
}
