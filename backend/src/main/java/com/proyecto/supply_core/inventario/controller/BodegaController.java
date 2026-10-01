package com.proyecto.supply_core.inventario.controller;

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

import com.proyecto.supply_core.inventario.dto.BodegaRequest;
import com.proyecto.supply_core.inventario.dto.BodegaResponse;
import com.proyecto.supply_core.inventario.service.BodegaService;
import com.proyecto.supply_core.security.annotation.RequiereRol;
import com.proyecto.supply_core.security.annotation.UsuarioInterno;
import com.proyecto.supply_core.security.context.Roles;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;

/** Bodegas: cualquier usuario interno consulta; ADMIN_SISTEMA administra. */
@RestController
@RequestMapping("/api/bodegas")
@UsuarioInterno
public class BodegaController {

    private final BodegaService bodegaService;

    /**
     * @param bodegaService lógica de bodegas
     */
    public BodegaController(BodegaService bodegaService) {
        this.bodegaService = bodegaService;
    }

    /**
     * Lista las bodegas ({@code ?activo=true} para los selectores).
     *
     * @param activo filtro opcional por estado
     * @return bodegas por nombre
     */
    @GetMapping
    public List<BodegaResponse> listar(@RequestParam(required = false) Boolean activo) {
        return bodegaService.listar(activo);
    }

    /**
     * Detalle de una bodega.
     *
     * @param id id positivo
     * @return bodega
     */
    @GetMapping("/{id}")
    public BodegaResponse obtener(@PathVariable @Positive(message = "debe ser un id válido") Long id) {
        return bodegaService.obtener(id);
    }

    /**
     * Crea una bodega.
     *
     * @param request datos validados
     * @return bodega creada (HTTP 201)
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @RequiereRol(Roles.ADMIN_SISTEMA)
    public BodegaResponse crear(@Valid @RequestBody BodegaRequest request) {
        return bodegaService.crear(request);
    }

    /**
     * Edita una bodega.
     *
     * @param id      id positivo
     * @param request datos validados
     * @return bodega actualizada
     */
    @PutMapping("/{id}")
    @RequiereRol(Roles.ADMIN_SISTEMA)
    public BodegaResponse actualizar(@PathVariable @Positive(message = "debe ser un id válido") Long id,
            @Valid @RequestBody BodegaRequest request) {
        return bodegaService.actualizar(id, request);
    }

    /**
     * Activa una bodega.
     *
     * @param id id positivo
     * @return bodega activa
     */
    @PatchMapping("/{id}/activar")
    @RequiereRol(Roles.ADMIN_SISTEMA)
    public BodegaResponse activar(@PathVariable @Positive(message = "debe ser un id válido") Long id) {
        return bodegaService.cambiarEstado(id, true);
    }

    /**
     * Desactiva una bodega (solo si no tiene stock).
     *
     * @param id id positivo
     * @return bodega inactiva
     */
    @PatchMapping("/{id}/desactivar")
    @RequiereRol(Roles.ADMIN_SISTEMA)
    public BodegaResponse desactivar(@PathVariable @Positive(message = "debe ser un id válido") Long id) {
        return bodegaService.cambiarEstado(id, false);
    }
}
