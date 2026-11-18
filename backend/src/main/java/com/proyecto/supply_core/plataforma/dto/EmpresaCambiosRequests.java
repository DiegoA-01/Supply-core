package com.proyecto.supply_core.plataforma.dto;

import com.proyecto.supply_core.plataforma.enums.EstadoSuscripcion;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Cuerpos de las operaciones de la plataforma sobre una empresa ya creada.
 */
public final class EmpresaCambiosRequests {

    /** Clase contenedora: no se instancia. */
    private EmpresaCambiosRequests() {
    }

    /**
     * Cambio de plan.
     *
     * @param planCodigo plan nuevo (activo)
     */
    public record CambiarPlan(
            @NotBlank(message = "es obligatorio")
            @Size(max = 30, message = "máximo 30 caracteres")
            String planCodigo) {
    }

    /**
     * Excepción de módulo para la empresa.
     *
     * @param habilitado {@code true} le da el módulo aunque su plan no lo incluya, {@code false} se lo quita aunque su
     *                   plan lo incluya, {@code null} quita la excepción y vuelve a mandar el plan
     */
    public record ModuloExcepcion(Boolean habilitado) {
    }

    /**
     * Cambio de estado de la suscripción.
     *
     * @param estado destino (PRUEBA no se puede volver a poner)
     * @param motivo obligatorio para MOROSA, SUSPENDIDA y CANCELADA
     */
    public record CambiarEstado(
            @NotNull(message = "es obligatorio")
            EstadoSuscripcion estado,

            @Size(max = 500, message = "máximo 500 caracteres")
            String motivo) {
    }
}
