package com.proyecto.supply_core.compra.orden.controller;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.proyecto.supply_core.security.annotation.RequiereModulo;
import com.proyecto.supply_core.security.context.Modulos;
import com.proyecto.supply_core.common.dto.PageResponse;
import com.proyecto.supply_core.compra.orden.dto.EmitirDirectaRequest;
import com.proyecto.supply_core.compra.orden.dto.EmitirOrdenRequest;
import com.proyecto.supply_core.compra.orden.dto.MotivoOrdenRequest;
import com.proyecto.supply_core.compra.orden.dto.NuevaVersionRequest;
import com.proyecto.supply_core.compra.orden.dto.OrdenResponse;
import com.proyecto.supply_core.compra.orden.dto.OrdenResumenResponse;
import com.proyecto.supply_core.compra.orden.enums.EstadoOrden;
import com.proyecto.supply_core.compra.orden.service.OrdenCompraService;
import com.proyecto.supply_core.compra.orden.service.OrdenDirectaService;
import com.proyecto.supply_core.security.annotation.RequiereRol;
import com.proyecto.supply_core.security.context.Roles;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;

/**
 * Órdenes de compra (UC-10). Emite, versiona y cancela el COMPRADOR; consultan compras, bodega,
 * administrativo, aprobadores y auditoría.
 */
@RequiereModulo(Modulos.ORDENES_PAGOS)
@RestController
@RequestMapping("/api/ordenes")
@RequiereRol({ Roles.COMPRADOR, Roles.ADMIN_COMPRAS, Roles.ALMACENISTA, Roles.ADMINISTRATIVO, Roles.APROBADOR,
        Roles.AUDITOR })
public class OrdenController {

    private final OrdenCompraService ordenService;
    private final OrdenDirectaService directaService;

    /**
     * @param ordenService   lógica de órdenes
     * @param directaService orden directa al precio pactado de un contrato
     */
    public OrdenController(OrdenCompraService ordenService, OrdenDirectaService directaService) {
        this.ordenService = ordenService;
        this.directaService = directaService;
    }

    /**
     * Emite una orden directa al precio pactado de un contrato, sin RFQ, desde una solicitud aprobada.
     *
     * @param request solicitud, contrato, bodega y entrega
     * @return orden EMITIDA
     */
    @PostMapping("/directa")
    @ResponseStatus(HttpStatus.CREATED)
    @RequiereRol(Roles.COMPRADOR)
    public OrdenResponse emitirDirecta(@Valid @RequestBody EmitirDirectaRequest request) {
        return directaService.emitir(request);
    }

    /**
     * Emite la orden desde una adjudicación aprobada.
     *
     * @param request adjudicación, bodega y entrega
     * @return orden EMITIDA
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @RequiereRol(Roles.COMPRADOR)
    public OrdenResponse emitir(@Valid @RequestBody EmitirOrdenRequest request) {
        return ordenService.emitir(request);
    }

    /**
     * Crea una versión nueva con cambios (la actual queda REEMPLAZADA).
     *
     * @param id      id positivo de la versión actual
     * @param request motivo y cambios
     * @return versión nueva
     */
    @PostMapping("/{id}/version")
    @ResponseStatus(HttpStatus.CREATED)
    @RequiereRol(Roles.COMPRADOR)
    public OrdenResponse nuevaVersion(@PathVariable @Positive(message = "debe ser un id válido") Long id,
            @Valid @RequestBody NuevaVersionRequest request) {
        return ordenService.nuevaVersion(id, request);
    }

    /**
     * Cancela la orden.
     *
     * @param id      id positivo
     * @param request motivo
     * @return orden CANCELADA
     */
    @PostMapping("/{id}/cancelar")
    @RequiereRol(Roles.COMPRADOR)
    public OrdenResponse cancelar(@PathVariable @Positive(message = "debe ser un id válido") Long id,
            @Valid @RequestBody MotivoOrdenRequest request) {
        return ordenService.cancelar(id, request.observacion());
    }

    /**
     * Lista órdenes (sin versiones reemplazadas salvo que se filtre por ese estado).
     *
     * @param texto       consecutivo
     * @param estado      estado
     * @param proveedorId proveedor
     * @param pageable    página (máximo 100) y orden
     * @return página de órdenes
     */
    @GetMapping
    public PageResponse<OrdenResumenResponse> buscar(@RequestParam(required = false) String texto,
            @RequestParam(required = false) EstadoOrden estado,
            @RequestParam(required = false) @Positive(message = "debe ser un id válido") Long proveedorId,
            @PageableDefault(size = 20, sort = "creadoEn", direction = Sort.Direction.DESC) Pageable pageable) {
        return ordenService.buscar(texto, estado, proveedorId, pageable);
    }

    /**
     * Detalle con líneas e historial.
     *
     * @param id id positivo
     * @return orden
     */
    @GetMapping("/{id}")
    public OrdenResponse obtener(@PathVariable @Positive(message = "debe ser un id válido") Long id) {
        return ordenService.obtener(id);
    }
}
