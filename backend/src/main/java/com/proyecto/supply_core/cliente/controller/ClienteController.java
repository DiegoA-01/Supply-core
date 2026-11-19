package com.proyecto.supply_core.cliente.controller;

import org.springframework.data.domain.Pageable;
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

import com.proyecto.supply_core.cliente.dto.ClienteRequest;
import com.proyecto.supply_core.cliente.dto.ClienteResponse;
import com.proyecto.supply_core.cliente.service.ClienteService;
import com.proyecto.supply_core.common.dto.PageResponse;
import com.proyecto.supply_core.security.annotation.RequiereModulo;
import com.proyecto.supply_core.security.annotation.RequiereRol;
import com.proyecto.supply_core.security.annotation.UsuarioInterno;
import com.proyecto.supply_core.security.context.Modulos;
import com.proyecto.supply_core.security.context.Roles;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Clientes de la empresa (spec 002). Los consultan caja, venta, el dueño y auditoría; los crean y editan caja, venta
 * y el dueño. El cupo, el plazo y la lista de precios solo los cambian el dueño o el supervisor (lo valida el servicio).
 */
@RequiereModulo(Modulos.VENTAS)
@RestController
@RequestMapping("/api/clientes")
@UsuarioInterno
@RequiereRol({ Roles.CAJERO, Roles.VENDEDOR, Roles.SUPERVISOR_VENTAS, Roles.ADMIN_SISTEMA, Roles.AUDITOR })
public class ClienteController {

    private final ClienteService clienteService;

    /**
     * @param clienteService lógica de clientes
     */
    public ClienteController(ClienteService clienteService) {
        this.clienteService = clienteService;
    }

    /**
     * @param q        número de documento (empieza por) o nombre (contiene); vacío = todos
     * @param activo   filtro por estado
     * @param pageable página (20 por defecto)
     * @return página de clientes, con el consumidor final primero
     */
    @GetMapping
    public PageResponse<ClienteResponse> buscar(
            @RequestParam(required = false) @Size(max = 100, message = "máximo 100 caracteres") String q,
            @RequestParam(required = false) Boolean activo,
            @PageableDefault(size = 20) Pageable pageable) {
        return clienteService.buscar(q, activo, pageable);
    }

    /**
     * @return el consumidor final de la empresa (para las ventas sin datos)
     */
    @GetMapping("/consumidor-final")
    public ClienteResponse consumidorFinal() {
        return clienteService.consumidorFinal();
    }

    /**
     * @param id cliente
     * @return el cliente
     */
    @GetMapping("/{id}")
    public ClienteResponse obtener(@PathVariable @Positive(message = "debe ser un id válido") Long id) {
        return clienteService.obtener(id);
    }

    /**
     * @param request datos del cliente
     * @return cliente creado (HTTP 201)
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @RequiereRol({ Roles.CAJERO, Roles.VENDEDOR, Roles.SUPERVISOR_VENTAS, Roles.ADMIN_SISTEMA })
    public ClienteResponse crear(@Valid @RequestBody ClienteRequest request) {
        return clienteService.crear(request);
    }

    /**
     * @param id      cliente
     * @param request datos nuevos (cupo, plazo y lista vacíos se conservan)
     * @return cliente actualizado
     */
    @PutMapping("/{id}")
    @RequiereRol({ Roles.CAJERO, Roles.VENDEDOR, Roles.SUPERVISOR_VENTAS, Roles.ADMIN_SISTEMA })
    public ClienteResponse editar(@PathVariable @Positive(message = "debe ser un id válido") Long id,
            @Valid @RequestBody ClienteRequest request) {
        return clienteService.editar(id, request);
    }
}
