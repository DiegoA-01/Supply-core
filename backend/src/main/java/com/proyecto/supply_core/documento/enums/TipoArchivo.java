package com.proyecto.supply_core.documento.enums;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Tipos de archivo aceptados, con su firma ("magic bytes"). El tipo se decide por la extensión
 * Y se confirma leyendo los primeros bytes del contenido: no se confía en el Content-Type que
 * envía el navegador (un .exe renombrado a .pdf se rechaza).
 */
public enum TipoArchivo {
    /** PDF: empieza por {@code %PDF}. */
    PDF("application/pdf", Set.of("pdf"), bytes("%PDF")),
    /** Excel moderno (ZIP): empieza por {@code PK\3\4}. */
    XLSX("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", Set.of("xlsx"),
            new byte[] { 0x50, 0x4B, 0x03, 0x04 }),
    /** Excel 97-2003 (OLE2). */
    XLS("application/vnd.ms-excel", Set.of("xls"),
            new byte[] { (byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0, (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1 }),
    /** Imagen PNG. */
    PNG("image/png", Set.of("png"), new byte[] { (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A }),
    /** Imagen JPEG. */
    JPEG("image/jpeg", Set.of("jpg", "jpeg"), new byte[] { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF }),
    /** Imagen WebP: {@code RIFF????WEBP} (la firma completa se revisa en {@link #coincide}). */
    WEBP("image/webp", Set.of("webp"), bytes("RIFF"));

    /** Bytes que hay que leer como máximo para reconocer cualquier tipo. */
    public static final int BYTES_FIRMA = 12;

    private final String mime;
    private final Set<String> extensiones;
    private final byte[] firma;

    /**
     * @param mime        tipo MIME canónico que se guarda y se devuelve al descargar
     * @param extensiones extensiones aceptadas (minúsculas, sin punto)
     * @param firma       primeros bytes esperados
     */
    TipoArchivo(String mime, Set<String> extensiones, byte[] firma) {
        this.mime = mime;
        this.extensiones = extensiones;
        this.firma = firma;
    }

    /**
     * Tipo MIME canónico.
     *
     * @return p. ej. {@code application/pdf}
     */
    public String mime() {
        return mime;
    }

    /**
     * Extensión que se usa al guardar en disco.
     *
     * @return primera extensión del tipo (p. ej. {@code jpg})
     */
    public String extensionPrincipal() {
        return extensiones.stream().sorted().findFirst().orElseThrow();
    }

    /**
     * Busca el tipo por la extensión del nombre de archivo.
     *
     * @param nombreArchivo nombre original (p. ej. {@code cotizacion.PDF})
     * @return tipo aceptado o vacío si la extensión no está permitida
     */
    public static Optional<TipoArchivo> porNombre(String nombreArchivo) {
        if (nombreArchivo == null || !nombreArchivo.contains(".")) {
            return Optional.empty();
        }
        String ext = nombreArchivo.substring(nombreArchivo.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        return Arrays.stream(values()).filter(t -> t.extensiones.contains(ext)).findFirst();
    }

    /**
     * Verifica que el contenido real corresponda a este tipo.
     *
     * @param cabecera primeros bytes del archivo (hasta {@link #BYTES_FIRMA})
     * @return {@code true} si la firma coincide
     */
    public boolean coincide(byte[] cabecera) {
        if (cabecera.length < firma.length) {
            return false;
        }
        for (int i = 0; i < firma.length; i++) {
            if (cabecera[i] != firma[i]) {
                return false;
            }
        }
        if (this == WEBP) { // RIFF <4 bytes de tamaño> WEBP
            return cabecera.length >= BYTES_FIRMA
                    && new String(cabecera, 8, 4, StandardCharsets.US_ASCII).equals("WEBP");
        }
        return true;
    }

    /**
     * Texto con las extensiones aceptadas, para mensajes de error.
     *
     * @return p. ej. {@code jpeg, jpg, pdf, png, webp, xls, xlsx}
     */
    public static String extensionesAceptadas() {
        return String.join(", ", Arrays.stream(values()).flatMap(t -> t.extensiones.stream()).sorted().toList());
    }

    /**
     * Convierte un texto ASCII en bytes (para firmas legibles).
     *
     * @param texto firma
     * @return bytes
     */
    private static byte[] bytes(String texto) {
        return texto.getBytes(StandardCharsets.US_ASCII);
    }
}
