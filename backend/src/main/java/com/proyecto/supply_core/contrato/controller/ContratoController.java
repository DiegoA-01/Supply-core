package com.proyecto.supply_core.contrato.controller;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.proyecto.supply_core.security.annotation.RequiereModulo;
import com.proyecto.supply_core.security.context.Modulos;
import com.proyecto.supply_core.common.dto.PageResponse;
import com.proyecto.supply_core.contrato.dto.ContratoRequest;
import com.proyecto.supply_core.contrato.dto.ContratoResponse;
import com.proyecto.supply_core.contrato.dto.ContratoResumenResponse;
import com.proyecto.supply_core.contrato.dto.MotivoContratoRequest;
import com.proyecto.supply_core.contrato.dto.OfertaContratoResponse;
import com.proyecto.supply_core.contrato.dto.VigenciaRequest;
import com.proyecto.supply_core.contrato.enums.EstadoContrato;
import com.proyecto.supply_core.contrato.service.ContratoService;
import com.proyecto.supply_core.security.annotation.RequiereRol;
import com.proyecto.supply_core.security.annotation.UsuarioInterno;
import com.proyecto.supply_core.security.context.Roles;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;

/**
 * Contratos con proveedores y precios pactados (paso 18-b). ADMIN_COMPRAS los crea, mueve su fecha de fin y los
 * anula; compras, aprobación, administración y auditoría los consultan; COMPRADOR y ADMIN_COMPRAS ven las ofertas
 * de una solicitud. Solo usuarios internos.
 */
@RequiereModulo(Modulos.COMPRAS)
@RestController
@RequestMapping("/api/contratos")
@UsuarioInterno
public class ContratoController {

    private final ContratoService contratoService;

    /**
     * @param contratoService lógica de contratos
     */
    public ContratoController(ContratoService contratoService) {
        this.contratoService = contratoService;
    }

    /**
     * Crea un contrato con sus precios pactados.
     *
     * @param request datos validados
     * @return contrato creado (HTTP 201)
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @RequiereRol(Roles.ADMIN_COMPRAS)
    public ContratoResponse crear(@Valid @RequestBody ContratoRequest request) {
        return contratoService.crear(request);
    }

    /**
     * Mueve la fecha de fin de un contrato activo.
     *
     * @param id      id positivo
     * @param request nueva fecha de fin
     * @return contrato actualizado
     */
    @PatchMapping("/{id}/vigencia")
    @RequiereRol(Roles.ADMIN_COMPRAS)
    public ContratoResponse cambiarVigencia(@PathVariable @Positive(message = "debe ser un id válido") Long id,
            @Valid @RequestBody VigenciaRequest request) {
        return contratoService.cambiarVigencia(id, request.vigenteHasta());
    }

    /**
     * Anula un contrato activo.
     *
     * @param id      id positivo
     * @param request motivo
     * @return contrato anulado
     */
    @PatchMapping("/{id}/anular")
    @RequiereRol(Roles.ADMIN_COMPRAS)
    public ContratoResponse anular(@PathVariable @Positive(message = "debe ser un id válido") Long id,
            @Valid @RequestBody MotivoContratoRequest request) {
        return contratoService.anular(id, request.motivo());
    }

    /**
     * Ofertas de contratos vigentes para una solicitud aprobada: cuánto costaría y si se puede emitir la orden
     * directa. Solo informa.
     *
     * @param solicitudId solicitud APROBADA
     * @return ofertas, primero las que se pueden emitir
     */
    @GetMapping("/ofertas")
    @RequiereRol({ Roles.COMPRADOR, Roles.ADMIN_COMPRAS })
    public List<OfertaContratoResponse> ofertas(
            @RequestParam @Positive(message = "debe ser un id válido") Long solicitudId) {
        return contratoService.ofertas(solicitudId);
    }

    /**
     * Lista contratos.
     *
     * @param proveedorId proveedor
     * @param estado      estado
     * @param vigente     {@code true} para ver solo los que se pueden usar hoy
     * @param pageable    página (máximo 100) y orden
     * @return página de contratos
     */
    @GetMapping
    @RequiereRol({ Roles.ADMIN_COMPRAS, Roles.COMPRADOR, Roles.APROBADOR, Roles.ADMINISTRATIVO, Roles.AUDITOR })
    public PageResponse<ContratoResumenResponse> buscar(
            @RequestParam(required = false) @Positive(message = "debe ser un id válido") Long proveedorId,
            @RequestParam(required = false) EstadoContrato estado, @RequestParam(required = false) Boolean vigente,
            @PageableDefault(size = 20, sort = "vigenteHasta", direction = Sort.Direction.DESC) Pageable pageable) {
        return contratoService.buscar(proveedorId, estado, vigente, pageable);
    }

    /**
     * Detalle de un contrato con lo consumido de cada tope.
     *
     * @param id id positivo
     * @return contrato
     */
    @GetMapping("/{id}")
    @RequiereRol({ Roles.ADMIN_COMPRAS, Roles.COMPRADOR, Roles.APROBADOR, Roles.ADMINISTRATIVO, Roles.AUDITOR })
    public ContratoResponse obtener(@PathVariable @Positive(message = "debe ser un id válido") Long id) {
        return contratoService.obtener(id);
    }
}
