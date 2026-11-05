package com.proyecto.supply_core.usuario.dto;

import java.util.Set;

import com.proyecto.supply_core.empresa.validation.CentroCostoActivo;
import com.proyecto.supply_core.usuario.validation.CorreoUnico;
import com.proyecto.supply_core.usuario.validation.ProveedorConsistente;
import com.proyecto.supply_core.usuario.validation.ReglasPassword;
import com.proyecto.supply_core.usuario.validation.RolesExistentes;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Alta de usuario (la hace ADMIN_SISTEMA). Todas las validaciones se aplican con {@code @Valid}.
 *
 * @param nombreCompleto nombre visible
 * @param correo         correo de login, único
 * @param password       contraseña inicial (8–72, con letra y número)
 * @param roles          códigos de rol existentes (sin distinguir mayúsculas)
 * @param centroCostoId  centro de costo (obligatorio para solicitar compras, UC-04)
 * @param proveedorId    solo para usuarios con rol PROVEEDOR
 */
@ProveedorConsistente
public record UsuarioRequest(
        @NotBlank(message = "es obligatorio")
        @Size(max = 200, message = "máximo 200 caracteres")
        String nombreCompleto,

        @NotBlank(message = "es obligatorio")
        @Email(message = "no tiene un formato de correo válido")
        @Size(max = 150, message = "máximo 150 caracteres")
        @CorreoUnico
        String correo,

        @NotBlank(message = "es obligatoria")
        @Size(min = ReglasPassword.MINIMO, max = ReglasPassword.MAXIMO, message = ReglasPassword.MENSAJE_LARGO)
        @Pattern(regexp = ReglasPassword.PATRON, message = ReglasPassword.MENSAJE_PATRON)
        String password,

        @NotEmpty(message = "debe tener al menos un rol")
        @RolesExistentes
        Set<@NotBlank(message = "no puede estar vacío") String> roles,

        @Positive(message = "debe ser un id válido")
        @CentroCostoActivo
        Long centroCostoId,

        @Positive(message = "debe ser un id válido")
        Long proveedorId) implements ProveedorConsistente.ConRolesYProveedor {
}
