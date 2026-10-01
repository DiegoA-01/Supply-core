package com.proyecto.supply_core.usuario.validation;

import java.util.Locale;

/** Normalización única de códigos de rol, usada por validadores y Service. */
public final class CodigosRol {

    /** Clase utilitaria: no se instancia. */
    private CodigosRol() {
    }

    /**
     * Quita espacios y pasa a mayúsculas ({@code " comprador "} → {@code "COMPRADOR"}).
     *
     * @param codigo código recibido (no {@code null})
     * @return código normalizado
     */
    public static String normalizar(String codigo) {
        return codigo.trim().toUpperCase(Locale.ROOT);
    }
}
