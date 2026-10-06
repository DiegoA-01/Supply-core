package com.proyecto.supply_core.compra.orden.service;

import java.util.Set;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.proyecto.supply_core.auditoria.enums.AccionAuditoria;
import com.proyecto.supply_core.auditoria.enums.EntidadAuditada;
import com.proyecto.supply_core.auditoria.service.AuditoriaService;
import com.proyecto.supply_core.common.dto.PageResponse;
import com.proyecto.supply_core.common.exception.AccesoDenegadoException;
import com.proyecto.supply_core.common.exception.NoEncontradoException;
import com.proyecto.supply_core.common.util.Ordenamiento;
import com.proyecto.supply_core.compra.orden.dto.OrdenResponse;
import com.proyecto.supply_core.compra.orden.dto.OrdenResumenResponse;
import com.proyecto.supply_core.compra.orden.entity.OrdenCompra;
import com.proyecto.supply_core.compra.orden.enums.EstadoOrden;
import com.proyecto.supply_core.compra.orden.repository.OrdenCompraRepository;
import com.proyecto.supply_core.notificacion.enums.EventoNotificacion;
import com.proyecto.supply_core.notificacion.service.NotificacionService;
import com.proyecto.supply_core.security.context.Roles;
import com.proyecto.supply_core.security.context.SesionActual;
import com.proyecto.supply_core.security.context.UsuarioAutenticado;

/**
 * UC-12 Confirmar y despachar (lado del proveedor): EMITIDA → ACEPTADA_PROVEEDOR → EN_PREPARACION →
 * DESPACHADA, o EMITIDA → RECHAZADA_PROVEEDOR con motivo. El proveedor solo ve y mueve sus órdenes (una ajena
 * responde 404). Cada paso avisa al comprador; el despacho también a los almacenistas.
 */
@Service
public class OrdenProveedorService {

    /** Campos por los que se puede ordenar. */
    private static final Set<String> CAMPOS_ORDENABLES = Set.of("creadoEn", "fechaEntregaEsperada", "estado");

    private final OrdenCompraService ordenService;
    private final OrdenCompraRepository ordenRepository;
    private final AuditoriaService auditoriaService;
    private final NotificacionService notificacionService;

    /**
     * @param ordenService        apoyo compartido (bloqueo, historial, respuesta)
     * @param ordenRepository     órdenes
     * @param auditoriaService    registro de acciones
     * @param notificacionService avisos
     */
    public OrdenProveedorService(OrdenCompraService ordenService, OrdenCompraRepository ordenRepository,
            AuditoriaService auditoriaService, NotificacionService notificacionService) {
        this.ordenService = ordenService;
        this.ordenRepository = ordenRepository;
        this.auditoriaService = auditoriaService;
        this.notificacionService = notificacionService;
    }

    /**
     * Acepta la orden.
     *
     * @param id orden
     * @return orden ACEPTADA_PROVEEDOR
     */
    @Transactional
    public OrdenResponse aceptar(Long id) {
        return mover(id, EstadoOrden.ACEPTADA_PROVEEDOR, null, "aceptó");
    }

    /**
     * Rechaza la orden con motivo.
     *
     * @param id     orden
     * @param motivo motivo obligatorio
     * @return orden RECHAZADA_PROVEEDOR
     */
    @Transactional
    public OrdenResponse rechazar(Long id, String motivo) {
        return mover(id, EstadoOrden.RECHAZADA_PROVEEDOR, OrdenCompraService.obligatorio(motivo), "rechazó");
    }

    /**
     * Marca la orden en preparación.
     *
     * @param id orden
     * @return orden EN_PREPARACION
     */
    @Transactional
    public OrdenResponse preparar(Long id) {
        return mover(id, EstadoOrden.EN_PREPARACION, null, "está preparando");
    }

    /**
     * Despacha la orden (la evidencia se adjunta en {@code /api/documentos} con entidad ORDEN_COMPRA).
     *
     * @param id          orden
     * @param observacion guía, transportadora u otro dato (opcional)
     * @return orden DESPACHADA
     */
    @Transactional
    public OrdenResponse despachar(Long id, String observacion) {
        OrdenResponse r = mover(id, EstadoOrden.DESPACHADA, observacion == null || observacion.isBlank()
                ? null : observacion.trim(), "despachó");
        notificacionService.notificarRol(Roles.ALMACENISTA, EventoNotificacion.ORDEN_DESPACHADA, r.proveedorRazonSocial()
                + " despachó la orden " + r.consecutivo() + " hacia " + r.bodegaDestinoNombre() + "; prepare la recepción");
        return r;
    }

    /**
     * Órdenes del proveedor de la sesión (sin versiones reemplazadas).
     *
     * @param estado   estado (opcional)
     * @param pageable página y orden
     * @return página de órdenes
     */
    @Transactional(readOnly = true)
    public PageResponse<OrdenResumenResponse> mias(EstadoOrden estado, Pageable pageable) {
        return PageResponse.of(ordenRepository.buscar(null, estado, proveedorDeSesion(),
                Ordenamiento.validar(pageable, CAMPOS_ORDENABLES)), OrdenResumenResponse::of);
    }

    /**
     * Detalle de una orden propia.
     *
     * @param id orden
     * @return orden con líneas e historial
     * @throws NoEncontradoException si no existe o es de otro proveedor
     */
    @Transactional(readOnly = true)
    public OrdenResponse obtener(Long id) {
        Long proveedorId = proveedorDeSesion();
        OrdenCompra o = ordenRepository.findDetalleById(id).filter(x -> x.getProveedor().getId().equals(proveedorId))
                .orElseThrow(() -> new NoEncontradoException("Orden", id));
        return ordenService.respuesta(o);
    }

    // ----------------------------------------------------------------- apoyo

    /**
     * Aplica una transición del proveedor sobre su orden.
     *
     * @param id          orden
     * @param destino     estado destino
     * @param observacion comentario o motivo
     * @param verbo       texto para el aviso ("aceptó", "rechazó"...)
     * @return orden actualizada
     */
    private OrdenResponse mover(Long id, EstadoOrden destino, String observacion, String verbo) {
        Long proveedorId = proveedorDeSesion();
        OrdenCompra o = ordenService.bloquear(id);
        if (!o.getProveedor().getId().equals(proveedorId)) {
            throw new NoEncontradoException("Orden", id);
        }
        OrdenCompraService.exigirTransicion(o, destino);
        o.setEstado(destino);
        ordenService.historial(o, destino, observacion);
        if (o.getUsuarioId() != null) {
            notificacionService.notificar(o.getUsuarioId(), EventoNotificacion.ORDEN_RESPUESTA_PROVEEDOR,
                    o.getProveedor().getRazonSocial() + " " + verbo + " la orden " + o.getConsecutivo() + " v" + o.getVersion()
                            + (observacion == null ? "" : ": " + observacion));
        }
        auditoriaService.exito(AccionAuditoria.ORDEN_PROVEEDOR, EntidadAuditada.ORDEN_COMPRA, id,
                o.getConsecutivo() + " v" + o.getVersion() + " → " + destino + (observacion == null ? "" : "; " + observacion));
        return ordenService.respuesta(o);
    }

    /**
     * Proveedor de la sesión.
     *
     * @return id del proveedor
     * @throws AccesoDenegadoException si el usuario no es de un proveedor
     */
    private static Long proveedorDeSesion() {
        UsuarioAutenticado u = SesionActual.usuario();
        if (!u.esProveedor()) {
            throw new AccesoDenegadoException("Su usuario no pertenece a un proveedor");
        }
        return u.proveedorId();
    }
}
