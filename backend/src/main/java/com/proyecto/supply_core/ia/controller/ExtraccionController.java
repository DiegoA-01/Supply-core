package com.proyecto.supply_core.ia.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.proyecto.supply_core.security.annotation.RequiereModulo;
import com.proyecto.supply_core.security.context.Modulos;
import com.proyecto.supply_core.ia.dto.ConfirmarCampoRequest;
import com.proyecto.supply_core.ia.dto.DigitacionRequest;
import com.proyecto.supply_core.ia.dto.ProcesamientoResponse;
import com.proyecto.supply_core.ia.service.ExtraccionService;
import com.proyecto.supply_core.security.annotation.RequiereRol;
import com.proyecto.supply_core.security.context.Roles;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;

/**
 * Revisión de cotizaciones por archivo (UC-08): ver lo que leyó la IA, confirmar o corregir datos dudosos,
 * digitarla a mano o reintentar la lectura. Compras, y solo con la RFQ ya cerrada (sobre cerrado).
 */
@RequiereModulo(Modulos.IA)
@RestController
@RequestMapping("/api/cotizaciones/{cotizacionId}/extraccion")
@RequiereRol({ Roles.COMPRADOR, Roles.ADMIN_COMPRAS })
public class ExtraccionController {

    private final ExtraccionService extraccionService;

    /**
     * @param extraccionService lectura con IA
     */
    public ExtraccionController(ExtraccionService extraccionService) {
        this.extraccionService = extraccionService;
    }

    /**
     * Estado de la lectura y datos leídos con su confianza.
     *
     * @param cotizacionId id positivo
     * @return procesamiento
     */
    @GetMapping
    @RequiereRol({ Roles.COMPRADOR, Roles.ADMIN_COMPRAS, Roles.AUDITOR })
    public ProcesamientoResponse obtener(@PathVariable @Positive(message = "debe ser un id válido") Long cotizacionId) {
        return extraccionService.obtener(cotizacionId);
    }

    /**
     * Confirma o corrige un dato leído.
     *
     * @param cotizacionId id positivo
     * @param campoId      id positivo del dato
     * @param request      valor correcto
     * @return procesamiento actualizado
     */
    @PatchMapping("/campos/{campoId}")
    public ProcesamientoResponse confirmar(@PathVariable @Positive(message = "debe ser un id válido") Long cotizacionId,
            @PathVariable @Positive(message = "debe ser un id válido") Long campoId,
            @Valid @RequestBody ConfirmarCampoRequest request) {
        return extraccionService.confirmar(cotizacionId, campoId, request.valor());
    }

    /**
     * Digita a mano los precios del documento (reemplaza lo leído por la IA).
     *
     * @param cotizacionId id positivo
     * @param request      líneas
     * @return procesamiento COMPLETADO
     */
    @PutMapping("/lineas")
    public ProcesamientoResponse digitar(@PathVariable @Positive(message = "debe ser un id válido") Long cotizacionId,
            @Valid @RequestBody DigitacionRequest request) {
        return extraccionService.digitar(cotizacionId, request.lineas());
    }

    /**
     * Vuelve a leer el documento con la IA.
     *
     * @param cotizacionId id positivo
     * @return procesamiento PENDIENTE
     */
    @PostMapping("/reintentar")
    public ProcesamientoResponse reintentar(@PathVariable @Positive(message = "debe ser un id válido") Long cotizacionId) {
        return extraccionService.reintentar(cotizacionId);
    }
}
