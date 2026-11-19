package com.proyecto.supply_core.caja.controller;

import java.util.List;

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

import com.proyecto.supply_core.caja.dto.CajaRequest;
import com.proyecto.supply_core.caja.dto.CajaResponse;
import com.proyecto.supply_core.caja.dto.ConfiguracionCaja;
import com.proyecto.supply_core.caja.service.CajaService;
import com.proyecto.supply_core.security.annotation.RequiereModulo;
import com.proyecto.supply_core.security.annotation.RequiereRol;
import com.proyecto.supply_core.security.annotation.UsuarioInterno;
import com.proyecto.supply_core.security.context.Modulos;
import com.proyecto.supply_core.security.context.Roles;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;

/**
 * Cajas y su configuración (spec 003). Las crea y edita el dueño; las consultan caja, supervisión y auditoría (el
 * cajero las necesita para abrir su turno).
 */
@RequiereModulo(Modulos.CAJA)
@RestController
@RequestMapping("/api/cajas")
@UsuarioInterno
@RequiereRol(Roles.ADMIN_SISTEMA)
public class CajaController {

    private final CajaService cajaService;

    /**
     * @param cajaService lógica de cajas
     */
    public CajaController(CajaService cajaService) {
        this.cajaService = cajaService;
    }

    /**
     * @param activa filtro por estado (vacío = todas)
     * @return cajas
     */
    @GetMapping
    @RequiereRol({ Roles.ADMIN_SISTEMA, Roles.SUPERVISOR_VENTAS, Roles.CAJERO, Roles.AUDITOR })
    public List<CajaResponse> listar(@RequestParam(required = false) Boolean activa) {
        return cajaService.listar(activa);
    }

    /**
     * @param request datos de la caja
     * @return caja creada (HTTP 201)
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CajaResponse crear(@Valid @RequestBody CajaRequest request) {
        return cajaService.crear(request);
    }

    /**
     * @param id      caja
     * @param request datos nuevos
     * @return caja actualizada
     */
    @PutMapping("/{id}")
    public CajaResponse editar(@PathVariable @Positive(message = "debe ser un id válido") Long id,
            @Valid @RequestBody CajaRequest request) {
        return cajaService.editar(id, request);
    }

    /**
     * @return la tolerancia de diferencia de caja
     */
    @GetMapping("/configuracion")
    @RequiereRol({ Roles.ADMIN_SISTEMA, Roles.SUPERVISOR_VENTAS, Roles.AUDITOR })
    public ConfiguracionCaja configuracion() {
        return cajaService.configuracion();
    }

    /**
     * @param request tolerancia nueva
     * @return configuración guardada
     */
    @PutMapping("/configuracion")
    public ConfiguracionCaja configurar(@Valid @RequestBody ConfiguracionCaja request) {
        return cajaService.configurar(request);
    }
}
