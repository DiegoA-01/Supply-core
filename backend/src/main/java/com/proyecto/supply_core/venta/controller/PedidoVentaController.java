package com.proyecto.supply_core.venta.controller;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.proyecto.supply_core.common.dto.PageResponse;
import com.proyecto.supply_core.security.annotation.RequiereModulo;
import com.proyecto.supply_core.security.annotation.RequiereRol;
import com.proyecto.supply_core.security.annotation.UsuarioInterno;
import com.proyecto.supply_core.security.context.Modulos;
import com.proyecto.supply_core.security.context.Roles;
import com.proyecto.supply_core.venta.dto.PedidoVentaRequest;
import com.proyecto.supply_core.venta.dto.PedidoVentaResponse;
import com.proyecto.supply_core.venta.enums.EstadoPedidoVenta;
import com.proyecto.supply_core.venta.service.PedidoVentaService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;

/** Pedidos de venta sin cobrar (spec 004): los arma el vendedor y los cobra el cajero. */
@RequiereModulo(Modulos.VENTAS)
@RestController
@RequestMapping("/api/pedidos-venta")
@UsuarioInterno
@RequiereRol({ Roles.VENDEDOR, Roles.CAJERO, Roles.SUPERVISOR_VENTAS, Roles.ADMIN_SISTEMA })
@Validated
public class PedidoVentaController {

    private final PedidoVentaService pedidoService;

    /**
     * @param pedidoService lógica de pedidos de venta
     */
    public PedidoVentaController(PedidoVentaService pedidoService) {
        this.pedidoService = pedidoService;
    }

    /**
     * @param request cliente, líneas y nota
     * @return pedido pendiente de cobro
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PedidoVentaResponse crear(@Valid @RequestBody PedidoVentaRequest request) {
        return pedidoService.crear(request);
    }

    /**
     * @param estado   estado (vacío = todos)
     * @param pageable página (por defecto los más recientes primero)
     * @return pedidos (el vendedor ve los suyos)
     */
    @GetMapping
    public PageResponse<PedidoVentaResponse> buscar(@RequestParam(required = false) EstadoPedidoVenta estado,
            @PageableDefault(size = 20, sort = "creadoEn", direction = Sort.Direction.DESC) Pageable pageable) {
        return pedidoService.buscar(estado, pageable);
    }

    /**
     * @param id pedido
     * @return pedido
     */
    @GetMapping("/{id}")
    public PedidoVentaResponse obtener(@PathVariable @Positive(message = "debe ser un id válido") Long id) {
        return pedidoService.obtener(id);
    }

    /**
     * @param id      pedido
     * @param request cliente, líneas y nota nuevos
     * @return pedido editado
     */
    @PutMapping("/{id}")
    public PedidoVentaResponse editar(@PathVariable @Positive(message = "debe ser un id válido") Long id,
            @Valid @RequestBody PedidoVentaRequest request) {
        return pedidoService.editar(id, request);
    }

    /**
     * @param id pedido
     * @return pedido descartado
     */
    @PostMapping("/{id}/descartar")
    public PedidoVentaResponse descartar(@PathVariable @Positive(message = "debe ser un id válido") Long id) {
        return pedidoService.descartar(id);
    }
}
