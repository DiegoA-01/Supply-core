package com.proyecto.supply_core.recepcion.enums;

/**
 * Por qué se rechazó mercancía en la recepción (ENUM de {@code recepcion_linea.motivo_rechazo}).
 * Lo rechazado nunca entra al stock y afecta el indicador de calidad del proveedor.
 */
public enum MotivoRechazo {
    /** Llegó roto o en mal estado. */
    DANADO("Dañado o en mal estado"),
    /** No cumple lo pedido (medida, marca, referencia, calidad). */
    NO_CUMPLE_ESPECIFICACION("No cumple la especificación"),
    /** Vencido o próximo a vencer. */
    VENCIDO("Vencido"),
    /** Producto distinto o cantidad que no corresponde a la orden. */
    CANTIDAD_ERRADA("Producto o cantidad errada"),
    /** Otro motivo, descrito en la observación de calidad. */
    OTRO("Otro motivo");

    /** Texto para mostrar al usuario y al proveedor. */
    private final String descripcion;

    /**
     * @param descripcion texto para mostrar
     */
    MotivoRechazo(String descripcion) {
        this.descripcion = descripcion;
    }

    /**
     * Texto legible del motivo.
     *
     * @return descripción
     */
    public String descripcion() {
        return descripcion;
    }
}
