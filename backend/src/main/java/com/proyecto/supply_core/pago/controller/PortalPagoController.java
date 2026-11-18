package com.proyecto.supply_core.pago.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.proyecto.supply_core.security.annotation.RequiereModulo;
import com.proyecto.supply_core.security.context.Modulos;
import com.proyecto.supply_core.pago.dto.PagosOrdenResponse;
import com.proyecto.supply_core.pago.service.PagoService;
import com.proyecto.supply_core.security.annotation.RequiereRol;
import com.proyecto.supply_core.security.context.Roles;

import jakarta.validation.constraints.Positive;

/** Pagos que el proveedor ve de sus órdenes. Solo PROVEEDOR y solo las suyas. */
@RequiereModulo(Modulos.ORDENES_PAGOS)
@RestController
@RequestMapping("/api/portal/ordenes")
@RequiereRol(Roles.PROVEEDOR)
public class PortalPagoController {

    private final PagoService pagoService;

    /**
     * @param pagoService lógica de pagos
     */
    public PortalPagoController(PagoService pagoService) {
        this.pagoService = pagoService;
    }

    /**
     * Total, pagado, saldo y pagos de una orden mía.
     *
     * @param ordenId cualquier versión de la orden
     * @return resumen de pagos
     */
    @GetMapping("/{ordenId}/pagos")
    public PagosOrdenResponse deOrden(@PathVariable @Positive(message = "debe ser un id válido") Long ordenId) {
        return pagoService.deOrdenDelProveedor(ordenId);
    }
}
