package com.proyecto.supply_core.usuario.validation;

/**
 * Reglas de contraseña compartidas por todos los requests (constantes para usar en anotaciones).
 * El máximo es 72 porque BCrypt solo procesa los primeros 72 bytes.
 */
public final class ReglasPassword {

    /** Largo mínimo. */
    public static final int MINIMO = 8;
    /** Largo máximo (límite de BCrypt). */
    public static final int MAXIMO = 72;
    /** Al menos una letra y un número. */
    public static final String PATRON = "^(?=.*[A-Za-z])(?=.*\\d).+$";
    /** Mensaje del largo. */
    public static final String MENSAJE_LARGO = "debe tener entre 8 y 72 caracteres";
    /** Mensaje del patrón. */
    public static final String MENSAJE_PATRON = "debe tener al menos una letra y un número";

    /** Clase de constantes: no se instancia. */
    private ReglasPassword() {
    }
}
