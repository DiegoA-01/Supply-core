package com.proyecto.supply_core.factura.enums;

/** Estado de una factura del proveedor (ENUM de {@code factura_proveedor.estado}). */
public enum EstadoFactura {
    /** El total de la factura coincide con lo recibido y aceptado, dentro de la tolerancia de la empresa. */
    CUADRADA,
    /** El total se aparta más de la tolerancia: no se puede pagar hasta que compras la apruebe o se anule. */
    EN_REVISION,
    /** Estaba EN_REVISION y compras la aprobó con un motivo: se puede pagar. */
    APROBADA,
    /** Registrada por error y anulada con motivo (final). Su número se puede volver a registrar. */
    ANULADA;

    /**
     * Indica si con esta factura se puede registrar el pago final.
     *
     * @return {@code true} si está CUADRADA o APROBADA
     */
    public boolean permitePagoFinal() {
        return this == CUADRADA || this == APROBADA;
    }
}
