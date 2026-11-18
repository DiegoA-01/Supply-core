package com.proyecto.supply_core.plataforma.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.proyecto.supply_core.plataforma.dto.PlanDtos;
import com.proyecto.supply_core.plataforma.service.PlanPlataformaService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;

/**
 * Planes comerciales y catálogo de módulos. Solo con el token de la plataforma.
 */
@RestController
@RequestMapping("/api/plataforma")
public class PlataformaPlanController {

    private final PlanPlataformaService planService;

    /**
     * @param planService lógica de planes y módulos
     */
    public PlataformaPlanController(PlanPlataformaService planService) {
        this.planService = planService;
    }

    /**
     * Catálogo de módulos.
     *
     * @return módulos por código
     */
    @GetMapping("/modulos")
    public List<PlanDtos.ModuloRespuesta> modulos() {
        return planService.modulos();
    }

    /**
     * Planes con sus módulos y cuántas empresas los usan.
     *
     * @return planes por código
     */
    @GetMapping("/planes")
    public List<PlanDtos.Respuesta> planes() {
        return planService.listar();
    }

    /**
     * Crea un plan.
     *
     * @param request código, nombre y módulos
     * @return plan creado (HTTP 201)
     */
    @PostMapping("/planes")
    @ResponseStatus(HttpStatus.CREATED)
    public PlanDtos.Respuesta crear(@Valid @RequestBody PlanDtos.Crear request) {
        return planService.crear(request);
    }

    /**
     * Cambia el nombre, los módulos o el estado de un plan.
     *
     * @param id      id positivo
     * @param request cambios
     * @return plan actualizado
     */
    @PutMapping("/planes/{id}")
    public PlanDtos.Respuesta editar(@PathVariable @Positive(message = "debe ser un id válido") Long id,
            @Valid @RequestBody PlanDtos.Editar request) {
        return planService.editar(id, request);
    }
}
