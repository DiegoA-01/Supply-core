package com.proyecto.supply_core.empresa.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.proyecto.supply_core.empresa.dto.CentroCostoRequest;
import com.proyecto.supply_core.empresa.dto.CentroCostoResponse;
import com.proyecto.supply_core.empresa.dto.CentroCostoUpdateRequest;
import com.proyecto.supply_core.empresa.service.CentroCostoService;
import com.proyecto.supply_core.security.annotation.RequiereRol;
import com.proyecto.supply_core.security.annotation.UsuarioInterno;
import com.proyecto.supply_core.security.context.Roles;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;

/** Centros de costo: cualquier usuario interno consulta; ADMIN_SISTEMA administra. */
@RestController
@RequestMapping("/api/centros-costo")
@UsuarioInterno
public class CentroCostoController {

    private final CentroCostoService centroCostoService;

    /**
     * @param centroCostoService lógica de centros de costo
     */
    public CentroCostoController(CentroCostoService centroCostoService) {
        this.centroCostoService = centroCostoService;
    }

    /**
     * Lista los centros de costo ({@code ?activo=true} para los selectores).
     *
     * @param activo filtro opcional por estado
     * @return centros por código
     */
    @GetMapping
    public List<CentroCostoResponse> listar(@RequestParam(required = false) Boolean activo) {
        return centroCostoService.listar(activo);
    }

    /**
     * Detalle de un centro de costo.
     *
     * @param id id positivo
     * @return centro de costo
     */
    @GetMapping("/{id}")
    public CentroCostoResponse obtener(@PathVariable @Positive(message = "debe ser un id válido") Long id) {
        return centroCostoService.obtener(id);
    }

    /**
     * Crea un centro de costo.
     *
     * @param request datos validados (código único)
     * @return centro creado (HTTP 201)
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @RequiereRol(Roles.ADMIN_SISTEMA)
    public CentroCostoResponse crear(@Valid @RequestBody CentroCostoRequest request) {
        return centroCostoService.crear(request);
    }

    /**
     * Edita un centro de costo.
     *
     * @param id      id positivo
     * @param request datos validados
     * @return centro actualizado
     */
    @PutMapping("/{id}")
    @RequiereRol(Roles.ADMIN_SISTEMA)
    public CentroCostoResponse actualizar(@PathVariable @Positive(message = "debe ser un id válido") Long id,
            @Valid @RequestBody CentroCostoUpdateRequest request) {
        return centroCostoService.actualizar(id, request);
    }

    /**
     * Activa un centro de costo.
     *
     * @param id id positivo
     * @return centro activo
     */
    @PatchMapping("/{id}/activar")
    @RequiereRol(Roles.ADMIN_SISTEMA)
    public CentroCostoResponse activar(@PathVariable @Positive(message = "debe ser un id válido") Long id) {
        return centroCostoService.cambiarEstado(id, true);
    }

    /**
     * Desactiva un centro de costo.
     *
     * @param id id positivo
     * @return centro inactivo
     */
    @PatchMapping("/{id}/desactivar")
    @RequiereRol(Roles.ADMIN_SISTEMA)
    public CentroCostoResponse desactivar(@PathVariable @Positive(message = "debe ser un id válido") Long id) {
        return centroCostoService.cambiarEstado(id, false);
    }
}
