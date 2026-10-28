package com.proyecto.supply_core.compra.adjudicacion.controller;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.proyecto.supply_core.security.annotation.RequiereModulo;
import com.proyecto.supply_core.security.context.Modulos;
import com.proyecto.supply_core.common.dto.PageResponse;
import com.proyecto.supply_core.compra.adjudicacion.dto.AdjudicacionResponse;
import com.proyecto.supply_core.compra.adjudicacion.dto.AdjudicarRequest;
import com.proyecto.supply_core.compra.adjudicacion.dto.DecisionAdjudicacionRequest;
import com.proyecto.supply_core.compra.adjudicacion.service.AdjudicacionService;
import com.proyecto.supply_core.security.annotation.RequiereRol;
import com.proyecto.supply_core.security.annotation.UsuarioInterno;
import com.proyecto.supply_core.security.context.Roles;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;

/**
 * Adjudicación (UC-09). Adjudica el COMPRADOR; decide el rol de la política (verificado en el Service).
 */
@RequiereModulo(Modulos.RFQ_COTIZACIONES)
@RestController
@RequestMapping("/api")
@UsuarioInterno
public class AdjudicacionController {

    private final AdjudicacionService adjudicacionService;

    /**
     * @param adjudicacionService lógica de adjudicación
     */
    public AdjudicacionController(AdjudicacionService adjudicacionService) {
        this.adjudicacionService = adjudicacionService;
    }

    /**
     * Adjudica la RFQ a una cotización del análisis publicado.
     *
     * @param rfqId   id positivo de la RFQ
     * @param request cotización y justificación
     * @return adjudicación
     */
    @PostMapping("/rfqs/{rfqId}/adjudicacion")
    @ResponseStatus(HttpStatus.CREATED)
    @RequiereRol(Roles.COMPRADOR)
    public AdjudicacionResponse adjudicar(@PathVariable @Positive(message = "debe ser un id válido") Long rfqId,
            @Valid @RequestBody AdjudicarRequest request) {
        return adjudicacionService.adjudicar(rfqId, request);
    }

    /**
     * Última adjudicación de la RFQ.
     *
     * @param rfqId id positivo de la RFQ
     * @return adjudicación
     */
    @GetMapping("/rfqs/{rfqId}/adjudicacion")
    @RequiereRol({ Roles.COMPRADOR, Roles.ADMIN_COMPRAS, Roles.APROBADOR, Roles.AUDITOR })
    public AdjudicacionResponse deRfq(@PathVariable @Positive(message = "debe ser un id válido") Long rfqId) {
        return adjudicacionService.deRfq(rfqId);
    }

    /**
     * Bandeja del aprobador.
     *
     * @param pageable página (máximo 100) y orden
     * @return adjudicaciones pendientes de su rol
     */
    @GetMapping("/adjudicaciones/pendientes")
    public PageResponse<AdjudicacionResponse> pendientes(
            @PageableDefault(size = 20, sort = "decididoEn", direction = Sort.Direction.ASC) Pageable pageable) {
        return adjudicacionService.pendientes(pageable);
    }

    /**
     * Detalle de una adjudicación.
     *
     * @param id id positivo
     * @return adjudicación
     */
    @GetMapping("/adjudicaciones/{id}")
    @RequiereRol({ Roles.COMPRADOR, Roles.ADMIN_COMPRAS, Roles.APROBADOR, Roles.AUDITOR })
    public AdjudicacionResponse obtener(@PathVariable @Positive(message = "debe ser un id válido") Long id) {
        return adjudicacionService.obtener(id);
    }

    /**
     * Aprueba o rechaza una adjudicación pendiente.
     *
     * @param id      id positivo
     * @param request decisión
     * @return adjudicación decidida
     */
    @PostMapping("/adjudicaciones/{id}/decision")
    public AdjudicacionResponse decidir(@PathVariable @Positive(message = "debe ser un id válido") Long id,
            @Valid @RequestBody DecisionAdjudicacionRequest request) {
        return adjudicacionService.decidir(id, request);
    }
}
