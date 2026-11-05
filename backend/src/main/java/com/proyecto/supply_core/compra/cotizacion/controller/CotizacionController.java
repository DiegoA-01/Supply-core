package com.proyecto.supply_core.compra.cotizacion.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.proyecto.supply_core.security.annotation.RequiereModulo;
import com.proyecto.supply_core.security.context.Modulos;
import com.proyecto.supply_core.compra.cotizacion.dto.CotizacionResponse;
import com.proyecto.supply_core.compra.cotizacion.service.CotizacionService;
import com.proyecto.supply_core.security.annotation.RequiereRol;
import com.proyecto.supply_core.security.context.Roles;

import jakarta.validation.constraints.Positive;

/**
 * Cotizaciones recibidas en una RFQ (vista de compras). Con la RFQ ABIERTA salen selladas.
 */
@RequiereModulo(Modulos.RFQ_COTIZACIONES)
@RestController
@RequestMapping("/api/rfqs/{rfqId}/cotizaciones")
@RequiereRol({ Roles.COMPRADOR, Roles.ADMIN_COMPRAS, Roles.APROBADOR, Roles.AUDITOR })
public class CotizacionController {

    private final CotizacionService cotizacionService;

    /**
     * @param cotizacionService lógica de cotizaciones
     */
    public CotizacionController(CotizacionService cotizacionService) {
        this.cotizacionService = cotizacionService;
    }

    /**
     * Cotizaciones enviadas, de la primera a la última.
     *
     * @param rfqId id positivo de la RFQ
     * @return cotizaciones (selladas si la RFQ sigue abierta)
     */
    @GetMapping
    public List<CotizacionResponse> listar(@PathVariable @Positive(message = "debe ser un id válido") Long rfqId) {
        return cotizacionService.deRfq(rfqId);
    }
}
