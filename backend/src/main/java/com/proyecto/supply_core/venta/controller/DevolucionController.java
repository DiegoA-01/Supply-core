package com.proyecto.supply_core.venta.controller;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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
import com.proyecto.supply_core.venta.dto.DevolucionRequest;
import com.proyecto.supply_core.venta.dto.DevolucionResponse;
import com.proyecto.supply_core.venta.service.DevolucionService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;

/** Devoluciones de clientes (spec 006): las recibe el cajero en su turno con autorización del supervisor. */
@RequiereModulo(Modulos.CAJA)
@RestController
@RequestMapping("/api/devoluciones")
@UsuarioInterno
@Validated
public class DevolucionController {

    private final DevolucionService devolucionService;

    /**
     * @param devolucionService lógica de devoluciones
     */
    public DevolucionController(DevolucionService devolucionService) {
        this.devolucionService = devolucionService;
    }

    /**
     * @param request venta, líneas, estado de cada producto, reembolso y autorización
     * @return la devolución
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @RequiereRol({ Roles.CAJERO, Roles.SUPERVISOR_VENTAS })
    public DevolucionResponse devolver(@Valid @RequestBody DevolucionRequest request) {
        return devolucionService.devolver(request);
    }

    /**
     * @param ventaId  venta (vacío = todas)
     * @param pageable página (por defecto las más recientes primero)
     * @return devoluciones
     */
    @GetMapping
    @RequiereRol({ Roles.SUPERVISOR_VENTAS, Roles.ADMIN_SISTEMA, Roles.AUDITOR, Roles.CAJERO })
    public PageResponse<DevolucionResponse> buscar(@RequestParam(required = false) Long ventaId,
            @PageableDefault(size = 20, sort = "creadoEn", direction = Sort.Direction.DESC) Pageable pageable) {
        return devolucionService.buscar(ventaId, pageable);
    }

    /**
     * @param id devolución
     * @return devolución con sus líneas
     */
    @GetMapping("/{id}")
    @RequiereRol({ Roles.SUPERVISOR_VENTAS, Roles.ADMIN_SISTEMA, Roles.AUDITOR, Roles.CAJERO })
    public DevolucionResponse obtener(@PathVariable @Positive(message = "debe ser un id válido") Long id) {
        return devolucionService.obtener(id);
    }
}
