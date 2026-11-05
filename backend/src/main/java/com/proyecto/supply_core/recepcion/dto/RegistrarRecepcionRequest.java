package com.proyecto.supply_core.recepcion.dto;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Una recepción de mercancía de una orden DESPACHADA o recibida parcialmente.
 *
 * @param bodegaId      bodega donde se recibe (opcional: por defecto la bodega destino de la orden)
 * @param observaciones comentario general (opcional)
 * @param lineas        lo que llegó de cada línea (solo las que llegaron o se rechazaron)
 */
public record RegistrarRecepcionRequest(
        @Positive(message = "debe ser un id válido")
        Long bodegaId,

        @Size(max = 500, message = "máximo 500 caracteres")
        String observaciones,

        @NotEmpty(message = "debe tener al menos una línea")
        @Valid
        List<LineaRecepcionRequest> lineas) {

    /**
     * Una línea de la orden no puede venir dos veces.
     *
     * @return {@code true} si no hay líneas repetidas
     */
    @AssertTrue(message = "no puede repetir una línea de la orden")
    public boolean isLineasSinRepetir() {
        if (lineas == null) {
            return true;
        }
        Set<Long> vistas = new HashSet<>();
        return lineas.stream().map(LineaRecepcionRequest::ordenLineaId).allMatch(vistas::add);
    }

    /**
     * Debe llegar o rechazarse algo en al menos una línea.
     *
     * @return {@code true} si hay alguna cantidad mayor que cero
     */
    @AssertTrue(message = "debe recibir algo en al menos una línea")
    public boolean isConMovimiento() {
        return lineas == null || lineas.stream().anyMatch(l -> l.cantidadRecibida() != null && l.cantidadRecibida().signum() > 0);
    }
}
