package com.proyecto.supply_core.presupuesto.controller;

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

import com.proyecto.supply_core.security.annotation.RequiereModulo;
import com.proyecto.supply_core.security.context.Modulos;
import com.proyecto.supply_core.presupuesto.dto.PresupuestoRequest;
import com.proyecto.supply_core.presupuesto.dto.PresupuestoResponse;
import com.proyecto.supply_core.presupuesto.dto.PresupuestoUpdateRequest;
import com.proyecto.supply_core.presupuesto.service.PresupuestoService;
import com.proyecto.supply_core.security.annotation.RequiereRol;
import com.proyecto.supply_core.security.annotation.UsuarioInterno;
import com.proyecto.supply_core.security.context.Roles;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;

/**
 * Presupuesto por centro de costo y mes (paso 18-a). ADMIN_COMPRAS lo define y lo corrige; compras, aprobación,
 * administración y auditoría lo consultan. Solo usuarios internos.
 */
@RequiereModulo(Modulos.COMPRAS)
@RestController
@RequestMapping("/api/presupuestos")
@UsuarioInterno
public class PresupuestoController {

    private final PresupuestoService presupuestoService;

    /**
     * @param presupuestoService lógica del presupuesto
     */
    public PresupuestoController(PresupuestoService presupuestoService) {
        this.presupuestoService = presupuestoService;
    }

    /**
     * Define el presupuesto de un centro de costo para un mes.
     *
     * @param request datos validados
     * @return presupuesto creado (HTTP 201)
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @RequiereRol(Roles.ADMIN_COMPRAS)
    public PresupuestoResponse crear(@Valid @RequestBody PresupuestoRequest request) {
        return presupuestoService.crear(request);
    }

    /**
     * Corrige el monto de un presupuesto.
     *
     * @param id      id positivo
     * @param request nuevo monto
     * @return presupuesto actualizado
     */
    @PutMapping("/{id}")
    @RequiereRol(Roles.ADMIN_COMPRAS)
    public PresupuestoResponse actualizar(@PathVariable @Positive(message = "debe ser un id válido") Long id,
            @Valid @RequestBody PresupuestoUpdateRequest request) {
        return presupuestoService.actualizar(id, request);
    }

    /**
     * Lista presupuestos con lo comprometido en cada mes.
     *
     * @param centroCostoId centro de costo
     * @param anio          año
     * @return presupuestos, del mes más reciente al más antiguo
     */
    @GetMapping
    @RequiereRol({ Roles.ADMIN_COMPRAS, Roles.COMPRADOR, Roles.APROBADOR, Roles.ADMINISTRATIVO, Roles.AUDITOR })
    public List<PresupuestoResponse> listar(
            @RequestParam(required = false) @Positive(message = "debe ser un id válido") Long centroCostoId,
            @RequestParam(required = false) @Min(value = 2000, message = "debe ser 2000 o posterior")
            @Max(value = 2100, message = "debe ser 2100 o anterior") Integer anio) {
        return presupuestoService.listar(centroCostoId, anio);
    }

    /**
     * Detalle de un presupuesto.
     *
     * @param id id positivo
     * @return presupuesto con lo comprometido
     */
    @GetMapping("/{id}")
    @RequiereRol({ Roles.ADMIN_COMPRAS, Roles.COMPRADOR, Roles.APROBADOR, Roles.ADMINISTRATIVO, Roles.AUDITOR })
    public PresupuestoResponse obtener(@PathVariable @Positive(message = "debe ser un id válido") Long id) {
        return presupuestoService.obtener(id);
    }
}
