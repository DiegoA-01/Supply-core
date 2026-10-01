package com.proyecto.supply_core.proveedor.controller;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.proyecto.supply_core.common.dto.PageResponse;
import com.proyecto.supply_core.proveedor.dto.MotivoRequest;
import com.proyecto.supply_core.proveedor.dto.ProveedorResponse;
import com.proyecto.supply_core.proveedor.enums.EstadoProveedor;
import com.proyecto.supply_core.proveedor.service.ProveedorService;
import com.proyecto.supply_core.security.annotation.RequiereRol;
import com.proyecto.supply_core.security.context.Roles;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;

/**
 * Administración de proveedores: ADMIN_COMPRAS aprueba, rechaza y suspende.
 * COMPRADOR y AUDITOR pueden consultar (el comprador elige a quién invitar a una RFQ).
 */
@RestController
@RequestMapping("/api/proveedores")
@RequiereRol(Roles.ADMIN_COMPRAS)
public class ProveedorController {

    private final ProveedorService proveedorService;

    /**
     * @param proveedorService lógica de proveedores
     */
    public ProveedorController(ProveedorService proveedorService) {
        this.proveedorService = proveedorService;
    }

    /**
     * Lista proveedores ({@code ?estado=PENDIENTE} para la bandeja de aprobación).
     *
     * @param texto    coincidencia en razón social o NIT
     * @param estado   estado
     * @param pageable página (máximo 100) y orden
     * @return página de proveedores
     */
    @GetMapping
    @RequiereRol({ Roles.ADMIN_COMPRAS, Roles.COMPRADOR, Roles.AUDITOR })
    public PageResponse<ProveedorResponse> buscar(
            @RequestParam(required = false) String texto,
            @RequestParam(required = false) EstadoProveedor estado,
            @PageableDefault(size = 20, sort = "creadoEn", direction = Sort.Direction.DESC) Pageable pageable) {
        return proveedorService.buscar(texto, estado, pageable);
    }

    /**
     * Detalle de un proveedor.
     *
     * @param id id positivo
     * @return proveedor
     */
    @GetMapping("/{id}")
    @RequiereRol({ Roles.ADMIN_COMPRAS, Roles.COMPRADOR, Roles.AUDITOR })
    public ProveedorResponse obtener(@PathVariable @Positive(message = "debe ser un id válido") Long id) {
        return proveedorService.obtener(id);
    }

    /**
     * Aprueba un proveedor PENDIENTE (o reactiva uno SUSPENDIDO).
     *
     * @param id id positivo
     * @return proveedor habilitado
     */
    @PatchMapping("/{id}/habilitar")
    public ProveedorResponse habilitar(@PathVariable @Positive(message = "debe ser un id válido") Long id) {
        return proveedorService.habilitar(id);
    }

    /**
     * Rechaza un proveedor PENDIENTE.
     *
     * @param id      id positivo
     * @param request motivo obligatorio
     * @return proveedor rechazado
     */
    @PatchMapping("/{id}/rechazar")
    public ProveedorResponse rechazar(@PathVariable @Positive(message = "debe ser un id válido") Long id, @Valid @RequestBody MotivoRequest request) {
        return proveedorService.rechazar(id, request);
    }

    /**
     * Suspende un proveedor HABILITADO.
     *
     * @param id      id positivo
     * @param request motivo obligatorio
     * @return proveedor suspendido
     */
    @PatchMapping("/{id}/suspender")
    public ProveedorResponse suspender(@PathVariable @Positive(message = "debe ser un id válido") Long id, @Valid @RequestBody MotivoRequest request) {
        return proveedorService.suspender(id, request);
    }
}
