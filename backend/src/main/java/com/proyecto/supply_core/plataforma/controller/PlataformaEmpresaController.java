package com.proyecto.supply_core.plataforma.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.proyecto.supply_core.plataforma.dto.EmpresaCambiosRequests;
import com.proyecto.supply_core.plataforma.dto.EmpresaCrearRequest;
import com.proyecto.supply_core.plataforma.dto.EmpresaDetalleResponse;
import com.proyecto.supply_core.plataforma.dto.EmpresaPlataformaResponse;
import com.proyecto.supply_core.plataforma.service.EmpresaPlataformaService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Consola de la plataforma: empresas clientes, su plan, sus módulos y el estado de su suscripción. Solo con el token
 * de la plataforma (el filtro rechaza cualquier otro). No expone datos de negocio de ninguna empresa.
 */
@RestController
@RequestMapping("/api/plataforma/empresas")
public class PlataformaEmpresaController {

    private final EmpresaPlataformaService empresaService;

    /**
     * @param empresaService lógica de empresas de la plataforma
     */
    public PlataformaEmpresaController(EmpresaPlataformaService empresaService) {
        this.empresaService = empresaService;
    }

    /**
     * Lista las empresas con su plan y estado.
     *
     * @return empresas, la más nueva primero
     */
    @GetMapping
    public List<EmpresaPlataformaResponse> listar() {
        return empresaService.listar();
    }

    /**
     * Detalle de una empresa con la situación de cada módulo.
     *
     * @param id id positivo
     * @return empresa y módulos
     */
    @GetMapping("/{id}")
    public EmpresaDetalleResponse obtener(@PathVariable @Positive(message = "debe ser un id válido") Long id) {
        return empresaService.obtener(id);
    }

    /**
     * Da de alta una empresa con su administrador inicial.
     *
     * @param request datos del alta
     * @return empresa creada (HTTP 201)
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EmpresaDetalleResponse crear(@Valid @RequestBody EmpresaCrearRequest request) {
        return empresaService.crear(request);
    }

    /**
     * Cambia el plan de una empresa.
     *
     * @param id      id positivo
     * @param request plan nuevo
     * @return empresa con sus módulos
     */
    @PutMapping("/{id}/plan")
    public EmpresaDetalleResponse cambiarPlan(@PathVariable @Positive(message = "debe ser un id válido") Long id,
            @Valid @RequestBody EmpresaCambiosRequests.CambiarPlan request) {
        return empresaService.cambiarPlan(id, request.planCodigo());
    }

    /**
     * Da, quita o restablece un módulo de la empresa aparte de su plan.
     *
     * @param id     id positivo
     * @param modulo código del módulo
     * @param request {@code habilitado}: true lo da, false lo quita, null borra la excepción
     * @return empresa con sus módulos
     */
    @PutMapping("/{id}/modulos/{modulo}")
    public EmpresaDetalleResponse excepcionDeModulo(@PathVariable @Positive(message = "debe ser un id válido") Long id,
            @PathVariable @NotBlank(message = "es obligatorio") @Size(max = 40, message = "máximo 40 caracteres")
            String modulo, @Valid @RequestBody EmpresaCambiosRequests.ModuloExcepcion request) {
        return empresaService.excepcionDeModulo(id, modulo, request.habilitado());
    }

    /**
     * Cambia el estado de la suscripción: suspender, reactivar, marcar morosa o cancelar.
     *
     * @param id      id positivo
     * @param request estado nuevo y motivo
     * @return empresa con sus módulos
     */
    @PatchMapping("/{id}/estado")
    public EmpresaDetalleResponse cambiarEstado(@PathVariable @Positive(message = "debe ser un id válido") Long id,
            @Valid @RequestBody EmpresaCambiosRequests.CambiarEstado request) {
        return empresaService.cambiarEstado(id, request.estado(), request.motivo());
    }
}
