package com.proyecto.supply_core.factura.controller;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.proyecto.supply_core.security.annotation.RequiereModulo;
import com.proyecto.supply_core.security.context.Modulos;
import com.proyecto.supply_core.common.dto.PageResponse;
import com.proyecto.supply_core.factura.dto.FacturaResponse;
import com.proyecto.supply_core.factura.dto.MotivoFacturaRequest;
import com.proyecto.supply_core.factura.dto.RegistrarFacturaRequest;
import com.proyecto.supply_core.factura.enums.EstadoFactura;
import com.proyecto.supply_core.factura.service.FacturaService;
import com.proyecto.supply_core.security.annotation.RequiereRol;
import com.proyecto.supply_core.security.context.Roles;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;

/**
 * Facturas de proveedores y cruce de tres vías. Registra, cruza y anula el ADMINISTRATIVO; aprueba las que no
 * cuadran ADMIN_COMPRAS; consultan compras, aprobadores y auditoría.
 */
@RequiereModulo(Modulos.ORDENES_PAGOS)
@RestController
@RequestMapping("/api")
@RequiereRol({ Roles.ADMINISTRATIVO, Roles.ADMIN_COMPRAS, Roles.COMPRADOR, Roles.APROBADOR, Roles.AUDITOR })
public class FacturaController {

    private final FacturaService facturaService;

    /**
     * @param facturaService lógica de facturas
     */
    public FacturaController(FacturaService facturaService) {
        this.facturaService = facturaService;
    }

    /**
     * Registra la factura del proveedor y la cruza con lo recibido.
     *
     * @param ordenId orden con recepciones
     * @param request número, fecha y montos
     * @return factura CUADRADA o EN_REVISION (HTTP 201)
     */
    @PostMapping("/ordenes/{ordenId}/facturas")
    @ResponseStatus(HttpStatus.CREATED)
    @RequiereRol(Roles.ADMINISTRATIVO)
    public FacturaResponse registrar(@PathVariable @Positive(message = "debe ser un id válido") Long ordenId,
            @Valid @RequestBody RegistrarFacturaRequest request) {
        return facturaService.registrar(ordenId, request);
    }

    /**
     * Facturas de una orden (todas sus versiones, incluidas las anuladas).
     *
     * @param ordenId cualquier versión de la orden
     * @return facturas
     */
    @GetMapping("/ordenes/{ordenId}/facturas")
    public List<FacturaResponse> deOrden(@PathVariable @Positive(message = "debe ser un id válido") Long ordenId) {
        return facturaService.deOrden(ordenId);
    }

    /**
     * Lista facturas.
     *
     * @param estado      estado
     * @param proveedorId proveedor
     * @param pageable    página (máximo 100) y orden
     * @return página de facturas
     */
    @GetMapping("/facturas")
    @RequiereRol({ Roles.ADMINISTRATIVO, Roles.ADMIN_COMPRAS, Roles.AUDITOR })
    public PageResponse<FacturaResponse> buscar(@RequestParam(required = false) EstadoFactura estado,
            @RequestParam(required = false) @Positive(message = "debe ser un id válido") Long proveedorId,
            @PageableDefault(size = 20, sort = "creadoEn", direction = Sort.Direction.DESC) Pageable pageable) {
        return facturaService.buscar(estado, proveedorId, pageable);
    }

    /**
     * Detalle de una factura con su cruce.
     *
     * @param id id positivo
     * @return factura
     */
    @GetMapping("/facturas/{id}")
    public FacturaResponse obtener(@PathVariable @Positive(message = "debe ser un id válido") Long id) {
        return facturaService.obtener(id);
    }

    /**
     * Adjunta el PDF de la factura (multipart, campo {@code archivo}); una sola vez.
     *
     * @param id      id positivo
     * @param archivo PDF o imagen
     * @return factura con su documento
     */
    @PostMapping(path = "/facturas/{id}/archivo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RequiereRol(Roles.ADMINISTRATIVO)
    public FacturaResponse adjuntarArchivo(@PathVariable @Positive(message = "debe ser un id válido") Long id,
            @RequestPart("archivo") MultipartFile archivo) {
        return facturaService.adjuntarArchivo(id, archivo);
    }

    /**
     * Vuelve a cruzar la factura con lo recibido hoy.
     *
     * @param id factura CUADRADA o EN_REVISION
     * @return factura con el cruce actualizado
     */
    @PostMapping("/facturas/{id}/recruzar")
    @RequiereRol(Roles.ADMINISTRATIVO)
    public FacturaResponse recruzar(@PathVariable @Positive(message = "debe ser un id válido") Long id) {
        return facturaService.recruzar(id);
    }

    /**
     * Aprueba una factura EN_REVISION con motivo (quien la registró no puede aprobarla).
     *
     * @param id      factura EN_REVISION
     * @param request motivo
     * @return factura APROBADA
     */
    @PostMapping("/facturas/{id}/aprobar")
    @RequiereRol(Roles.ADMIN_COMPRAS)
    public FacturaResponse aprobar(@PathVariable @Positive(message = "debe ser un id válido") Long id,
            @Valid @RequestBody MotivoFacturaRequest request) {
        return facturaService.aprobar(id, request.motivo());
    }

    /**
     * Anula una factura registrada por error.
     *
     * @param id      id positivo
     * @param request motivo
     * @return factura ANULADA
     */
    @PostMapping("/facturas/{id}/anular")
    @RequiereRol({ Roles.ADMINISTRATIVO, Roles.ADMIN_COMPRAS })
    public FacturaResponse anular(@PathVariable @Positive(message = "debe ser un id válido") Long id,
            @Valid @RequestBody MotivoFacturaRequest request) {
        return facturaService.anular(id, request.motivo());
    }
}
