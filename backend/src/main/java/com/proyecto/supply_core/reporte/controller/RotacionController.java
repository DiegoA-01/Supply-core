package com.proyecto.supply_core.reporte.controller;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.proyecto.supply_core.reporte.dto.RotacionResponse;
import com.proyecto.supply_core.reporte.service.RotacionService;
import com.proyecto.supply_core.security.annotation.RequiereModulo;
import com.proyecto.supply_core.security.annotation.RequiereRol;
import com.proyecto.supply_core.security.context.Modulos;
import com.proyecto.supply_core.security.context.Roles;

import jakarta.validation.constraints.Positive;

/**
 * Rotación de productos (UC-15), solo lectura: qué sale más, qué sale menos y cuánto le alcanza el stock. Lo ven el
 * administrador de la empresa, compras, aprobadores, tesorería, auditoría y bodega.
 */
@RequiereModulo(Modulos.REPORTES)
@RestController
@RequestMapping("/api/reportes/rotacion")
@RequiereRol({ Roles.ADMIN_SISTEMA, Roles.ADMIN_COMPRAS, Roles.COMPRADOR, Roles.APROBADOR, Roles.ADMINISTRATIVO,
        Roles.AUDITOR, Roles.ALMACENISTA })
public class RotacionController {

    private final RotacionService rotacionService;

    /**
     * @param rotacionService cálculo de la rotación
     */
    public RotacionController(RotacionService rotacionService) {
        this.rotacionService = rotacionService;
    }

    /**
     * Rotación de los productos activos en un período.
     *
     * @param desde       primer día (yyyy-MM-dd); por defecto 90 días antes de {@code hasta}
     * @param hasta       último día incluido (yyyy-MM-dd); por defecto hoy
     * @param categoriaId categoría, opcional
     * @param sedeId      solo el stock y las salidas de las bodegas de esta sede, opcional
     * @return productos de más a menos salidas, con resumen
     */
    @GetMapping
    public RotacionResponse rotacion(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) @Positive(message = "debe ser un id válido") Long categoriaId,
            @RequestParam(required = false) @Positive(message = "debe ser un id válido") Long sedeId) {
        return rotacionService.rotacion(desde, hasta, categoriaId, sedeId);
    }
}
