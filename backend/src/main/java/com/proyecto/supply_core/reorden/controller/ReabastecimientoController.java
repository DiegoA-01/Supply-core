package com.proyecto.supply_core.reorden.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.proyecto.supply_core.compra.solicitud.dto.SolicitudResponse;
import com.proyecto.supply_core.reorden.dto.ReabastecimientoBorradorRequest;
import com.proyecto.supply_core.reorden.dto.ReabastecimientoResponse;
import com.proyecto.supply_core.reorden.dto.ResumenSede;
import com.proyecto.supply_core.reorden.service.ReabastecimientoService;
import com.proyecto.supply_core.security.annotation.RequiereModulo;
import com.proyecto.supply_core.security.annotation.RequiereRol;
import com.proyecto.supply_core.security.annotation.UsuarioInterno;
import com.proyecto.supply_core.security.context.Modulos;
import com.proyecto.supply_core.security.context.Roles;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;

/**
 * Plan de reabastecimiento: qué pedir, cuándo y cuánto según el consumo real y lo que tarda el proveedor en
 * entregar. Lo ven quienes gestionan el abastecimiento y el administrador; el SOLICITANTE lo convierte en una
 * solicitud BORRADOR que sigue el flujo normal.
 */
@RequiereModulo(Modulos.COMPRAS)
@RestController
@RequestMapping("/api/reabastecimiento")
@UsuarioInterno
public class ReabastecimientoController {

    private final ReabastecimientoService reabastecimientoService;

    /**
     * @param reabastecimientoService lógica del plan
     */
    public ReabastecimientoController(ReabastecimientoService reabastecimientoService) {
        this.reabastecimientoService = reabastecimientoService;
    }

    /**
     * Plan de todos los productos activos, de más a menos urgente, de la empresa o de una sede.
     *
     * @param sedeId solo el stock, el consumo y las órdenes de las bodegas de esta sede, opcional
     * @return plan con resumen
     */
    @GetMapping
    @RequiereRol({ Roles.ADMIN_COMPRAS, Roles.COMPRADOR, Roles.ALMACENISTA, Roles.SOLICITANTE, Roles.AUDITOR,
            Roles.ADMIN_SISTEMA })
    public ReabastecimientoResponse plan(
            @RequestParam(required = false) @Positive(message = "debe ser un id válido") Long sedeId) {
        return reabastecimientoService.plan(sedeId);
    }

    /**
     * Comparación de las sedes: cuántos productos tiene cada una por pedir y cuántos podría cubrir trasladando.
     *
     * @return una fila por sede activa
     */
    @GetMapping("/sedes")
    @RequiereRol({ Roles.ADMIN_COMPRAS, Roles.COMPRADOR, Roles.ALMACENISTA, Roles.SOLICITANTE, Roles.AUDITOR,
            Roles.ADMIN_SISTEMA })
    public List<ResumenSede> sedes() {
        return reabastecimientoService.resumenSedes();
    }

    /**
     * Crea una solicitud BORRADOR con la cantidad sugerida de los productos elegidos. No compra ni aprueba nada.
     *
     * @param request fecha requerida y productos
     * @return solicitud en BORRADOR (HTTP 201)
     */
    @PostMapping("/borrador")
    @ResponseStatus(HttpStatus.CREATED)
    @RequiereRol(Roles.SOLICITANTE)
    public SolicitudResponse crearBorrador(@Valid @RequestBody ReabastecimientoBorradorRequest request) {
        return reabastecimientoService.crearBorrador(request);
    }
}
