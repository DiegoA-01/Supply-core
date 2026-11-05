package com.proyecto.supply_core.despacho.controller;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.proyecto.supply_core.security.annotation.RequiereModulo;
import com.proyecto.supply_core.security.context.Modulos;
import com.proyecto.supply_core.common.dto.PageResponse;
import com.proyecto.supply_core.despacho.dto.AnulacionRequest;
import com.proyecto.supply_core.despacho.dto.DespachoRequest;
import com.proyecto.supply_core.despacho.dto.DespachoResponse;
import com.proyecto.supply_core.despacho.dto.DespachoResumenResponse;
import com.proyecto.supply_core.despacho.enums.EstadoDespacho;
import com.proyecto.supply_core.despacho.service.DespachoService;
import com.proyecto.supply_core.security.annotation.RequiereRol;
import com.proyecto.supply_core.security.context.Roles;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;

/**
 * Despachos (remisiones de salida). Los arma y confirma el ALMACENISTA; anula el almacenista
 * o ADMIN_COMPRAS; consultan compras y auditoría.
 */
@RequiereModulo(Modulos.DESPACHOS)
@RestController
@RequestMapping("/api/despachos")
@RequiereRol({ Roles.ALMACENISTA, Roles.ADMIN_COMPRAS, Roles.COMPRADOR, Roles.AUDITOR })
public class DespachoController {

    private final DespachoService despachoService;

    /**
     * @param despachoService lógica de despachos
     */
    public DespachoController(DespachoService despachoService) {
        this.despachoService = despachoService;
    }

    /**
     * Crea un despacho en BORRADOR (no toca el stock).
     *
     * @param request bodega, datos y líneas
     * @return despacho creado
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @RequiereRol(Roles.ALMACENISTA)
    public DespachoResponse crear(@Valid @RequestBody DespachoRequest request) {
        return despachoService.crear(request);
    }

    /**
     * Edita un despacho en BORRADOR.
     *
     * @param id      id positivo
     * @param request bodega, datos y líneas
     * @return despacho actualizado
     */
    @PutMapping("/{id}")
    @RequiereRol(Roles.ALMACENISTA)
    public DespachoResponse actualizar(@PathVariable @Positive(message = "debe ser un id válido") Long id,
            @Valid @RequestBody DespachoRequest request) {
        return despachoService.actualizar(id, request);
    }

    /**
     * Confirma el despacho y descuenta el stock (todo o nada).
     *
     * @param id id positivo
     * @return despacho confirmado
     */
    @PostMapping("/{id}/confirmar")
    @RequiereRol(Roles.ALMACENISTA)
    public DespachoResponse confirmar(@PathVariable @Positive(message = "debe ser un id válido") Long id) {
        return despachoService.confirmar(id);
    }

    /**
     * Anula el despacho; si estaba confirmado, devuelve el stock.
     *
     * @param id      id positivo
     * @param request motivo
     * @return despacho anulado
     */
    @PostMapping("/{id}/anular")
    @RequiereRol({ Roles.ALMACENISTA, Roles.ADMIN_COMPRAS })
    public DespachoResponse anular(@PathVariable @Positive(message = "debe ser un id válido") Long id,
            @Valid @RequestBody AnulacionRequest request) {
        return despachoService.anular(id, request.motivo());
    }

    /**
     * Lista despachos con filtros.
     *
     * @param texto    coincidencia en consecutivo, cliente o factura/ticket
     * @param estado   estado
     * @param bodegaId bodega
     * @param pageable página (máximo 100) y orden
     * @return página de despachos
     */
    @GetMapping
    public PageResponse<DespachoResumenResponse> buscar(@RequestParam(required = false) String texto,
            @RequestParam(required = false) EstadoDespacho estado,
            @RequestParam(required = false) @Positive(message = "debe ser un id válido") Long bodegaId,
            @PageableDefault(size = 20, sort = "creadoEn", direction = Sort.Direction.DESC) Pageable pageable) {
        return despachoService.buscar(texto, estado, bodegaId, pageable);
    }

    /**
     * Detalle con líneas.
     *
     * @param id id positivo
     * @return despacho
     */
    @GetMapping("/{id}")
    public DespachoResponse obtener(@PathVariable @Positive(message = "debe ser un id válido") Long id) {
        return despachoService.obtener(id);
    }
}
