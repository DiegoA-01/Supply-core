package com.proyecto.supply_core.recepcion.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.proyecto.supply_core.security.annotation.RequiereModulo;
import com.proyecto.supply_core.security.context.Modulos;
import com.proyecto.supply_core.recepcion.dto.ResumenRecepcionResponse;
import com.proyecto.supply_core.recepcion.service.RecepcionService;
import com.proyecto.supply_core.security.annotation.RequiereRol;
import com.proyecto.supply_core.security.context.Roles;

import jakarta.validation.constraints.Positive;

/** Recepciones que el proveedor ve de sus órdenes (con los motivos de rechazo). Solo PROVEEDOR y solo las suyas. */
@RequiereModulo(Modulos.RECEPCION)
@RestController
@RequestMapping("/api/portal/ordenes")
@RequiereRol(Roles.PROVEEDOR)
public class PortalRecepcionController {

    private final RecepcionService recepcionService;

    /**
     * @param recepcionService lógica de recepción
     */
    public PortalRecepcionController(RecepcionService recepcionService) {
        this.recepcionService = recepcionService;
    }

    /**
     * Avance de la recepción de una orden mía.
     *
     * @param ordenId orden
     * @return pedido, aceptado, rechazado y faltante por línea, y las recepciones
     */
    @GetMapping("/{ordenId}/recepciones")
    public ResumenRecepcionResponse deOrden(@PathVariable @Positive(message = "debe ser un id válido") Long ordenId) {
        return recepcionService.deOrdenDelProveedor(ordenId);
    }
}
