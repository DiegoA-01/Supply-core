package com.proyecto.supply_core.proveedor.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.proyecto.supply_core.proveedor.dto.ProveedorResponse;
import com.proyecto.supply_core.proveedor.service.ProveedorService;
import com.proyecto.supply_core.security.annotation.RequiereRol;
import com.proyecto.supply_core.security.context.Roles;

/**
 * Portal del proveedor (solo rol PROVEEDOR). Todo se filtra por el {@code proveedorId} del token:
 * un proveedor nunca ve datos de otro. Aquí se sumarán sus RFQ y cotizaciones (pasos 11–12).
 */
@RestController
@RequestMapping("/api/portal")
@RequiereRol(Roles.PROVEEDOR)
public class PortalProveedorController {

    private final ProveedorService proveedorService;

    /**
     * @param proveedorService lógica de proveedores
     */
    public PortalProveedorController(ProveedorService proveedorService) {
        this.proveedorService = proveedorService;
    }

    /**
     * Datos del propio proveedor.
     *
     * @return su proveedor
     */
    @GetMapping("/proveedor")
    public ProveedorResponse miProveedor() {
        return proveedorService.miProveedor();
    }
}
