package com.proyecto.supply_core.factura.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.proyecto.supply_core.security.annotation.RequiereModulo;
import com.proyecto.supply_core.security.context.Modulos;
import com.proyecto.supply_core.factura.dto.FacturaResponse;
import com.proyecto.supply_core.factura.service.FacturaService;
import com.proyecto.supply_core.security.annotation.RequiereRol;
import com.proyecto.supply_core.security.context.Roles;

import jakarta.validation.constraints.Positive;

/** Facturas que el proveedor ve de sus órdenes, con el resultado del cruce. Solo PROVEEDOR y solo las suyas. */
@RequiereModulo(Modulos.ORDENES_PAGOS)
@RestController
@RequestMapping("/api/portal/ordenes")
@RequiereRol(Roles.PROVEEDOR)
public class PortalFacturaController {

    private final FacturaService facturaService;

    /**
     * @param facturaService lógica de facturas
     */
    public PortalFacturaController(FacturaService facturaService) {
        this.facturaService = facturaService;
    }

    /**
     * Facturas de una orden mía.
     *
     * @param ordenId cualquier versión de la orden
     * @return facturas con su estado de cruce
     */
    @GetMapping("/{ordenId}/facturas")
    public List<FacturaResponse> deOrden(@PathVariable @Positive(message = "debe ser un id válido") Long ordenId) {
        return facturaService.deOrdenDelProveedor(ordenId);
    }
}
