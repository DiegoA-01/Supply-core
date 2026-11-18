package com.proyecto.supply_core.plataforma.enums;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Estados de la suscripción de una empresa (ENUM de {@code suscripcion.estado}).
 * <pre>
 * PRUEBA → ACTIVA → MOROSA (solo lectura) → SUSPENDIDA (sin acceso) → CANCELADA (final)
 * </pre>
 * Reglas de acceso: PRUEBA y ACTIVA leen y escriben; MOROSA solo lee; SUSPENDIDA y CANCELADA no entran.
 */
public enum EstadoSuscripcion {
    /** Periodo de prueba: acceso completo a los módulos del plan. */
    PRUEBA,
    /** Al día: acceso completo a los módulos del plan. */
    ACTIVA,
    /** Con deuda: solo consulta, no se puede crear ni cambiar nada. */
    MOROSA,
    /** Sin acceso, con los datos conservados; se puede reactivar. */
    SUSPENDIDA,
    /** Terminada: sin acceso y sin reactivación. */
    CANCELADA;

    /** Transiciones que puede hacer la plataforma a mano. */
    private static final Map<EstadoSuscripcion, Set<EstadoSuscripcion>> TRANSICIONES = Map.of(
            PRUEBA, EnumSet.of(ACTIVA, MOROSA, SUSPENDIDA, CANCELADA),
            ACTIVA, EnumSet.of(MOROSA, SUSPENDIDA, CANCELADA),
            MOROSA, EnumSet.of(ACTIVA, SUSPENDIDA, CANCELADA),
            SUSPENDIDA, EnumSet.of(ACTIVA, MOROSA, CANCELADA));

    /**
     * Indica si la empresa puede entrar a la aplicación.
     *
     * @return {@code true} salvo SUSPENDIDA y CANCELADA
     */
    public boolean permiteAcceso() {
        return this != SUSPENDIDA && this != CANCELADA;
    }

    /**
     * Indica si la empresa puede crear o cambiar datos.
     *
     * @return {@code true} en PRUEBA y ACTIVA
     */
    public boolean permiteEscritura() {
        return this == PRUEBA || this == ACTIVA;
    }

    /**
     * Indica si la plataforma puede pasar de este estado al destino.
     *
     * @param destino estado al que se quiere pasar
     * @return {@code true} si la transición está permitida
     */
    public boolean puedePasarA(EstadoSuscripcion destino) {
        return TRANSICIONES.getOrDefault(this, Set.of()).contains(destino);
    }
}
