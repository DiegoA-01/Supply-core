package com.proyecto.supply_core.exportacion.generador;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.util.WorkbookUtil;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import com.proyecto.supply_core.empresa.dto.EmpresaResponse;

/**
 * Arma un libro de Excel de una sola hoja: encabezado de la empresa, título, quién y cuándo lo generó,
 * y una tabla con filtro. No calcula nada: escribe los valores tal como llegan.
 * <p>Los textos se escriben como texto (nunca como fórmula), así que un dato que empiece por {@code =} no se
 * ejecuta al abrir el archivo.</p>
 */
@Component
public class ExcelGenerador {

    /** Fila (base 0) donde va la cabecera de la tabla. */
    static final int FILA_CABECERA = 4;

    /** Ancho máximo de una columna, en caracteres; lo que sobre se ve al abrir la celda. */
    public static final int ANCHO_MAXIMO = 60;
    /** Ancho mínimo de una columna, en caracteres. */
    private static final int ANCHO_MINIMO = 12;
    /** Espacio extra en la cabecera para el botón del filtro. */
    private static final int MARGEN_CABECERA = 4;
    /** Espacio extra en los datos para que no queden pegados al borde. */
    private static final int MARGEN_DATO = 2;

    private static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DecimalFormat NUMERO = new DecimalFormat("#,##0.00##",
            DecimalFormatSymbols.getInstance(Locale.ROOT));

    /**
     * Genera el libro.
     *
     * @param titulo      título de la hoja (la pestaña usa una versión segura de hasta 31 caracteres)
     * @param empresa     empresa del usuario (encabezado)
     * @param generadoPor nombre de quien exporta
     * @param columnas    títulos de las columnas
     * @param filas       valores por fila, en el orden de {@code columnas}
     * @return bytes del .xlsx
     * @throws UncheckedIOException si no se puede escribir el libro en memoria
     */
    public byte[] generar(String titulo, EmpresaResponse empresa, String generadoPor, List<String> columnas,
            List<List<Object>> filas) {
        try (XSSFWorkbook libro = new XSSFWorkbook(); ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            Estilos estilos = new Estilos(libro);
            Sheet hoja = libro.createSheet(WorkbookUtil.createSafeSheetName(titulo));

            texto(hoja.createRow(0), 0, empresa.razonSocial() + " — NIT " + empresa.nit(), estilos.titulo);
            texto(hoja.createRow(1), 0, titulo, estilos.negrita);
            texto(hoja.createRow(2), 0, "Generado el " + LocalDateTime.now().format(FECHA_HORA) + " por " + generadoPor,
                    null);

            int[] anchos = new int[columnas.size()];
            Row cabecera = hoja.createRow(FILA_CABECERA);
            for (int c = 0; c < columnas.size(); c++) {
                texto(cabecera, c, columnas.get(c), estilos.cabecera);
                anchos[c] = Math.max(ANCHO_MINIMO, columnas.get(c).length() + MARGEN_CABECERA);
            }
            int r = FILA_CABECERA + 1;
            for (List<Object> fila : filas) {
                Row row = hoja.createRow(r++);
                for (int c = 0; c < fila.size(); c++) {
                    escribir(row.createCell(c), fila.get(c), estilos);
                    anchos[c] = Math.max(anchos[c], largoVisible(fila.get(c)) + MARGEN_DATO);
                }
            }
            for (int c = 0; c < anchos.length; c++) {
                hoja.setColumnWidth(c, Math.min(anchos[c], ANCHO_MAXIMO) * 256);
            }
            if (!columnas.isEmpty()) {
                hoja.setAutoFilter(new CellRangeAddress(FILA_CABECERA, Math.max(FILA_CABECERA, r - 1), 0,
                        columnas.size() - 1));
            }
            hoja.createFreezePane(0, FILA_CABECERA + 1);
            libro.write(salida);
            return salida.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo generar el Excel", e);
        }
    }

    /**
     * Escribe un texto en la celda indicada.
     *
     * @param fila   fila
     * @param col    columna
     * @param valor  texto
     * @param estilo estilo o {@code null}
     */
    private static void texto(Row fila, int col, String valor, CellStyle estilo) {
        Cell celda = fila.createCell(col);
        celda.setCellValue(valor);
        if (estilo != null) {
            celda.setCellStyle(estilo);
        }
    }

    /**
     * Caracteres que ocupa un valor al mostrarlo, para que la columna alcance: una fecha más ancha que su columna
     * se ve como {@code ####} y un texto se corta si la celda vecina tiene datos.
     *
     * @param valor valor de la celda
     * @return largo con el formato de la celda
     */
    private static int largoVisible(Object valor) {
        return switch (valor) {
            case null -> 0;
            case BigDecimal n -> ((DecimalFormat) NUMERO.clone()).format(n).length();
            case LocalDateTime f -> "yyyy-mm-dd hh:mm".length();
            case LocalDate f -> "yyyy-mm-dd".length();
            case Boolean b -> "No".length();
            default -> valor.toString().length();
        };
    }

    /**
     * Escribe un valor según su tipo: números y fechas con formato, booleanos como Sí/No, lo demás como texto.
     *
     * @param celda   celda destino
     * @param valor   valor ({@code null} deja la celda vacía)
     * @param estilos estilos del libro
     */
    private static void escribir(Cell celda, Object valor, Estilos estilos) {
        switch (valor) {
            case null -> celda.setBlank();
            case BigDecimal n -> {
                celda.setCellValue(n.doubleValue());
                celda.setCellStyle(estilos.numero);
            }
            case Number n -> celda.setCellValue(n.doubleValue());
            case LocalDateTime f -> {
                celda.setCellValue(f);
                celda.setCellStyle(estilos.fechaHora);
            }
            case LocalDate f -> {
                celda.setCellValue(f);
                celda.setCellStyle(estilos.fecha);
            }
            case Boolean b -> celda.setCellValue(b ? "Sí" : "No");
            default -> celda.setCellValue(valor.toString());
        }
    }

    /**
     * Estilos compartidos por todas las celdas del libro (Excel limita cuántos se pueden crear).
     */
    private static final class Estilos {
        private final CellStyle titulo;
        private final CellStyle negrita;
        private final CellStyle cabecera;
        private final CellStyle numero;
        private final CellStyle fecha;
        private final CellStyle fechaHora;

        /**
         * @param libro libro donde se registran los estilos
         */
        private Estilos(XSSFWorkbook libro) {
            Font grande = libro.createFont();
            grande.setBold(true);
            grande.setFontHeightInPoints((short) 14);
            titulo = libro.createCellStyle();
            titulo.setFont(grande);

            Font bold = libro.createFont();
            bold.setBold(true);
            negrita = libro.createCellStyle();
            negrita.setFont(bold);
            cabecera = libro.createCellStyle();
            cabecera.setFont(bold);
            cabecera.setBorderBottom(BorderStyle.THIN);

            short formatoNumero = libro.createDataFormat().getFormat("#,##0.00##");
            numero = libro.createCellStyle();
            numero.setDataFormat(formatoNumero);
            fecha = libro.createCellStyle();
            fecha.setDataFormat(libro.createDataFormat().getFormat("yyyy-mm-dd"));
            fechaHora = libro.createCellStyle();
            fechaHora.setDataFormat(libro.createDataFormat().getFormat("yyyy-mm-dd hh:mm"));
        }
    }
}
