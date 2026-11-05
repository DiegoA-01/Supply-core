package com.proyecto.supply_core.exportacion.enums;

import java.util.Locale;

import com.proyecto.supply_core.common.exception.FormatoNoSoportadoException;

/**
 * Formatos en que se puede exportar información, con su tipo MIME y extensión.
 */
public enum FormatoExportacion {

    /** Documento PDF. */
    PDF("application/pdf", "pdf"),
    /** Libro de Excel. */
    XLSX("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "xlsx");

    private final String contentType;
    private final String extension;

    /**
     * @param contentType tipo MIME
     * @param extension   extensión del archivo, sin punto
     */
    FormatoExportacion(String contentType, String extension) {
        this.contentType = contentType;
        this.extension = extension;
    }

    /**
     * Tipo MIME del archivo.
     *
     * @return p. ej. {@code application/pdf}
     */
    public String contentType() {
        return contentType;
    }

    /**
     * Extensión del archivo, sin punto.
     *
     * @return p. ej. {@code pdf}
     */
    public String extension() {
        return extension;
    }

    /**
     * Interpreta el parámetro {@code formato} sin distinguir mayúsculas.
     *
     * @param valor texto recibido ({@code pdf} o {@code xlsx})
     * @return formato
     * @throws FormatoNoSoportadoException si falta o no es un formato conocido
     */
    public static FormatoExportacion de(String valor) {
        if (valor == null || valor.isBlank()) {
            throw new FormatoNoSoportadoException("Indica el formato: pdf o xlsx");
        }
        try {
            return valueOf(valor.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new FormatoNoSoportadoException("Formato '" + valor + "' desconocido: usa pdf o xlsx");
        }
    }
}
