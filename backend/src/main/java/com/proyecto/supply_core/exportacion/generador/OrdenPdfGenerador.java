package com.proyecto.supply_core.exportacion.generador;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.proyecto.supply_core.compra.orden.dto.OrdenResponse;
import com.proyecto.supply_core.empresa.dto.EmpresaResponse;

/**
 * PDF de una orden de compra con el encabezado de la empresa (OpenPDF).
 * <p>Los montos salen tal como están guardados en la orden: aquí no se recalcula nada (regla de oro 1).</p>
 */
@Component
public class OrdenPdfGenerador {

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final Locale COLOMBIA = Locale.of("es", "CO");

    private static final Font TITULO = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 15);
    private static final Font SUBTITULO = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11);
    private static final Font NORMAL = FontFactory.getFont(FontFactory.HELVETICA, 9);
    private static final Font NEGRITA = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9);
    private static final Font PIE = FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 8, Color.GRAY);
    private static final Color GRIS = new Color(230, 230, 230);

    /**
     * Genera el PDF.
     *
     * @param empresa     empresa emisora (encabezado)
     * @param orden       orden con sus líneas
     * @param generadoPor nombre de quien exporta
     * @return bytes del PDF
     * @throws IllegalStateException si OpenPDF no puede armar el documento
     */
    public byte[] generar(EmpresaResponse empresa, OrdenResponse orden, String generadoPor) {
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 36, 36, 36, 36);
        try {
            PdfWriter.getInstance(doc, salida);
            doc.addTitle("Orden de compra " + orden.consecutivo());
            doc.addCreator("Supply-Core");
            doc.open();

            doc.add(new Paragraph(empresa.razonSocial(), TITULO));
            doc.add(new Paragraph("NIT " + empresa.nit(), NORMAL));
            if (empresa.direccionPrincipal() != null) {
                doc.add(new Paragraph(empresa.direccionPrincipal(), NORMAL));
            }
            Paragraph titulo = new Paragraph("Orden de compra " + orden.consecutivo() + " — versión " + orden.version(),
                    SUBTITULO);
            titulo.setSpacingBefore(12);
            titulo.setSpacingAfter(8);
            doc.add(titulo);

            doc.add(datosGenerales(orden));
            doc.add(lineas(orden, empresa.monedaBase()));
            doc.add(totales(orden, empresa.monedaBase()));

            Paragraph pie = new Paragraph("Generado el " + LocalDateTime.now().format(FECHA_HORA) + " por " + generadoPor
                    + ". Documento informativo: la orden vigente es la que figura en Supply-Core.", PIE);
            pie.setSpacingBefore(18);
            doc.add(pie);
        } catch (DocumentException e) {
            throw new IllegalStateException("No se pudo generar el PDF de la orden", e);
        } finally {
            if (doc.isOpen()) {
                doc.close();
            }
        }
        return salida.toByteArray();
    }

    /**
     * Tabla de dos columnas con proveedor, estado, entrega y origen de la orden.
     *
     * @param o orden
     * @return tabla
     */
    private static PdfPTable datosGenerales(OrdenResponse o) {
        PdfPTable t = new PdfPTable(new float[] { 1.3f, 3f });
        t.setWidthPercentage(100);
        t.setSpacingAfter(10);
        par(t, "Proveedor", o.proveedorRazonSocial());
        par(t, "Estado", o.estado().name());
        par(t, "Emitida", o.creadoEn() == null ? "" : o.creadoEn().format(FECHA_HORA));
        par(t, "Bodega de destino", o.bodegaDestinoNombre());
        par(t, "Lugar de entrega", o.lugarEntrega());
        par(t, "Entrega esperada", o.fechaEntregaEsperada() == null ? "" : o.fechaEntregaEsperada().format(FECHA));
        if (o.version() > 1 && o.motivoVersion() != null) {
            par(t, "Motivo de la versión", o.motivoVersion());
        }
        return t;
    }

    /**
     * Tabla de líneas: código, producto, unidad, cantidad, precio, IVA y subtotal.
     *
     * @param o      orden
     * @param moneda moneda de la empresa
     * @return tabla
     */
    private static PdfPTable lineas(OrdenResponse o, String moneda) {
        PdfPTable t = new PdfPTable(new float[] { 1.2f, 3.2f, 0.8f, 1.1f, 1.5f, 0.7f, 1.6f });
        t.setWidthPercentage(100);
        t.setHeaderRows(1);
        for (String c : new String[] { "Código", "Producto", "Unidad", "Cantidad", "Precio unit. (" + moneda + ")",
                "IVA %", "Subtotal (" + moneda + ")" }) {
            PdfPCell celda = new PdfPCell(new Phrase(c, NEGRITA));
            celda.setBackgroundColor(GRIS);
            t.addCell(celda);
        }
        for (OrdenResponse.Linea l : o.lineas()) {
            t.addCell(new Phrase(l.productoCodigo(), NORMAL));
            t.addCell(new Phrase(l.productoNombre(), NORMAL));
            t.addCell(new Phrase(l.unidad(), NORMAL));
            t.addCell(numero(l.cantidad()));
            t.addCell(numero(l.precioUnitario()));
            t.addCell(numero(l.impuestoPct()));
            t.addCell(numero(l.subtotal()));
        }
        return t;
    }

    /**
     * Totales guardados en la orden, alineados a la derecha.
     *
     * @param o      orden
     * @param moneda moneda de la empresa
     * @return tabla
     */
    private static PdfPTable totales(OrdenResponse o, String moneda) {
        PdfPTable t = new PdfPTable(new float[] { 1.5f, 1.6f });
        t.setWidthPercentage(40);
        t.setHorizontalAlignment(Element.ALIGN_RIGHT);
        t.setSpacingBefore(6);
        par(t, "Subtotal", formato(o.subtotal()) + " " + moneda);
        par(t, "Impuestos", formato(o.impuestos()) + " " + moneda);
        par(t, "Total", formato(o.total()) + " " + moneda);
        return t;
    }

    /**
     * Agrega una fila etiqueta / valor sin bordes.
     *
     * @param t     tabla
     * @param clave etiqueta
     * @param valor valor ({@code null} se muestra vacío)
     */
    private static void par(PdfPTable t, String clave, String valor) {
        PdfPCell k = new PdfPCell(new Phrase(clave, NEGRITA));
        PdfPCell v = new PdfPCell(new Phrase(valor == null ? "" : valor, NORMAL));
        k.setBorder(Rectangle.NO_BORDER);
        v.setBorder(Rectangle.NO_BORDER);
        t.addCell(k);
        t.addCell(v);
    }

    /**
     * Celda numérica alineada a la derecha.
     *
     * @param n número
     * @return celda
     */
    private static PdfPCell numero(BigDecimal n) {
        PdfPCell c = new PdfPCell(new Phrase(formato(n), NORMAL));
        c.setHorizontalAlignment(Element.ALIGN_RIGHT);
        return c;
    }

    /**
     * Formato colombiano: punto de miles y coma decimal (1.234.567,50).
     *
     * @param n número ({@code null} = vacío)
     * @return texto
     */
    static String formato(BigDecimal n) {
        if (n == null) {
            return "";
        }
        DecimalFormat f = new DecimalFormat("#,##0.00##", DecimalFormatSymbols.getInstance(COLOMBIA));
        return f.format(n);
    }
}
