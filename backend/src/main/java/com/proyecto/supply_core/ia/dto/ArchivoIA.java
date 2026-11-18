package com.proyecto.supply_core.ia.dto;

/**
 * Archivo que se entrega a la IA.
 *
 * @param contenido bytes del archivo
 * @param tipoMime  tipo real detectado al subirlo (application/pdf, image/png...)
 * @param nombre    nombre original (solo informativo)
 */
public record ArchivoIA(byte[] contenido, String tipoMime, String nombre) {

    /**
     * Indica si es un PDF.
     *
     * @return {@code true} para application/pdf
     */
    public boolean esPdf() {
        return "application/pdf".equals(tipoMime);
    }

    /**
     * Indica si es una imagen.
     *
     * @return {@code true} para image/*
     */
    public boolean esImagen() {
        return tipoMime != null && tipoMime.startsWith("image/");
    }

    /**
     * Indica si es un documento de Word.
     *
     * @return {@code true} para Word moderno (docx)
     */
    public boolean esWord() {
        return tipoMime != null && tipoMime.contains("wordprocessingml");
    }

    /**
     * Indica si es una hoja de cálculo.
     *
     * @return {@code true} para Excel (xlsx o xls)
     */
    public boolean esExcel() {
        return tipoMime != null && (tipoMime.contains("spreadsheet") || tipoMime.contains("ms-excel"));
    }
}
