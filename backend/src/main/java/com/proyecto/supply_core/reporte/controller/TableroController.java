package com.proyecto.supply_core.reporte.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.proyecto.supply_core.reporte.dto.TableroResponse;
import com.proyecto.supply_core.reporte.service.TableroService;
import com.proyecto.supply_core.security.annotation.RequiereModulo;
import com.proyecto.supply_core.security.annotation.RequiereRol;
import com.proyecto.supply_core.security.annotation.UsuarioInterno;
import com.proyecto.supply_core.security.context.Modulos;
import com.proyecto.supply_core.security.context.Roles;

import jakarta.validation.constraints.Positive;

/**
 * Tablero del dueño de la empresa: gasto, dinero en inventario, rotación y qué hay que pedir, en una sola vista.
 * Lo ven el administrador de la empresa, el jefe de compras y auditoría.
 */
@RequiereModulo(Modulos.REPORTES)
@RestController
@RequestMapping("/api/reportes/tablero")
@UsuarioInterno
public class TableroController {

    private final TableroService tableroService;

    /**
     * @param tableroService armado del tablero
     */
    public TableroController(TableroService tableroService) {
        this.tableroService = tableroService;
    }

    /**
     * Tablero de los últimos doce meses, de la empresa o de una sede.
     *
     * @param sedeId solo lo de las bodegas de esta sede, opcional
     * @return cifras ya calculadas
     */
    @GetMapping
    @RequiereRol({ Roles.ADMIN_SISTEMA, Roles.ADMIN_COMPRAS, Roles.AUDITOR })
    public TableroResponse tablero(
            @RequestParam(required = false) @Positive(message = "debe ser un id válido") Long sedeId) {
        return tableroService.tablero(sedeId);
    }
}
