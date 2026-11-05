package com.proyecto.supply_core.compra.cotizacion.controller;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.proyecto.supply_core.security.annotation.RequiereModulo;
import com.proyecto.supply_core.security.context.Modulos;
import com.proyecto.supply_core.compra.cotizacion.dto.CotizacionRequest;
import com.proyecto.supply_core.compra.cotizacion.dto.CotizacionResponse;
import com.proyecto.supply_core.compra.cotizacion.service.CotizacionService;
import com.proyecto.supply_core.security.annotation.RequiereRol;
import com.proyecto.supply_core.security.context.Roles;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;

/**
 * Cotización del proveedor para una RFQ a la que fue invitado (UC-07). Solo PROVEEDOR.
 */
@RequiereModulo(Modulos.RFQ_COTIZACIONES)
@RestController
@RequestMapping("/api/portal/rfqs/{rfqId}/cotizacion")
@RequiereRol(Roles.PROVEEDOR)
public class PortalCotizacionController {

    private final CotizacionService cotizacionService;

    /**
     * @param cotizacionService lógica de cotizaciones
     */
    public PortalCotizacionController(CotizacionService cotizacionService) {
        this.cotizacionService = cotizacionService;
    }

    /**
     * Mi cotización para esta RFQ.
     *
     * @param rfqId id positivo de la RFQ
     * @return cotización
     */
    @GetMapping
    public CotizacionResponse mia(@PathVariable @Positive(message = "debe ser un id válido") Long rfqId) {
        return cotizacionService.mia(rfqId);
    }

    /**
     * Guarda el borrador por formulario (crea o reemplaza precios y condiciones).
     *
     * @param rfqId   id positivo de la RFQ
     * @param request datos y precios
     * @return borrador con montos calculados
     */
    @PutMapping
    public CotizacionResponse guardar(@PathVariable @Positive(message = "debe ser un id válido") Long rfqId,
            @Valid @RequestBody CotizacionRequest request) {
        return cotizacionService.guardar(rfqId, request);
    }

    /**
     * Sube el documento original de la cotización (multipart, campo {@code archivo}).
     *
     * @param rfqId   id positivo de la RFQ
     * @param archivo PDF, Excel o imagen (máx. 10 MB)
     * @return borrador con el documento enlazado
     */
    @PostMapping(path = "/archivo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public CotizacionResponse subirArchivo(@PathVariable @Positive(message = "debe ser un id válido") Long rfqId,
            @RequestPart("archivo") MultipartFile archivo) {
        return cotizacionService.subirArchivo(rfqId, archivo);
    }

    /**
     * Envía la cotización al comprador (ya no se podrá editar).
     *
     * @param rfqId id positivo de la RFQ
     * @return cotización enviada
     */
    @PostMapping("/enviar")
    public CotizacionResponse enviar(@PathVariable @Positive(message = "debe ser un id válido") Long rfqId) {
        return cotizacionService.enviar(rfqId);
    }
}
