package com.proyecto.supply_core.compra.orden.controller;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.proyecto.supply_core.security.annotation.RequiereModulo;
import com.proyecto.supply_core.security.context.Modulos;
import com.proyecto.supply_core.common.dto.PageResponse;
import com.proyecto.supply_core.compra.orden.dto.MotivoOrdenRequest;
import com.proyecto.supply_core.compra.orden.dto.OrdenResponse;
import com.proyecto.supply_core.compra.orden.dto.OrdenResumenResponse;
import com.proyecto.supply_core.compra.orden.enums.EstadoOrden;
import com.proyecto.supply_core.compra.orden.service.OrdenProveedorService;
import com.proyecto.supply_core.security.annotation.RequiereRol;
import com.proyecto.supply_core.security.context.Roles;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;

/** Órdenes vistas y atendidas por el proveedor (UC-12). Solo PROVEEDOR y solo las suyas. */
@RequiereModulo(Modulos.ORDENES_PAGOS)
@RestController
@RequestMapping("/api/portal/ordenes")
@RequiereRol(Roles.PROVEEDOR)
public class PortalOrdenController {

    private final OrdenProveedorService proveedorService;

    /**
     * @param proveedorService lógica del proveedor
     */
    public PortalOrdenController(OrdenProveedorService proveedorService) {
        this.proveedorService = proveedorService;
    }

    /**
     * Mis órdenes.
     *
     * @param estado   estado (opcional)
     * @param pageable página (máximo 100) y orden
     * @return página de órdenes
     */
    @GetMapping
    public PageResponse<OrdenResumenResponse> mias(@RequestParam(required = false) EstadoOrden estado,
            @PageableDefault(size = 20, sort = "creadoEn", direction = Sort.Direction.DESC) Pageable pageable) {
        return proveedorService.mias(estado, pageable);
    }

    /**
     * Detalle de una orden mía.
     *
     * @param id id positivo
     * @return orden
     */
    @GetMapping("/{id}")
    public OrdenResponse obtener(@PathVariable @Positive(message = "debe ser un id válido") Long id) {
        return proveedorService.obtener(id);
    }

    /**
     * Acepta la orden.
     *
     * @param id id positivo
     * @return orden aceptada
     */
    @PostMapping("/{id}/aceptar")
    public OrdenResponse aceptar(@PathVariable @Positive(message = "debe ser un id válido") Long id) {
        return proveedorService.aceptar(id);
    }

    /**
     * Rechaza la orden.
     *
     * @param id      id positivo
     * @param request motivo
     * @return orden rechazada
     */
    @PostMapping("/{id}/rechazar")
    public OrdenResponse rechazar(@PathVariable @Positive(message = "debe ser un id válido") Long id,
            @Valid @RequestBody MotivoOrdenRequest request) {
        return proveedorService.rechazar(id, request.observacion());
    }

    /**
     * Marca la orden en preparación.
     *
     * @param id id positivo
     * @return orden en preparación
     */
    @PostMapping("/{id}/preparar")
    public OrdenResponse preparar(@PathVariable @Positive(message = "debe ser un id válido") Long id) {
        return proveedorService.preparar(id);
    }

    /**
     * Despacha la orden.
     *
     * @param id      id positivo
     * @param request guía o comentario (opcional)
     * @return orden despachada
     */
    @PostMapping("/{id}/despachar")
    public OrdenResponse despachar(@PathVariable @Positive(message = "debe ser un id válido") Long id,
            @Valid @RequestBody(required = false) MotivoOrdenRequest request) {
        return proveedorService.despachar(id, request == null ? null : request.observacion());
    }
}
