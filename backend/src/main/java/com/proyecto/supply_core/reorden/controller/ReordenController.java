package com.proyecto.supply_core.reorden.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.proyecto.supply_core.security.annotation.RequiereModulo;
import com.proyecto.supply_core.security.context.Modulos;
import com.proyecto.supply_core.compra.solicitud.dto.SolicitudResponse;
import com.proyecto.supply_core.reorden.dto.BorradorReordenRequest;
import com.proyecto.supply_core.reorden.dto.SugerenciaReorden;
import com.proyecto.supply_core.reorden.service.ReordenService;
import com.proyecto.supply_core.security.annotation.RequiereRol;
import com.proyecto.supply_core.security.annotation.UsuarioInterno;
import com.proyecto.supply_core.security.context.Roles;

import jakarta.validation.Valid;

/**
 * Reorden sugerida (paso 18-d). Cualquiera que gestione el abastecimiento ve las sugerencias; el SOLICITANTE las
 * convierte en una solicitud BORRADOR que revisa y envía por el flujo normal. Solo usuarios internos.
 */
@RequiereModulo(Modulos.COMPRAS)
@RestController
@RequestMapping("/api/reorden")
@UsuarioInterno
public class ReordenController {

    private final ReordenService reordenService;

    /**
     * @param reordenService lógica de la reorden
     */
    public ReordenController(ReordenService reordenService) {
        this.reordenService = reordenService;
    }

    /**
     * Productos bajo su stock mínimo con la cantidad sugerida para llegar al máximo.
     *
     * @return sugerencias por código de producto
     */
    @GetMapping("/sugerencias")
    @RequiereRol({ Roles.ADMIN_COMPRAS, Roles.COMPRADOR, Roles.ALMACENISTA, Roles.SOLICITANTE, Roles.AUDITOR })
    public List<SugerenciaReorden> sugerencias() {
        return reordenService.sugerencias();
    }

    /**
     * Crea una solicitud en BORRADOR con las cantidades sugeridas. No compra ni aprueba nada.
     *
     * @param request fecha requerida y productos a incluir (todos si no se indican)
     * @return solicitud en BORRADOR (HTTP 201)
     */
    @PostMapping("/borrador")
    @ResponseStatus(HttpStatus.CREATED)
    @RequiereRol(Roles.SOLICITANTE)
    public SolicitudResponse crearBorrador(@Valid @RequestBody BorradorReordenRequest request) {
        return reordenService.crearBorrador(request);
    }
}
