package com.proyecto.supply_core.usuario.dto;

import java.util.Set;

import com.proyecto.supply_core.empresa.validation.CentroCostoActivo;
import com.proyecto.supply_core.usuario.validation.ProveedorConsistente;
import com.proyecto.supply_core.usuario.validation.RolesExistentes;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Edición de datos y roles. La contraseña se cambia por endpoints aparte.
 * El correo único se valida en el Service porque hay que excluir al propio usuario (id de la URL).
 *
 * @param nombreCompleto nombre visible
 * @param correo         correo de login
 * @param roles          códigos de rol existentes
 * @param centroCostoId  centro de costo
 * @param proveedorId    solo para usuarios con rol PROVEEDOR
 */
@ProveedorConsistente
public record UsuarioUpdateRequest(
        @NotBlank(message = "es obligatorio")
        @Size(max = 200, message = "máximo 200 caracteres")
        String nombreCompleto,

        @NotBlank(message = "es obligatorio")
        @Email(message = "no tiene un formato de correo válido")
        @Size(max = 150, message = "máximo 150 caracteres")
        String correo,

        @NotEmpty(message = "debe tener al menos un rol")
        @RolesExistentes
        Set<@NotBlank(message = "no puede estar vacío") String> roles,

        @Positive(message = "debe ser un id válido")
        @CentroCostoActivo
        Long centroCostoId,

        @Positive(message = "debe ser un id válido")
        Long proveedorId) implements ProveedorConsistente.ConRolesYProveedor {
}
