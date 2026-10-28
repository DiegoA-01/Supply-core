package com.proyecto.supply_core.pago.controller;

import java.time.LocalDate;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
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
import com.proyecto.supply_core.pago.dto.AnularPagoRequest;
import com.proyecto.supply_core.pago.dto.PagoResponse;
import com.proyecto.supply_core.pago.dto.PagosOrdenResponse;
import com.proyecto.supply_core.pago.dto.RegistrarPagoRequest;
import com.proyecto.supply_core.pago.enums.EstadoPago;
import com.proyecto.supply_core.pago.service.PagoService;
import com.proyecto.supply_core.security.annotation.RequiereRol;
import com.proyecto.supply_core.security.context.Roles;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;

/**
 * Pagos de órdenes de compra (UC-11). Registra, adjunta comprobante y anula el ADMINISTRATIVO; consultan
 * compras, aprobadores y auditoría.
 */
@RequiereModulo(Modulos.ORDENES_PAGOS)
@RestController
@RequestMapping("/api")
@RequiereRol({ Roles.ADMINISTRATIVO, Roles.COMPRADOR, Roles.ADMIN_COMPRAS, Roles.APROBADOR, Roles.AUDITOR })
public class PagoController {

    private final PagoService pagoService;

    /**
     * @param pagoService lógica de pagos
     */
    public PagoController(PagoService pagoService) {
        this.pagoService = pagoService;
    }

    /**
     * Registra un pago de la orden (sin comprobante queda con evidencia pendiente).
     *
     * @param ordenId versión vigente de la orden
     * @param request método, valor, fecha y referencia
     * @return pago REGISTRADO
     */
    @PostMapping("/ordenes/{ordenId}/pagos")
    @ResponseStatus(HttpStatus.CREATED)
    @RequiereRol(Roles.ADMINISTRATIVO)
    public PagoResponse registrar(@PathVariable @Positive(message = "debe ser un id válido") Long ordenId,
            @Valid @RequestBody RegistrarPagoRequest request) {
        return pagoService.registrar(ordenId, request);
    }

    /**
     * Total, pagado, saldo y pagos de una orden (todas sus versiones).
     *
     * @param ordenId cualquier versión de la orden
     * @return resumen de pagos
     */
    @GetMapping("/ordenes/{ordenId}/pagos")
    public PagosOrdenResponse deOrden(@PathVariable @Positive(message = "debe ser un id válido") Long ordenId) {
        return pagoService.deOrden(ordenId);
    }

    /**
     * Lista pagos.
     *
     * @param estado             estado
     * @param evidenciaPendiente {@code true} = solo los que no tienen comprobante
     * @param proveedorId        proveedor
     * @param desde              fecha de pago desde (AAAA-MM-DD)
     * @param hasta              fecha de pago hasta (AAAA-MM-DD)
     * @param pageable           página (máximo 100) y orden
     * @return página de pagos
     */
    @GetMapping("/pagos")
    @RequiereRol({ Roles.ADMINISTRATIVO, Roles.ADMIN_COMPRAS, Roles.AUDITOR })
    public PageResponse<PagoResponse> buscar(@RequestParam(required = false) EstadoPago estado,
            @RequestParam(required = false) Boolean evidenciaPendiente,
            @RequestParam(required = false) @Positive(message = "debe ser un id válido") Long proveedorId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @PageableDefault(size = 20, sort = "fechaPago", direction = Sort.Direction.DESC) Pageable pageable) {
        return pagoService.buscar(estado, evidenciaPendiente, proveedorId, desde, hasta, pageable);
    }

    /**
     * Detalle de un pago.
     *
     * @param id id positivo
     * @return pago
     */
    @GetMapping("/pagos/{id}")
    public PagoResponse obtener(@PathVariable @Positive(message = "debe ser un id válido") Long id) {
        return pagoService.obtener(id);
    }

    /**
     * Adjunta el comprobante (multipart, campo {@code archivo}); una sola vez.
     *
     * @param id      id positivo
     * @param archivo PDF o imagen
     * @return pago sin evidencia pendiente
     */
    @PostMapping(path = "/pagos/{id}/comprobante", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RequiereRol(Roles.ADMINISTRATIVO)
    public PagoResponse adjuntarComprobante(@PathVariable @Positive(message = "debe ser un id válido") Long id,
            @RequestPart("archivo") MultipartFile archivo) {
        return pagoService.adjuntarComprobante(id, archivo);
    }

    /**
     * Anula un pago registrado por error.
     *
     * @param id      id positivo
     * @param request motivo
     * @return pago ANULADO
     */
    @PostMapping("/pagos/{id}/anular")
    @RequiereRol(Roles.ADMINISTRATIVO)
    public PagoResponse anular(@PathVariable @Positive(message = "debe ser un id válido") Long id,
            @Valid @RequestBody AnularPagoRequest request) {
        return pagoService.anular(id, request.motivo());
    }
}
