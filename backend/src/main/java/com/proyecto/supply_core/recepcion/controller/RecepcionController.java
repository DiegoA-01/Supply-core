package com.proyecto.supply_core.recepcion.controller;

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
import com.proyecto.supply_core.recepcion.dto.CerrarRecepcionRequest;
import com.proyecto.supply_core.recepcion.dto.LineaRecepcionResponse;
import com.proyecto.supply_core.recepcion.dto.RecepcionResponse;
import com.proyecto.supply_core.recepcion.dto.RegistrarRecepcionRequest;
import com.proyecto.supply_core.recepcion.dto.ResolverExcesoRequest;
import com.proyecto.supply_core.recepcion.dto.ResumenRecepcionResponse;
import com.proyecto.supply_core.recepcion.service.RecepcionService;
import com.proyecto.supply_core.security.annotation.RequiereRol;
import com.proyecto.supply_core.security.context.Roles;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;

/**
 * Recepción de mercancía (UC-13). Registra y cierra el ALMACENISTA; el excedente lo resuelve compras;
 * consultan compras, administrativo, aprobadores y auditoría.
 */
@RequiereModulo(Modulos.RECEPCION)
@RestController
@RequestMapping("/api")
@RequiereRol({ Roles.ALMACENISTA, Roles.COMPRADOR, Roles.ADMIN_COMPRAS, Roles.ADMINISTRATIVO, Roles.APROBADOR,
        Roles.AUDITOR })
public class RecepcionController {

    private final RecepcionService recepcionService;

    /**
     * @param recepcionService lógica de recepción
     */
    public RecepcionController(RecepcionService recepcionService) {
        this.recepcionService = recepcionService;
    }

    /**
     * Registra una recepción: lo recibido y rechazado por línea; lo aceptado sube el stock.
     *
     * @param ordenId orden DESPACHADA o recibida parcialmente
     * @param request bodega, observaciones y líneas
     * @return recepción registrada (HTTP 201)
     */
    @PostMapping("/ordenes/{ordenId}/recepciones")
    @ResponseStatus(HttpStatus.CREATED)
    @RequiereRol(Roles.ALMACENISTA)
    public RecepcionResponse registrar(@PathVariable @Positive(message = "debe ser un id válido") Long ordenId,
            @Valid @RequestBody RegistrarRecepcionRequest request) {
        return recepcionService.registrar(ordenId, request);
    }

    /**
     * Avance de la recepción de una orden: pedido, aceptado, rechazado y faltante por línea, y las recepciones.
     *
     * @param ordenId orden
     * @return resumen de recepción
     */
    @GetMapping("/ordenes/{ordenId}/recepciones")
    public ResumenRecepcionResponse deOrden(@PathVariable @Positive(message = "debe ser un id válido") Long ordenId) {
        return recepcionService.deOrden(ordenId);
    }

    /**
     * Cierra la recepción de la orden y evalúa al proveedor.
     *
     * @param ordenId orden recibida (parcial o total)
     * @param request motivo (obligatorio si se cierra con faltante)
     * @return estado final de la recepción
     */
    @PostMapping("/ordenes/{ordenId}/recepciones/cerrar")
    @RequiereRol({ Roles.ALMACENISTA, Roles.ADMIN_COMPRAS })
    public ResumenRecepcionResponse cerrar(@PathVariable @Positive(message = "debe ser un id válido") Long ordenId,
            @Valid @RequestBody CerrarRecepcionRequest request) {
        return recepcionService.cerrar(ordenId, request.motivo());
    }

    /**
     * Detalle de una recepción.
     *
     * @param id id positivo
     * @return recepción con sus líneas
     */
    @GetMapping("/recepciones/{id}")
    public RecepcionResponse obtener(@PathVariable @Positive(message = "debe ser un id válido") Long id) {
        return recepcionService.obtener(id);
    }

    /**
     * Resuelve el excedente de una línea: aceptarlo (entra al stock) o devolverlo.
     *
     * @param id      línea de recepción EN_REVISION
     * @param request decisión y motivo
     * @return línea resuelta
     */
    @PostMapping("/recepciones/lineas/{id}/resolver-exceso")
    @RequiereRol({ Roles.COMPRADOR, Roles.ADMIN_COMPRAS })
    public LineaRecepcionResponse resolverExceso(@PathVariable @Positive(message = "debe ser un id válido") Long id,
            @Valid @RequestBody ResolverExcesoRequest request) {
        return recepcionService.resolverExceso(id, request.decision(), request.motivo());
    }
}
