package com.proyecto.supply_core.compra.solicitud.controller;

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
import com.proyecto.supply_core.compra.solicitud.dto.DecisionRequest;
import com.proyecto.supply_core.compra.solicitud.dto.EnviarSolicitudRequest;
import com.proyecto.supply_core.compra.solicitud.dto.SolicitudRequest;
import com.proyecto.supply_core.compra.solicitud.dto.SolicitudResponse;
import com.proyecto.supply_core.compra.solicitud.dto.SolicitudResumenResponse;
import com.proyecto.supply_core.compra.solicitud.enums.EstadoSolicitud;
import com.proyecto.supply_core.compra.solicitud.service.AprobacionSolicitudService;
import com.proyecto.supply_core.compra.solicitud.service.SolicitudService;
import com.proyecto.supply_core.security.annotation.RequiereRol;
import com.proyecto.supply_core.security.annotation.UsuarioInterno;
import com.proyecto.supply_core.security.context.Roles;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;

/**
 * Solicitudes de compra (UC-04) y su aprobación (UC-05). Solo usuarios internos.
 * <p>El solicitante crea, edita y envía; quien tenga el rol de la política decide
 * (la verificación del rol y la segregación de funciones las hace el Service).</p>
 */
@RequiereModulo(Modulos.COMPRAS)
@RestController
@RequestMapping("/api/solicitudes")
@UsuarioInterno
public class SolicitudController {

    private final SolicitudService solicitudService;
    private final AprobacionSolicitudService aprobacionService;

    /**
     * @param solicitudService  ciclo de la solicitud
     * @param aprobacionService decisiones
     */
    public SolicitudController(SolicitudService solicitudService, AprobacionSolicitudService aprobacionService) {
        this.solicitudService = solicitudService;
        this.aprobacionService = aprobacionService;
    }

    /**
     * Crea una solicitud en BORRADOR.
     *
     * @param request datos y líneas
     * @return solicitud creada
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @RequiereRol(Roles.SOLICITANTE)
    public SolicitudResponse crear(@Valid @RequestBody SolicitudRequest request) {
        return solicitudService.crear(request);
    }

    /**
     * Edita una solicitud propia en BORRADOR o DEVUELTA (reemplaza sus líneas).
     *
     * @param id      id positivo
     * @param request datos y líneas
     * @return solicitud actualizada
     */
    @PutMapping("/{id}")
    @RequiereRol(Roles.SOLICITANTE)
    public SolicitudResponse actualizar(@PathVariable @Positive(message = "debe ser un id válido") Long id,
            @Valid @RequestBody SolicitudRequest request) {
        return solicitudService.actualizar(id, request);
    }

    /**
     * Envía la solicitud a aprobación (o queda APROBADA si ninguna política aplica). El cuerpo es opcional: con una
     * justificación se puede enviar aunque exceda el presupuesto del centro de costo o repita un producto ya pedido.
     *
     * @param id      id positivo
     * @param request justificación de la excepción (opcional)
     * @return solicitud enviada
     */
    @PostMapping("/{id}/enviar")
    @RequiereRol(Roles.SOLICITANTE)
    public SolicitudResponse enviar(@PathVariable @Positive(message = "debe ser un id válido") Long id,
            @Valid @RequestBody(required = false) EnviarSolicitudRequest request) {
        return solicitudService.enviar(id, request == null ? null : request.justificacionExcepcion());
    }

    /**
     * Cancela una solicitud que aún no pasó a RFQ.
     *
     * @param id id positivo
     * @return solicitud cancelada
     */
    @PostMapping("/{id}/cancelar")
    @RequiereRol({ Roles.SOLICITANTE, Roles.ADMIN_COMPRAS })
    public SolicitudResponse cancelar(@PathVariable @Positive(message = "debe ser un id válido") Long id) {
        return solicitudService.cancelar(id);
    }

    /**
     * Aprueba, rechaza o devuelve una solicitud pendiente.
     *
     * @param id      id positivo
     * @param request decisión y observaciones
     * @return solicitud con su nuevo estado
     */
    @PostMapping("/{id}/decision")
    public SolicitudResponse decidir(@PathVariable @Positive(message = "debe ser un id válido") Long id,
            @Valid @RequestBody DecisionRequest request) {
        return aprobacionService.decidir(id, request);
    }

    /**
     * Bandeja del aprobador: pendientes que le corresponden según su rol.
     *
     * @param pageable página (máximo 100) y orden
     * @return página de pendientes
     */
    @GetMapping("/pendientes-aprobacion")
    public PageResponse<SolicitudResumenResponse> pendientes(
            @PageableDefault(size = 20, sort = "enviadoEn", direction = Sort.Direction.ASC) Pageable pageable) {
        return aprobacionService.pendientes(pageable);
    }

    /**
     * Lista solicitudes (el solicitante solo ve las suyas).
     *
     * @param texto         coincidencia en el consecutivo
     * @param estado        estado
     * @param solicitanteId solicitante
     * @param centroCostoId centro de costo
     * @param pageable      página (máximo 100) y orden
     * @return página de solicitudes
     */
    @GetMapping
    public PageResponse<SolicitudResumenResponse> buscar(@RequestParam(required = false) String texto,
            @RequestParam(required = false) EstadoSolicitud estado,
            @RequestParam(required = false) @Positive(message = "debe ser un id válido") Long solicitanteId,
            @RequestParam(required = false) @Positive(message = "debe ser un id válido") Long centroCostoId,
            @PageableDefault(size = 20, sort = "creadoEn", direction = Sort.Direction.DESC) Pageable pageable) {
        return solicitudService.buscar(texto, estado, solicitanteId, centroCostoId, pageable);
    }

    /**
     * Detalle con líneas e historial de decisiones.
     *
     * @param id id positivo
     * @return solicitud
     */
    @GetMapping("/{id}")
    public SolicitudResponse obtener(@PathVariable @Positive(message = "debe ser un id válido") Long id) {
        return solicitudService.obtener(id);
    }
}
