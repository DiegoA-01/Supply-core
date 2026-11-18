package com.proyecto.supply_core.ia.service;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Convierte números escritos como aparecen en una cotización ("$ 12.000,50", "1,234.5", "19 %", "30 días")
 * a {@link BigDecimal}. La IA copia los valores tal cual y Java los interpreta: así un error de lectura no
 * se convierte en un número inventado.
 * <p>Regla de separadores: si hay coma y punto, el último es el decimal. Si hay uno solo y aparece una vez
 * seguido de exactamente 3 dígitos, es de miles ("12.000" = 12000, uso colombiano); si no, es decimal.</p>
 */
public final class NumeroTexto {

    /** Clase de utilidades: no se instancia. */
    private NumeroTexto() {
    }

    /**
     * Interpreta el texto.
     *
     * @param texto valor tal como lo leyó la IA
     * @return número, o vacío si no hay un número válido
     */
    public static Optional<BigDecimal> leer(String texto) {
        if (texto == null) {
            return Optional.empty();
        }
        String limpio = texto.replaceAll("[^0-9,.\\-]", "");
        if (limpio.isEmpty() || limpio.equals("-") || limpio.chars().noneMatch(Character::isDigit)) {
            return Optional.empty();
        }
        int coma = limpio.lastIndexOf(',');
        int punto = limpio.lastIndexOf('.');
        String normalizado;
        if (coma >= 0 && punto >= 0) {
            char decimal = coma > punto ? ',' : '.';
            char miles = decimal == ',' ? '.' : ',';
            normalizado = limpio.replace(String.valueOf(miles), "").replace(decimal, '.');
        } else if (coma >= 0 || punto >= 0) {
            char sep = coma >= 0 ? ',' : '.';
            long veces = limpio.chars().filter(c -> c == sep).count();
            int despues = limpio.length() - limpio.lastIndexOf(sep) - 1;
            boolean esMiles = veces > 1 || despues == 3;
            normalizado = esMiles ? limpio.replace(String.valueOf(sep), "") : limpio.replace(sep, '.');
        } else {
            normalizado = limpio;
        }
        try {
            return Optional.of(new BigDecimal(normalizado));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }
}
