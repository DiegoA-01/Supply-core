package com.proyecto.supply_core.proveedor.dto;

import java.util.Set;

import com.proyecto.supply_core.proveedor.validation.CategoriasActivas;
import com.proyecto.supply_core.proveedor.validation.NitUnico;
import com.proyecto.supply_core.usuario.validation.CorreoUnico;
import com.proyecto.supply_core.usuario.validation.ReglasPassword;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Autorregistro público del proveedor desde el portal. Crea el proveedor en PENDIENTE y su
 * usuario (rol PROVEEDOR) inactivo hasta que ADMIN_COMPRAS lo habilite.
 *
 * @param razonSocial    nombre legal de la empresa proveedora
 * @param nit            NIT sin puntos, con dígito de verificación opcional (900123456-7)
 * @param telefono       teléfono de contacto
 * @param direccion      dirección principal
 * @param categoriaIds   categorías que puede cotizar (activas)
 * @param nombreContacto persona que administrará el portal
 * @param correo         correo de contacto y de inicio de sesión (único)
 * @param password       contraseña del portal (8–72, con letra y número)
 */
public record RegistroProveedorRequest(
        @NotBlank(message = "es obligatoria")
        @Size(max = 200, message = "máximo 200 caracteres")
        String razonSocial,

        @NotBlank(message = "es obligatorio")
        @Pattern(regexp = "^\\d{6,15}(-\\d)?$",
                message = "solo números, con dígito de verificación opcional (ej. 900123456-7)")
        @NitUnico
        String nit,

        @Pattern(regexp = "^\\+?[0-9 -]{7,20}$", message = "no tiene un formato de teléfono válido")
        String telefono,

        @Size(max = 250, message = "máximo 250 caracteres")
        String direccion,

        @NotNull(message = "es obligatorio")
        @NotEmpty(message = "debe elegir al menos una categoría")
        @Size(max = 50, message = "máximo 50 categorías")
        @CategoriasActivas
        Set<@NotNull(message = "no puede estar vacío") @Positive(message = "debe ser un id válido") Long> categoriaIds,

        @NotBlank(message = "es obligatorio")
        @Size(max = 200, message = "máximo 200 caracteres")
        String nombreContacto,

        @NotBlank(message = "es obligatorio")
        @Email(message = "no tiene un formato de correo válido")
        @Size(max = 150, message = "máximo 150 caracteres")
        @CorreoUnico
        String correo,

        @NotBlank(message = "es obligatoria")
        @Size(min = ReglasPassword.MINIMO, max = ReglasPassword.MAXIMO, message = ReglasPassword.MENSAJE_LARGO)
        @Pattern(regexp = ReglasPassword.PATRON, message = ReglasPassword.MENSAJE_PATRON)
        String password) {
}
