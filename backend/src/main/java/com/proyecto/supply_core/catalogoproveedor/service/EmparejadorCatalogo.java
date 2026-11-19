package com.proyecto.supply_core.catalogoproveedor.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import com.proyecto.supply_core.inventario.entity.Producto;

/**
 * Busca a qué producto del inventario corresponde un artículo del catálogo de un proveedor. Es Java puro y
 * predecible: el código exacto (o el código de barras) gana; si no, se compara el parecido de las palabras de la
 * descripción y la marca con el nombre del producto. La persona confirma; nunca se compra sin confirmar.
 * <p>Los casos dudosos (similitud entre {@value #UMBRAL_SUGERENCIA} y {@value #UMBRAL_SEGURO}) son los que una IA de
 * bajo costo podría afinar más adelante (la IA explica; Java decide qué se propone).</p>
 */
public final class EmparejadorCatalogo {

    /** Desde esta similitud se propone el producto (queda SUGERIDA para que compras confirme). */
    public static final double UMBRAL_SUGERENCIA = 0.45;
    /** Desde esta similitud la propuesta es muy probable. */
    public static final double UMBRAL_SEGURO = 0.8;
    /** Palabras que no ayudan a distinguir productos. */
    private static final Set<String> VACIAS = Set.of("de", "la", "el", "los", "las", "en", "y", "para", "con", "por",
            "un", "una", "und", "unidad", "unidades", "x", "a", "al", "del");

    private EmparejadorCatalogo() {
    }

    /**
     * Mejor candidato y su similitud.
     *
     * @param producto  producto del inventario ({@code null} si ninguno se parece)
     * @param similitud de 0 a 1
     */
    public record Candidato(Producto producto, BigDecimal similitud) {
    }

    /**
     * @param codigoProveedor código del artículo en el catálogo
     * @param descripcion     descripción del artículo
     * @param marca           marca, opcional
     * @param productos       productos activos de la empresa
     * @return el producto más parecido (o {@code null} si ninguno pasa {@value #UMBRAL_SUGERENCIA}) y su similitud
     */
    public static Candidato mejor(String codigoProveedor, String descripcion, String marca, List<Producto> productos) {
        String codigo = codigoProveedor == null ? "" : codigoProveedor.trim();
        for (Producto p : productos) {
            if (codigo.equalsIgnoreCase(p.getCodigo()) || (p.getCodigoBarras() != null && codigo.equals(p.getCodigoBarras()))) {
                return new Candidato(p, BigDecimal.ONE.setScale(2));
            }
        }
        Set<String> palabras = palabras(descripcion + " " + (marca == null ? "" : marca));
        Producto mejor = null;
        double mejorSimilitud = 0;
        for (Producto p : productos) {
            double s = similitud(palabras, palabras(p.getNombre() + " " + p.getCodigo()));
            if (s > mejorSimilitud) {
                mejorSimilitud = s;
                mejor = p;
            }
        }
        BigDecimal similitud = BigDecimal.valueOf(mejorSimilitud).setScale(2, RoundingMode.HALF_UP);
        return mejorSimilitud >= UMBRAL_SUGERENCIA ? new Candidato(mejor, similitud) : new Candidato(null, similitud);
    }

    /**
     * Parecido entre dos conjuntos de palabras: las que comparten sobre las del más corto (así "Cable THHN 12 AWG"
     * se parece mucho a "Cable THHN 12 AWG rollo 100 m negro").
     *
     * @param a palabras de un texto
     * @param b palabras del otro
     * @return de 0 a 1
     */
    static double similitud(Set<String> a, Set<String> b) {
        if (a.isEmpty() || b.isEmpty()) {
            return 0;
        }
        Set<String> comunes = new HashSet<>(a);
        comunes.retainAll(b);
        return (double) comunes.size() / Math.min(a.size(), b.size());
    }

    /**
     * Palabras de un texto sin tildes, en minúsculas, sin signos y sin palabras vacías. Separa números pegados a
     * letras ("12awg" queda "12" y "awg") para que coincidan escritos de una u otra forma.
     *
     * @param texto texto libre
     * @return palabras útiles
     */
    static Set<String> palabras(String texto) {
        if (texto == null) {
            return Set.of();
        }
        String limpio = Normalizer.normalize(texto.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replaceAll("(\\d)([a-z])", "$1 $2").replaceAll("([a-z])(\\d)", "$1 $2")
                .replaceAll("[^a-z0-9]+", " ");
        return Arrays.stream(limpio.trim().split("\\s+")).filter(w -> !w.isEmpty() && !VACIAS.contains(w))
                .collect(Collectors.toSet());
    }
}
