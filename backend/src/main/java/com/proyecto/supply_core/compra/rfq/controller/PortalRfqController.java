package com.proyecto.supply_core.compra.rfq.controller;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.proyecto.supply_core.security.annotation.RequiereModulo;
import com.proyecto.supply_core.security.context.Modulos;
import com.proyecto.supply_core.common.dto.PageResponse;
import com.proyecto.supply_core.compra.rfq.dto.RfqPortalResponse;
import com.proyecto.supply_core.compra.rfq.dto.RfqResumenResponse;
import com.proyecto.supply_core.compra.rfq.service.RfqService;
import com.proyecto.supply_core.security.annotation.RequiereRol;
import com.proyecto.supply_core.security.context.Roles;

import jakarta.validation.constraints.Positive;

/**
 * RFQ vistas por el proveedor (solo PROVEEDOR): únicamente aquellas a las que fue invitado
 * y que ya se abrieron. No ve a los demás invitados.
 */
@RequiereModulo(Modulos.RFQ_COTIZACIONES)
@RestController
@RequestMapping("/api/portal/rfqs")
@RequiereRol(Roles.PROVEEDOR)
public class PortalRfqController {

    private final RfqService rfqService;

    /**
     * @param rfqService lógica de RFQ
     */
    public PortalRfqController(RfqService rfqService) {
        this.rfqService = rfqService;
    }

    /**
     * Mis invitaciones, las de fecha límite más próxima primero.
     *
     * @param pageable página (máximo 100) y orden
     * @return página de RFQ
     */
    @GetMapping
    public PageResponse<RfqResumenResponse> invitadas(
            @PageableDefault(size = 20, sort = "fechaLimite", direction = Sort.Direction.ASC) Pageable pageable) {
        return rfqService.invitadas(pageable);
    }

    /**
     * Detalle de una RFQ a la que fui invitado.
     *
     * @param id id positivo
     * @return RFQ sin datos internos
     */
    @GetMapping("/{id}")
    public RfqPortalResponse obtener(@PathVariable @Positive(message = "debe ser un id válido") Long id) {
        return rfqService.obtenerParaProveedor(id);
    }
}
