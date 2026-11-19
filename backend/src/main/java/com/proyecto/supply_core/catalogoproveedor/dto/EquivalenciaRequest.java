package com.proyecto.supply_core.catalogoproveedor.dto;

import com.proyecto.supply_core.catalogoproveedor.enums.EstadoEquivalencia;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Decisión de compras sobre un artículo del catálogo.
 *
 * @param estado     CONFIRMADA (con producto) o DESCARTADA
 * @param productoId producto equivalente (obligatorio al confirmar)
 */
public record EquivalenciaRequest(
        @NotNull(message = "es obligatorio") EstadoEquivalencia estado,
        @Positive(message = "debe ser un id válido") Long productoId) {

    /**
     * @return {@code true} si confirma con producto o descarta
     */
    @AssertTrue(message = "para confirmar elige el producto; también puedes descartar")
    public boolean isDecisionValida() {
        return estado == EstadoEquivalencia.DESCARTADA || (estado == EstadoEquivalencia.CONFIRMADA && productoId != null);
    }
}
