package com.proyecto.supply_core.plataforma.dto;

import com.proyecto.supply_core.plataforma.enums.EstadoSuscripcion;
import com.proyecto.supply_core.usuario.validation.ReglasPassword;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Alta de una empresa cliente con su administrador inicial. La plataforma crea la empresa, su suscripción y a la
 * persona que luego crea los demás usuarios; no crea ningún dato de negocio.
 *
 * @param razonSocial    nombre legal
 * @param nit            NIT sin puntos, con dígito de verificación opcional; único
 * @param codigoPublico  código con el que la empresa entra y recibe el registro de proveedores: texto libre con
 *                       espacios, mayúsculas y números; solo se excluyen {@code / \ ? # %} y los caracteres de
 *                       control, que rompen la dirección del registro; único sin distinguir mayúsculas
 * @param planCodigo     plan que se le asigna
 * @param estadoInicial  PRUEBA o ACTIVA ({@code null} = PRUEBA)
 * @param adminNombre    nombre del administrador de la empresa
 * @param adminCorreo    correo del administrador de la empresa
 * @param adminPassword  contraseña inicial del administrador (8 a 72, con letra y número)
 */
public record EmpresaCrearRequest(
        @NotBlank(message = "es obligatoria")
        @Size(max = 200, message = "máximo 200 caracteres")
        String razonSocial,

        @NotBlank(message = "es obligatorio")
        @Pattern(regexp = "^\\d{6,15}(-\\d)?$",
                message = "solo números, con dígito de verificación opcional (ej. 900123456-7)")
        String nit,

        @NotBlank(message = "es obligatorio")
        @Size(min = 3, max = 40, message = "entre 3 y 40 caracteres")
        @Pattern(regexp = "^[^/\\\\?#%\\p{Cntrl}]+$", message = "no puede llevar / \\ ? # % ni caracteres de control")
        String codigoPublico,

        @NotBlank(message = "es obligatorio")
        @Size(max = 30, message = "máximo 30 caracteres")
        String planCodigo,

        EstadoSuscripcion estadoInicial,

        @NotBlank(message = "es obligatorio")
        @Size(max = 150, message = "máximo 150 caracteres")
        String adminNombre,

        @NotBlank(message = "es obligatorio")
        @Email(message = "no es un correo válido")
        @Size(max = 150, message = "máximo 150 caracteres")
        String adminCorreo,

        @NotBlank(message = "es obligatoria")
        @Size(min = ReglasPassword.MINIMO, max = ReglasPassword.MAXIMO, message = ReglasPassword.MENSAJE_LARGO)
        @Pattern(regexp = ReglasPassword.PATRON, message = ReglasPassword.MENSAJE_PATRON)
        String adminPassword) {
}
