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

import com.proyecto.supply_core.empresa.dto.SedeRequest;
import com.proyecto.supply_core.empresa.dto.SedeResponse;
import com.proyecto.supply_core.empresa.service.SedeService;
import com.proyecto.supply_core.security.annotation.RequiereRol;
import com.proyecto.supply_core.security.annotation.UsuarioInterno;
import com.proyecto.supply_core.security.context.Roles;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;

/** Sedes: cualquier usuario interno consulta; ADMIN_SISTEMA crea, edita, activa y desactiva. */
@RestController
@RequestMapping("/api/sedes")
@UsuarioInterno
public class SedeController {

    private final SedeService sedeService;

    /**
     * @param sedeService lógica de sedes
     */
    public SedeController(SedeService sedeService) {
        this.sedeService = sedeService;
    }

    /**
     * Lista las sedes ({@code ?activo=true} para los selectores del frontend).
     *
     * @param activo filtro opcional por estado
     * @return sedes por nombre
     */
    @GetMapping
    public List<SedeResponse> listar(@RequestParam(required = false) Boolean activo) {
        return sedeService.listar(activo);
    }

    /**
     * Detalle de una sede.
     *
     * @param id id positivo
     * @return sede
     */
    @GetMapping("/{id}")
    public SedeResponse obtener(@PathVariable @Positive(message = "debe ser un id válido") Long id) {
        return sedeService.obtener(id);
    }

    /**
     * Crea una sede.
     *
     * @param request datos validados
     * @return sede creada (HTTP 201)
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @RequiereRol(Roles.ADMIN_SISTEMA)
    public SedeResponse crear(@Valid @RequestBody SedeRequest request) {
        return sedeService.crear(request);
    }

    /**
     * Edita una sede.
     *
     * @param id      id positivo
     * @param request datos validados
     * @return sede actualizada
     */
    @PutMapping("/{id}")
    @RequiereRol(Roles.ADMIN_SISTEMA)
    public SedeResponse actualizar(@PathVariable @Positive(message = "debe ser un id válido") Long id,
            @Valid @RequestBody SedeRequest request) {
        return sedeService.actualizar(id, request);
    }

    /**
     * Activa una sede.
     *
     * @param id id positivo
     * @return sede activa
     */
    @PatchMapping("/{id}/activar")
    @RequiereRol(Roles.ADMIN_SISTEMA)
    public SedeResponse activar(@PathVariable @Positive(message = "debe ser un id válido") Long id) {
        return sedeService.cambiarEstado(id, true);
    }

    /**
     * Desactiva una sede.
     *
     * @param id id positivo
     * @return sede inactiva
     */
    @PatchMapping("/{id}/desactivar")
    @RequiereRol(Roles.ADMIN_SISTEMA)
    public SedeResponse desactivar(@PathVariable @Positive(message = "debe ser un id válido") Long id) {
        return sedeService.cambiarEstado(id, false);
    }
}
