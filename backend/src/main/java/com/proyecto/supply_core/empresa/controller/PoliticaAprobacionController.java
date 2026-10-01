package com.proyecto.supply_core.empresa.controller;

import java.math.BigDecimal;
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

import com.proyecto.supply_core.empresa.dto.PoliticaAplicableResponse;
import com.proyecto.supply_core.empresa.dto.PoliticaAprobacionRequest;
import com.proyecto.supply_core.empresa.dto.PoliticaAprobacionResponse;
import com.proyecto.supply_core.empresa.enums.TipoPolitica;
import com.proyecto.supply_core.empresa.service.PoliticaAprobacionService;
import com.proyecto.supply_core.security.annotation.RequiereRol;
import com.proyecto.supply_core.security.annotation.UsuarioInterno;
import com.proyecto.supply_core.security.context.Roles;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

/** Políticas de aprobación: cualquier usuario interno consulta; ADMIN_COMPRAS las define. */
@RestController
@RequestMapping("/api/politicas-aprobacion")
@UsuarioInterno
public class PoliticaAprobacionController {

    private final PoliticaAprobacionService politicaService;

    /**
     * @param politicaService lógica de políticas
     */
    public PoliticaAprobacionController(PoliticaAprobacionService politicaService) {
        this.politicaService = politicaService;
    }

    /**
     * Lista todas las políticas.
     *
     * @return políticas por tipo y monto
     */
    @GetMapping
    public List<PoliticaAprobacionResponse> listar() {
        return politicaService.listar();
    }

    /**
     * Qué política aplica a un monto (p. ej. para avisar al solicitante quién aprobará).
     *
     * @param tipo  SOLICITUD o ADJUDICACION
     * @param monto monto a evaluar (≥ 0)
     * @return si requiere aprobación y con qué política
     */
    @GetMapping("/aplicable")
    public PoliticaAplicableResponse aplicable(
            @RequestParam @NotNull(message = "es obligatorio") TipoPolitica tipo,
            @RequestParam @NotNull(message = "es obligatorio") @PositiveOrZero(message = "no puede ser negativo")
            @Digits(integer = 16, fraction = 2, message = "máximo 16 enteros y 2 decimales") BigDecimal monto) {
        return politicaService.consultarAplicable(tipo, monto);
    }

    /**
     * Detalle de una política.
     *
     * @param id id positivo
     * @return política
     */
    @GetMapping("/{id}")
    public PoliticaAprobacionResponse obtener(@PathVariable @Positive(message = "debe ser un id válido") Long id) {
        return politicaService.obtener(id);
    }

    /**
     * Crea una política activa.
     *
     * @param request datos validados
     * @return política creada (HTTP 201)
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @RequiereRol(Roles.ADMIN_COMPRAS)
    public PoliticaAprobacionResponse crear(@Valid @RequestBody PoliticaAprobacionRequest request) {
        return politicaService.crear(request);
    }

    /**
     * Edita una política.
     *
     * @param id      id positivo
     * @param request datos validados
     * @return política actualizada
     */
    @PutMapping("/{id}")
    @RequiereRol(Roles.ADMIN_COMPRAS)
    public PoliticaAprobacionResponse actualizar(@PathVariable @Positive(message = "debe ser un id válido") Long id,
            @Valid @RequestBody PoliticaAprobacionRequest request) {
        return politicaService.actualizar(id, request);
    }

    /**
     * Activa una política (se revisa que no se superponga).
     *
     * @param id id positivo
     * @return política activa
     */
    @PatchMapping("/{id}/activar")
    @RequiereRol(Roles.ADMIN_COMPRAS)
    public PoliticaAprobacionResponse activar(@PathVariable @Positive(message = "debe ser un id válido") Long id) {
        return politicaService.cambiarEstado(id, true);
    }

    /**
     * Desactiva una política.
     *
     * @param id id positivo
     * @return política inactiva
     */
    @PatchMapping("/{id}/desactivar")
    @RequiereRol(Roles.ADMIN_COMPRAS)
    public PoliticaAprobacionResponse desactivar(@PathVariable @Positive(message = "debe ser un id válido") Long id) {
        return politicaService.cambiarEstado(id, false);
    }
}
