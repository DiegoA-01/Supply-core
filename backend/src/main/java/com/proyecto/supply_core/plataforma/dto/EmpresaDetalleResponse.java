package com.proyecto.supply_core.plataforma.dto;

import java.util.List;

/**
 * Empresa con sus módulos: cuáles trae su plan, cuáles tienen una excepción y cuáles quedan activos.
 *
 * @param empresa       empresa y suscripción
 * @param modulos       todos los módulos del catálogo, con su situación para esta empresa
 * @param administrador quien administra la empresa (el primer ADMIN_SISTEMA); {@code null} si no tiene
 */
public record EmpresaDetalleResponse(EmpresaPlataformaResponse empresa, List<ModuloEmpresa> modulos,
        Administrador administrador) {

    /**
     * Detalle sin administrador conocido.
     *
     * @param empresa empresa y suscripción
     * @param modulos módulos con su situación
     */
    public EmpresaDetalleResponse(EmpresaPlataformaResponse empresa, List<ModuloEmpresa> modulos) {
        this(empresa, modulos, null);
    }

    /**
     * Administrador de la empresa: solo su nombre y su correo, nunca su contraseña ni datos de negocio.
     *
     * @param nombre nombre completo
     * @param correo correo con el que entra
     */
    public record Administrador(String nombre, String correo) {
    }

    /**
     * Situación de un módulo para una empresa.
     *
     * @param codigo    código del módulo
     * @param nombre    nombre
     * @param enPlan    si el plan de la empresa lo incluye
     * @param excepcion {@code true} si se le dio aparte, {@code false} si se le quitó, {@code null} si sigue el plan
     * @param activo    si la empresa lo puede usar hoy
     */
    public record ModuloEmpresa(String codigo, String nombre, boolean enPlan, Boolean excepcion, boolean activo) {
    }
}
