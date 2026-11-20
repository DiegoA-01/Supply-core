package com.proyecto.supply_core.exportacion.generador;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Component;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.proyecto.supply_core.empresa.dto.EmpresaResponse;

/**
 * PDF de reportes en forma de tablas (spec 009): el título, la empresa y una o varias secciones con su tabla. Recibe
 * las filas ya calculadas por el servicio del reporte, igual que el Excel.
 */
@Component
public class TablaPdfGenerador {

    private static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final Locale COLOMBIA = Locale.of("es", "CO");
    private static final Font TITULO = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14);
    private static final Font SUBTITULO = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10);
    private static final Font NORMAL = FontFactory.getFont(FontFactory.HELVETICA, 8);
    private static final Font NEGRITA = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8);
    private static final Font PIE = FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 7, Color.GRAY);
    private static final Color GRIS = new Color(230, 230, 230);

    /**
     * Una tabla del reporte.
     *
     * @param titulo   título de la sección
     * @param columnas encabezados
     * @param filas    valores (texto, número, fecha o {@code null})
     */
    public record Seccion(String titulo, List<String> columnas, List<List<Object>> filas) {
    }

    /**
     * @param titulo      título del reporte
     * @param periodo     texto del periodo
     * @param empresa     empresa
     * @param generadoPor usuario que lo pidió
     * @param secciones   tablas en orden
     * @return bytes del PDF (horizontal, para que quepan las columnas)
     */
    public byte[] generar(String titulo, String periodo, EmpresaResponse empresa, String generadoPor,
            List<Seccion> secciones) {
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4.rotate(), 28, 28, 28, 28);
        try {
            PdfWriter.getInstance(doc, salida);
            doc.addTitle(titulo);
            doc.addCreator("Supply-Core");
            doc.open();
            doc.add(new Paragraph(empresa.razonSocial() + " — " + titulo, TITULO));
            doc.add(new Paragraph(periodo, NORMAL));
            for (Seccion s : secciones) {
                Paragraph p = new Paragraph(s.titulo(), SUBTITULO);
                p.setSpacingBefore(10);
                p.setSpacingAfter(4);
                doc.add(p);
                if (s.filas().isEmpty()) {
                    doc.add(new Paragraph("Sin datos en el periodo.", NORMAL));
                    continue;
                }
                PdfPTable t = new PdfPTable(s.columnas().size());
                t.setWidthPercentage(100);
                t.setHeaderRows(1);
                for (String c : s.columnas()) {
                    PdfPCell celda = new PdfPCell(new Phrase(c, NEGRITA));
                    celda.setBackgroundColor(GRIS);
                    t.addCell(celda);
                }
                for (List<Object> fila : s.filas()) {
                    for (Object v : fila) {
                        PdfPCell celda = new PdfPCell(new Phrase(texto(v), NORMAL));
                        if (v instanceof BigDecimal || v instanceof Number) {
                            celda.setHorizontalAlignment(Element.ALIGN_RIGHT);
                        }
                        t.addCell(celda);
                    }
                }
                doc.add(t);
            }
            Paragraph pie = new Paragraph("Generado el " + LocalDateTime.now().format(FECHA_HORA) + " por " + generadoPor
                    + ". Cifras calculadas por Supply-Core.", PIE);
            pie.setSpacingBefore(14);
            doc.add(pie);
        } catch (DocumentException e) {
            throw new IllegalStateException("No se pudo generar el PDF del reporte", e);
        } finally {
            if (doc.isOpen()) {
                doc.close();
            }
        }
        return salida.toByteArray();
    }

    /**
     * @param v valor
     * @return texto para la celda
     */
    static String texto(Object v) {
        if (v == null) {
            return "";
        }
        if (v instanceof BigDecimal b) {
            return new DecimalFormat("#,##0.##", DecimalFormatSymbols.getInstance(COLOMBIA)).format(b);
        }
        if (v instanceof LocalDateTime f) {
            return f.format(FECHA_HORA);
        }
        if (v instanceof LocalDate f) {
            return f.toString();
        }
        return String.valueOf(v);
    }
}
