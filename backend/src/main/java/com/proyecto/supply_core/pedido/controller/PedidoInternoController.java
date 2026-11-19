package com.proyecto.supply_core.pedido.controller;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.proyecto.supply_core.common.dto.PageResponse;
import com.proyecto.supply_core.pedido.dto.GestionPedidoRequest;
import com.proyecto.supply_core.pedido.dto.PedidoInternoRequest;
import com.proyecto.supply_core.pedido.dto.PedidoInternoResponse;
import com.proyecto.supply_core.pedido.enums.EstadoPedidoInterno;
import com.proyecto.supply_core.pedido.service.PedidoInternoService;
import com.proyecto.supply_core.security.annotation.RequiereModulo;
import com.proyecto.supply_core.security.annotation.RequiereRol;
import com.proyecto.supply_core.security.annotation.UsuarioInterno;
import com.proyecto.supply_core.security.context.Modulos;
import com.proyecto.supply_core.security.context.Roles;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;

/**
 * Pedidos internos ("Necesito este producto"). Cualquier usuario interno pide y ve los suyos; compras ve todos y
 * responde. Los proveedores no tienen acceso.
 */
@RequiereModulo(Modulos.COMPRAS)
@RestController
@RequestMapping("/api/pedidos-internos")
@UsuarioInterno
public class PedidoInternoController {

    private final PedidoInternoService pedidoInternoService;

    /**
     * @param pedidoInternoService lógica de los pedidos
     */
    public PedidoInternoController(PedidoInternoService pedidoInternoService) {
        this.pedidoInternoService = pedidoInternoService;
    }

    /**
     * Pide algo que hace falta; compras recibe el aviso.
     *
     * @param request qué hace falta
     * @return pedido PENDIENTE (HTTP 201)
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PedidoInternoResponse crear(@Valid @RequestBody PedidoInternoRequest request) {
        return pedidoInternoService.crear(request);
    }

    /**
     * Pedidos de la empresa (compras) o del usuario (los demás).
     *
     * @param mios     {@code true} para ver solo los propios
     * @param estado   filtro por estado, opcional
     * @param pageable página (por defecto los más recientes primero)
     * @return página de pedidos
     */
    @GetMapping
    public PageResponse<PedidoInternoResponse> listar(@RequestParam(defaultValue = "false") boolean mios,
            @RequestParam(required = false) EstadoPedidoInterno estado,
            @PageableDefault(size = 20, sort = "creadoEn", direction = Sort.Direction.DESC) Pageable pageable) {
        return pedidoInternoService.listar(mios, estado, pageable);
    }

    /**
     * Compras toma, atiende o descarta un pedido.
     *
     * @param id      pedido
     * @param request nuevo estado y respuesta
     * @return pedido actualizado
     */
    @PatchMapping("/{id}")
    @RequiereRol({ Roles.ADMIN_COMPRAS, Roles.COMPRADOR })
    public PedidoInternoResponse gestionar(@PathVariable @Positive(message = "debe ser un id válido") Long id,
            @Valid @RequestBody GestionPedidoRequest request) {
        return pedidoInternoService.gestionar(id, request);
    }
}
