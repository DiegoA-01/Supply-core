package com.proyecto.supply_core.compra.rfq.controller;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
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
import com.proyecto.supply_core.common.dto.PageResponse;
import com.proyecto.supply_core.compra.rfq.dto.RfqCrearRequest;
import com.proyecto.supply_core.compra.rfq.dto.RfqDatosRequest;
import com.proyecto.supply_core.compra.rfq.dto.RfqResponse;
import com.proyecto.supply_core.compra.rfq.dto.RfqResumenResponse;
import com.proyecto.supply_core.compra.rfq.enums.EstadoRfq;
import com.proyecto.supply_core.compra.rfq.service.RfqService;
import com.proyecto.supply_core.security.annotation.RequiereRol;
import com.proyecto.supply_core.security.context.Roles;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;

/**
 * RFQ (UC-06). Escribe el COMPRADOR; consultan compras, aprobadores y auditoría.
 */
@RequiereModulo(Modulos.RFQ_COTIZACIONES)
@RestController
@RequestMapping("/api/rfqs")
@RequiereRol({ Roles.COMPRADOR, Roles.ADMIN_COMPRAS, Roles.APROBADOR, Roles.AUDITOR })
public class RfqController {

    private final RfqService rfqService;

    /**
     * @param rfqService lógica de RFQ
     */
    public RfqController(RfqService rfqService) {
        this.rfqService = rfqService;
    }

    /**
     * Crea una RFQ en BORRADOR desde una solicitud APROBADA.
     *
     * @param request solicitud y datos
     * @return RFQ con alertas
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @RequiereRol(Roles.COMPRADOR)
    public RfqResponse crear(@Valid @RequestBody RfqCrearRequest request) {
        return rfqService.crear(request);
    }

    /**
     * Edita una RFQ en BORRADOR.
     *
     * @param id      id positivo
     * @param request datos
     * @return RFQ con alertas
     */
    @PutMapping("/{id}")
    @RequiereRol(Roles.COMPRADOR)
    public RfqResponse actualizar(@PathVariable @Positive(message = "debe ser un id válido") Long id,
            @Valid @RequestBody RfqDatosRequest request) {
        return rfqService.actualizar(id, request);
    }

    /**
     * Abre la RFQ y avisa a los invitados.
     *
     * @param id id positivo
     * @return RFQ abierta
     */
    @PostMapping("/{id}/abrir")
    @RequiereRol(Roles.COMPRADOR)
    public RfqResponse abrir(@PathVariable @Positive(message = "debe ser un id válido") Long id) {
        return rfqService.abrir(id);
    }

    /**
     * Cierra la RFQ antes de la fecha límite.
     *
     * @param id id positivo
     * @return RFQ cerrada
     */
    @PostMapping("/{id}/cerrar")
    @RequiereRol(Roles.COMPRADOR)
    public RfqResponse cerrar(@PathVariable @Positive(message = "debe ser un id válido") Long id) {
        return rfqService.cerrar(id);
    }

    /**
     * Cancela la RFQ (la solicitud vuelve a APROBADA).
     *
     * @param id id positivo
     * @return RFQ cancelada
     */
    @PostMapping("/{id}/cancelar")
    @RequiereRol(Roles.COMPRADOR)
    public RfqResponse cancelar(@PathVariable @Positive(message = "debe ser un id válido") Long id) {
        return rfqService.cancelar(id);
    }

    /**
     * Lista RFQ con filtros.
     *
     * @param texto       coincidencia en el consecutivo
     * @param estado      estado
     * @param solicitudId solicitud de origen
     * @param pageable    página (máximo 100) y orden
     * @return página de RFQ
     */
    @GetMapping
    public PageResponse<RfqResumenResponse> buscar(@RequestParam(required = false) String texto,
            @RequestParam(required = false) EstadoRfq estado,
            @RequestParam(required = false) @Positive(message = "debe ser un id válido") Long solicitudId,
            @PageableDefault(size = 20, sort = "creadoEn", direction = Sort.Direction.DESC) Pageable pageable) {
        return rfqService.buscar(texto, estado, solicitudId, pageable);
    }

    /**
     * Detalle con líneas, invitados y alertas.
     *
     * @param id id positivo
     * @return RFQ
     */
    @GetMapping("/{id}")
    public RfqResponse obtener(@PathVariable @Positive(message = "debe ser un id válido") Long id) {
        return rfqService.obtener(id);
    }
}
