package com.proyecto.supply_core.compra.analisis.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.proyecto.supply_core.security.annotation.RequiereModulo;
import com.proyecto.supply_core.security.context.Modulos;
import com.proyecto.supply_core.compra.analisis.dto.PesosRequest;
import com.proyecto.supply_core.compra.analisis.dto.PesosResponse;
import com.proyecto.supply_core.compra.analisis.service.PesosService;
import com.proyecto.supply_core.security.annotation.RequiereRol;
import com.proyecto.supply_core.security.annotation.UsuarioInterno;
import com.proyecto.supply_core.security.context.Roles;

import jakarta.validation.Valid;

/** Matriz de pesos de evaluación. La consultan los internos; la cambia ADMIN_COMPRAS (nueva versión). */
@RequiereModulo(Modulos.RFQ_COTIZACIONES)
@RestController
@RequestMapping("/api/config-pesos")
@UsuarioInterno
public class PesosController {

    private final PesosService pesosService;

    /**
     * @param pesosService lógica de pesos
     */
    public PesosController(PesosService pesosService) {
        this.pesosService = pesosService;
    }

    /**
     * Pesos vigentes.
     *
     * @return versión vigente
     */
    @GetMapping
    public PesosResponse vigente() {
        return pesosService.vigente();
    }

    /**
     * Crea una versión nueva de pesos (todos los criterios, suma 100).
     *
     * @param request peso por criterio
     * @return versión nueva
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @RequiereRol(Roles.ADMIN_COMPRAS)
    public PesosResponse cambiar(@Valid @RequestBody PesosRequest request) {
        return pesosService.cambiar(request.pesos());
    }
}
