package com.proyecto.supply_core.proveedor.dto;

import java.util.Set;

import com.proyecto.supply_core.proveedor.validation.CategoriasActivas;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Datos editables de un proveedor (portal o ADMIN_COMPRAS). La razón social y el NIT no se
 * cambian aquí: identifican legalmente al proveedor que ya fue revisado.
 *
 * @param correoContacto correo de contacto comercial (opcional; no es el correo de login)
 * @param telefono       teléfono (opcional)
 * @param direccion      dirección (opcional)
 * @param categoriaIds   categorías que puede cotizar (al menos una, todas activas)
 */
public record ActualizarProveedorRequest(
        @Email(message = "no tiene un formato de correo válido")
        @Size(max = 150, message = "máximo 150 caracteres")
        String correoContacto,

        @Pattern(regexp = "^\\+?[0-9 -]{7,20}$", message = "no tiene un formato de teléfono válido")
        String telefono,

        @Size(max = 250, message = "máximo 250 caracteres")
        String direccion,

        @NotNull(message = "es obligatorio")
        @NotEmpty(message = "debe elegir al menos una categoría")
        @Size(max = 50, message = "máximo 50 categorías")
        @CategoriasActivas
        Set<@NotNull(message = "no puede estar vacío") @Positive(message = "debe ser un id válido") Long> categoriaIds) {
}
