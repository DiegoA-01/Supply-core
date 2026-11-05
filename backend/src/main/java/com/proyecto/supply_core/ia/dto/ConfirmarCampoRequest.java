package com.proyecto.supply_core.ia.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Confirmación o corrección humana de un dato leído por la IA.
 *
 * @param valor valor correcto (el mismo que leyó la IA para confirmarlo, u otro para corregirlo)
 */
public record ConfirmarCampoRequest(
        @NotBlank(message = "es obligatorio")
        @Size(max = 500, message = "máximo 500 caracteres")
        String valor) {
}
