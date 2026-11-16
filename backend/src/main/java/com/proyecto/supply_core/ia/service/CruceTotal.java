package com.proyecto.supply_core.ia.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

/**
 * Cruce del total que la IA leyó impreso en la cotización contra el total que calcula el sistema con las líneas.
 * <p>Regla de oro 1: la IA solo copia el total que aparece en el documento (como texto); Java lo compara con la suma
 * de las líneas y decide. Si la diferencia pasa de {@link #TOLERANCIA_PCT} % del total impreso, la cotización queda
 * en revisión con la alerta {@link #ALERTA}: o la IA leyó mal un renglón, o el documento del proveedor no cuadra, o
 * alguien intentó engañar a la lectura con texto oculto en el PDF.</p>
 */
final class CruceTotal {

    /** Código de la alerta cuando el total impreso no coincide con el calculado. */
    static final String ALERTA = "TOTAL_NO_CUADRA";
    /** Diferencia máxima aceptada, en porcentaje del total impreso. */
    static final BigDecimal TOLERANCIA_PCT = BigDecimal.ONE;

    /** Clase de utilidades: no se instancia. */
    private CruceTotal() {
    }

    /**
     * Resultado del cruce.
     *
     * @param hayTotal      {@code true} si el documento trajo un total impreso que se pudo leer
     * @param cuadra        {@code true} si no hay total impreso (nada que cruzar) o la diferencia está dentro de la
     *                      tolerancia
     * @param impreso       total impreso, si se pudo leer
     * @param calculado     total calculado por el sistema (0 si no hay líneas)
     * @param diferenciaPct diferencia absoluta en % del total impreso (0 si no hay total impreso)
     */
    record Resultado(boolean hayTotal, boolean cuadra, BigDecimal impreso, BigDecimal calculado,
            BigDecimal diferenciaPct) {

        /**
         * Texto de la alerta para el comprador.
         *
         * @return mensaje que empieza por el código {@link CruceTotal#ALERTA}
         */
        String mensaje() {
            return ALERTA + ": el total impreso en el documento (" + impreso.setScale(2, RoundingMode.HALF_UP)
                    + ") no coincide con el calculado por el sistema (" + calculado.setScale(2, RoundingMode.HALF_UP)
                    + "), diferencia de " + diferenciaPct + " %. Revise los renglones contra el original";
        }
    }

    /**
     * Compara el total impreso con el calculado.
     *
     * @param calculado    total de la cotización según el sistema ({@code null} si no tiene líneas)
     * @param impresoTexto total tal como lo copió la IA ({@code null} o vacío si el documento no lo trae)
     * @return resultado del cruce
     */
    static Resultado evaluar(BigDecimal calculado, String impresoTexto) {
        BigDecimal propio = calculado == null ? BigDecimal.ZERO : calculado;
        Optional<BigDecimal> impreso = NumeroTexto.leer(impresoTexto).filter(v -> v.signum() > 0);
        if (impreso.isEmpty()) {
            return new Resultado(false, true, null, propio, BigDecimal.ZERO);
        }
        BigDecimal pct = impreso.get().subtract(propio).abs().multiply(BigDecimal.valueOf(100))
                .divide(impreso.get(), 2, RoundingMode.HALF_UP);
        return new Resultado(true, pct.compareTo(TOLERANCIA_PCT) <= 0, impreso.get(), propio, pct);
    }
}
