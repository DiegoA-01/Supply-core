package com.proyecto.supply_core.proveedor.enums;

/** Estado del proveedor (ENUM {@code proveedor.estado}, migración V4). */
public enum EstadoProveedor {
    /** Se registró desde el portal y espera revisión. No puede iniciar sesión. */
    PENDIENTE,
    /** Aprobado: inicia sesión, es invitado a RFQ y cotiza. */
    HABILITADO,
    /** No aprobado; el motivo queda registrado. */
    RECHAZADO,
    /** Estaba habilitado y fue bloqueado; el motivo queda registrado. */
    SUSPENDIDO;

    /**
     * Indica si se puede pasar de este estado al indicado.
     * <p>PENDIENTE → HABILITADO | RECHAZADO · HABILITADO → SUSPENDIDO · SUSPENDIDO → HABILITADO.
     * RECHAZADO es final (debe registrarse de nuevo).</p>
     *
     * @param destino estado al que se quiere pasar
     * @return {@code true} si la transición es válida
     */
    public boolean puedePasarA(EstadoProveedor destino) {
        return switch (this) {
            case PENDIENTE -> destino == HABILITADO || destino == RECHAZADO;
            case HABILITADO -> destino == SUSPENDIDO;
            case SUSPENDIDO -> destino == HABILITADO;
            case RECHAZADO -> false;
        };
    }
}
