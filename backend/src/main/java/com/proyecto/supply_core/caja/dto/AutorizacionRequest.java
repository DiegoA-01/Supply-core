package com.proyecto.supply_core.caja.dto;

import com.proyecto.supply_core.caja.enums.AccionAutorizada;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * El supervisor escribe su correo y su PIN en la pantalla del cajero para autorizar una acción.
 *
 * @param correo       correo del supervisor
 * @param pin          PIN de 6 dígitos (va en el cuerpo, nunca en la dirección)
 * @param accion       qué se autoriza
 * @param turnoId      turno del cajero
 * @param referenciaId venta u otro registro, si la acción lo tiene
 */
public record AutorizacionRequest(
        @NotBlank(message = "es obligatorio") @Email(message = "no es un correo válido")
        @Size(max = 150, message = "máximo 150 caracteres") String correo,
        @NotBlank(message = "es obligatorio") @Pattern(regexp = "^\\d{6}$", message = "son 6 dígitos") String pin,
        @NotNull(message = "es obligatoria") AccionAutorizada accion,
        @NotNull(message = "es obligatorio") @Positive(message = "debe ser un id válido") Long turnoId,
        @Positive(message = "debe ser un id válido") Long referenciaId) {

    /**
     * Oculta el PIN si el registro se imprime en un log.
     *
     * @return texto sin el PIN
     */
    @Override
    public String toString() {
        return "AutorizacionRequest[correo=" + correo + ", accion=" + accion + ", turnoId=" + turnoId + "]";
    }
}
