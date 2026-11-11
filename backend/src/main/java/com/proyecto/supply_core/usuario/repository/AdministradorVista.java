package com.proyecto.supply_core.usuario.repository;

/** Nombre y correo de quien administra una empresa, leídos por la consola de la plataforma. */
public interface AdministradorVista {

    /**
     * Nombre completo.
     *
     * @return nombre
     */
    String getNombre();

    /**
     * Correo de ingreso.
     *
     * @return correo
     */
    String getCorreo();
}
