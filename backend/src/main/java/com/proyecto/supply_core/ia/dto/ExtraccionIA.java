package com.proyecto.supply_core.ia.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/**
 * Lo que la IA lee de una cotización (esquema de salida estructurada). Los valores van como texto tal
 * cual aparecen ("12.000,50", "30 días"); Java los interpreta y valida. Cadena vacía = no aparece.
 *
 * @param moneda            moneda (COP, USD...) o vacío
 * @param plazoEntregaDias  plazo de entrega en días o vacío
 * @param garantiaMeses     garantía en meses o vacío
 * @param validezHasta      fecha de validez de la oferta (AAAA-MM-DD) o vacío
 * @param condiciones       condiciones comerciales resumidas o vacío
 * @param confianzaCabecera confianza 0–1 en los datos de cabecera
 * @param lineas            renglones cotizados
 * @param totalImpreso      total final impreso en el documento (con impuestos), tal cual aparece, o vacío
 */
public record ExtraccionIA(
        @JsonPropertyDescription("Código ISO de la moneda (COP, USD) o cadena vacía si no aparece")
        String moneda,
        @JsonPropertyDescription("Plazo de entrega en días tal como aparece, o cadena vacía")
        String plazoEntregaDias,
        @JsonPropertyDescription("Garantía en meses tal como aparece, o cadena vacía")
        String garantiaMeses,
        @JsonPropertyDescription("Fecha de validez de la oferta en formato AAAA-MM-DD, o cadena vacía")
        String validezHasta,
        @JsonPropertyDescription("Condiciones comerciales (pago, transporte) resumidas en una frase, o cadena vacía")
        String condiciones,
        @JsonPropertyDescription("Confianza de 0 a 1 en los datos de cabecera")
        double confianzaCabecera,
        @JsonPropertyDescription("Un elemento por cada producto cotizado en el documento")
        List<LineaExtraida> lineas,
        @JsonPropertyDescription("Total final a pagar impreso en el documento (con impuestos), copiado tal como aparece, "
                + "por ejemplo '$ 5.236.000'. No lo calcules sumando: si el documento no trae un total, cadena vacía")
        String totalImpreso) {

    /**
     * Lectura sin total impreso (compatibilidad con quien no lo informa).
     *
     * @param moneda            moneda
     * @param plazoEntregaDias  plazo de entrega
     * @param garantiaMeses     garantía
     * @param validezHasta      validez
     * @param condiciones       condiciones
     * @param confianzaCabecera confianza de la cabecera
     * @param lineas            renglones
     */
    public ExtraccionIA(String moneda, String plazoEntregaDias, String garantiaMeses, String validezHasta,
            String condiciones, double confianzaCabecera, List<LineaExtraida> lineas) {
        this(moneda, plazoEntregaDias, garantiaMeses, validezHasta, condiciones, confianzaCabecera, lineas, "");
    }

    /**
     * Renglón cotizado.
     *
     * @param rfqLineaId           id de la línea de la RFQ a la que corresponde (0 si no corresponde a ninguna)
     * @param descripcion          descripción tal como aparece en el documento
     * @param cantidad             cantidad ofertada
     * @param unidad               presentación ofertada ("caja x24")
     * @param factorConversion     unidades de la RFQ por cada unidad ofertada
     * @param precioUnitario       precio de una unidad ofertada sin impuesto
     * @param descuentoPct         descuento en %
     * @param impuestoPct          impuesto en %
     * @param confianzaCantidad    confianza 0–1 en la cantidad
     * @param confianzaUnidad      confianza 0–1 en la unidad y el factor
     * @param confianzaPrecio      confianza 0–1 en el precio
     */
    public record LineaExtraida(
            @JsonPropertyDescription("Id numérico de la línea de la RFQ que corresponde a este renglón; 0 si ninguna")
            long rfqLineaId,
            @JsonPropertyDescription("Descripción del producto tal como aparece en el documento")
            String descripcion,
            @JsonPropertyDescription("Cantidad ofertada tal como aparece")
            String cantidad,
            @JsonPropertyDescription("Presentación ofertada tal como aparece, p. ej. 'caja x24' o 'und'")
            String unidad,
            @JsonPropertyDescription("Cuántas unidades de la RFQ trae cada unidad ofertada (caja x24 frente a UND = 24); '1' si es la misma")
            String factorConversion,
            @JsonPropertyDescription("Precio de una unidad ofertada SIN impuesto, tal como aparece")
            String precioUnitario,
            @JsonPropertyDescription("Descuento en porcentaje, o '0'")
            String descuentoPct,
            @JsonPropertyDescription("Impuesto (IVA) en porcentaje, o '0'")
            String impuestoPct,
            @JsonPropertyDescription("Confianza de 0 a 1 en la cantidad")
            double confianzaCantidad,
            @JsonPropertyDescription("Confianza de 0 a 1 en la unidad y el factor de conversión")
            double confianzaUnidad,
            @JsonPropertyDescription("Confianza de 0 a 1 en el precio")
            double confianzaPrecio) {
    }
}
