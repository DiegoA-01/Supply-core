package com.proyecto.supply_core.venta.dto;

import java.util.List;

import com.proyecto.supply_core.venta.enums.FormaReembolso;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Devolución de un cliente.
 *
 * @param ventaId        venta original
 * @param motivo         motivo de la devolución
 * @param formaReembolso efectivo, mismo medio o saldo a favor
 * @param lineas         líneas devueltas (1 a 300)
 * @param autorizacion   permiso de un solo uso del supervisor (acción DEVOLUCION)
 */
public record DevolucionRequest(
        @NotNull(message = "es obligatoria") @Positive(message = "debe ser un id válido") Long ventaId,
        @NotBlank(message = "es obligatorio") @Size(min = 5, max = 300, message = "debe tener entre 5 y 300 caracteres") String motivo,
        @NotNull(message = "es obligatoria") FormaReembolso formaReembolso,
        @NotEmpty(message = "elija al menos una línea") @Size(max = 300, message = "admite hasta 300 líneas")
        List<@Valid DevolucionLineaRequest> lineas,
        @NotBlank(message = "es obligatoria") @Size(max = 100, message = "no es una autorización válida") String autorizacion) {
}
