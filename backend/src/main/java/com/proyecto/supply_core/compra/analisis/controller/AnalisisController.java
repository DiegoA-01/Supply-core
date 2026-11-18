package com.proyecto.supply_core.compra.analisis.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.proyecto.supply_core.security.annotation.RequiereModulo;
import com.proyecto.supply_core.security.context.Modulos;
import com.proyecto.supply_core.compra.analisis.dto.AnalisisResponse;
import com.proyecto.supply_core.compra.analisis.service.AnalisisService;
import com.proyecto.supply_core.security.annotation.RequiereRol;
import com.proyecto.supply_core.security.context.Roles;

import jakarta.validation.constraints.Positive;

/**
 * Análisis comparativo de una RFQ (UC-08). Genera y publica el COMPRADOR; consultan compras, aprobadores y
 * auditoría.
 */
@RequiereModulo(Modulos.RFQ_COTIZACIONES)
@RestController
@RequestMapping("/api/rfqs/{rfqId}/analisis")
@RequiereRol({ Roles.COMPRADOR, Roles.ADMIN_COMPRAS, Roles.APROBADOR, Roles.AUDITOR })
public class AnalisisController {

    private final AnalisisService analisisService;

    /**
     * @param analisisService lógica del análisis
     */
    public AnalisisController(AnalisisService analisisService) {
        this.analisisService = analisisService;
    }

    /**
     * Último análisis (ranking, criterios, alertas y pendientes).
     *
     * @param rfqId id positivo de la RFQ
     * @return análisis
     */
    @GetMapping
    public AnalisisResponse obtener(@PathVariable @Positive(message = "debe ser un id válido") Long rfqId) {
        return analisisService.obtener(rfqId);
    }

    /**
     * Genera (o regenera) el análisis en borrador.
     *
     * @param rfqId id positivo de la RFQ
     * @return análisis
     */
    @PostMapping
    @RequiereRol(Roles.COMPRADOR)
    public AnalisisResponse generar(@PathVariable @Positive(message = "debe ser un id válido") Long rfqId) {
        return analisisService.generar(rfqId);
    }

    /**
     * Publica el análisis (exige cotizaciones por archivo revisadas).
     *
     * @param rfqId id positivo de la RFQ
     * @return análisis publicado
     */
    @PostMapping("/publicar")
    @RequiereRol(Roles.COMPRADOR)
    public AnalisisResponse publicar(@PathVariable @Positive(message = "debe ser un id válido") Long rfqId) {
        return analisisService.publicar(rfqId);
    }

    /**
     * Pide a la IA una explicación general del ranking.
     *
     * @param rfqId id positivo de la RFQ
     * @return análisis con la explicación
     */
    @RequiereModulo(Modulos.IA)
    @PostMapping("/explicar")
    @RequiereRol(Roles.COMPRADOR)
    public AnalisisResponse explicar(@PathVariable @Positive(message = "debe ser un id válido") Long rfqId) {
        return analisisService.explicar(rfqId);
    }
}
