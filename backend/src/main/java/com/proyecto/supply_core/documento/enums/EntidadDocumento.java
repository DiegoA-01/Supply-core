package com.proyecto.supply_core.documento.enums;

/**
 * A qué tipo de registro pertenece un documento ({@code documento.entidad_tipo}, referencia polimórfica).
 * Cada tipo necesita una {@code ReglaAccesoDocumentos} registrada para poder subir o leer archivos.
 */
public enum EntidadDocumento {
    /** Documentos del proveedor (RUT, cámara de comercio, certificaciones). */
    PROVEEDOR,
    /** Archivo original de una cotización (Excel, PDF o imagen) — paso 12. */
    COTIZACION,
    /** Soportes de una orden de compra o evidencia de despacho — paso 15. */
    ORDEN_COMPRA,
    /** Comprobante de pago — paso 16. */
    PAGO,
    /** Evidencia fotográfica de la recepción — paso 17. */
    RECEPCION
}
