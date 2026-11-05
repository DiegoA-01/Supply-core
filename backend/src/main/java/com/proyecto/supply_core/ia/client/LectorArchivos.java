package com.proyecto.supply_core.ia.client;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

/**
 * Convierte archivos a lo que cada IA puede leer: Excel a texto tabulado (ninguna IA lo lee directo),
 * PDF a texto (si es digital) o a imágenes por página (si es escaneado, para modelos locales).
 */
public final class LectorArchivos {

    /** Máximo de páginas que se convierten a imagen (los modelos locales son lentos). */
    static final int MAX_PAGINAS_IMAGEN = 3;
    /** Máximo de filas por hoja que se envían a la IA. */
    private static final int MAX_FILAS = 500;

    /** Clase de utilidades: no se instancia. */
    private LectorArchivos() {
    }

    /**
     * Excel (xlsx o xls) a texto: una fila por línea, celdas separadas por " | ".
     *
     * @param contenido bytes del archivo
     * @return texto de todas las hojas
     * @throws IAException si el archivo no se puede leer
     */
    public static String excelATexto(byte[] contenido) {
        DataFormatter formato = new DataFormatter();
        StringBuilder sb = new StringBuilder();
        try (Workbook libro = WorkbookFactory.create(new ByteArrayInputStream(contenido))) {
            for (Sheet hoja : libro) {
                sb.append("Hoja: ").append(hoja.getSheetName()).append('\n');
                int filas = 0;
                for (Row fila : hoja) {
                    if (++filas > MAX_FILAS) {
                        break;
                    }
                    List<String> celdas = new ArrayList<>();
                    for (Cell celda : fila) {
                        celdas.add(formato.formatCellValue(celda).trim());
                    }
                    if (celdas.stream().anyMatch(c -> !c.isEmpty())) {
                        sb.append(String.join(" | ", celdas)).append('\n');
                    }
                }
            }
        } catch (IOException | RuntimeException e) {
            throw new IAException("No se pudo leer el archivo de Excel: " + e.getMessage(), e);
        }
        return sb.toString();
    }

    /**
     * Texto de un PDF digital (vacío si es escaneado).
     *
     * @param contenido bytes del PDF
     * @return texto extraído
     * @throws IAException si el PDF no se puede abrir
     */
    public static String pdfATexto(byte[] contenido) {
        try (PDDocument pdf = Loader.loadPDF(contenido)) {
            return new PDFTextStripper().getText(pdf).trim();
        } catch (IOException e) {
            throw new IAException("No se pudo abrir el PDF: " + e.getMessage(), e);
        }
    }

    /**
     * Primeras páginas de un PDF como imágenes PNG (para PDF escaneados en modelos locales).
     *
     * @param contenido bytes del PDF
     * @return una imagen PNG por página (máximo {@link #MAX_PAGINAS_IMAGEN})
     * @throws IAException si el PDF no se puede abrir
     */
    public static List<byte[]> pdfAImagenes(byte[] contenido) {
        List<byte[]> paginas = new ArrayList<>();
        try (PDDocument pdf = Loader.loadPDF(contenido)) {
            PDFRenderer render = new PDFRenderer(pdf);
            for (int i = 0; i < Math.min(pdf.getNumberOfPages(), MAX_PAGINAS_IMAGEN); i++) {
                BufferedImage imagen = render.renderImageWithDPI(i, 150);
                ByteArrayOutputStream salida = new ByteArrayOutputStream();
                ImageIO.write(imagen, "png", salida);
                paginas.add(salida.toByteArray());
            }
        } catch (IOException e) {
            throw new IAException("No se pudo convertir el PDF a imagen: " + e.getMessage(), e);
        }
        return paginas;
    }
}
