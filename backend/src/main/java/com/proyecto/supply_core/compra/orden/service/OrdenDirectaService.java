package com.proyecto.supply_core.compra.orden.service;

import java.math.RoundingMode;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.proyecto.supply_core.auditoria.enums.AccionAuditoria;
import com.proyecto.supply_core.auditoria.enums.EntidadAuditada;
import com.proyecto.supply_core.auditoria.service.AuditoriaService;
import com.proyecto.supply_core.common.exception.AccesoDenegadoException;
import com.proyecto.supply_core.common.exception.NegocioException;
import com.proyecto.supply_core.common.exception.NoEncontradoException;
import com.proyecto.supply_core.common.util.ConsecutivoGenerator;
import com.proyecto.supply_core.compra.orden.dto.EmitirDirectaRequest;
import com.proyecto.supply_core.compra.orden.dto.OrdenResponse;
import com.proyecto.supply_core.compra.orden.entity.OrdenCompra;
import com.proyecto.supply_core.compra.orden.entity.OrdenCompraLinea;
import com.proyecto.supply_core.compra.orden.enums.EstadoOrden;
import com.proyecto.supply_core.compra.orden.repository.OrdenCompraRepository;
import com.proyecto.supply_core.compra.solicitud.entity.SolicitudCompra;
import com.proyecto.supply_core.compra.solicitud.enums.EstadoSolicitud;
import com.proyecto.supply_core.compra.solicitud.repository.SolicitudCompraRepository;
import com.proyecto.supply_core.contrato.dto.CalculoContrato;
import com.proyecto.supply_core.contrato.entity.Contrato;
import com.proyecto.supply_core.contrato.repository.ContratoRepository;
import com.proyecto.supply_core.contrato.service.ContratoService;
import com.proyecto.supply_core.inventario.entity.Bodega;
import com.proyecto.supply_core.inventario.repository.BodegaRepository;
import com.proyecto.supply_core.notificacion.enums.EventoNotificacion;
import com.proyecto.supply_core.proveedor.enums.EstadoProveedor;
import com.proyecto.supply_core.security.context.SesionActual;
import com.proyecto.supply_core.security.context.UsuarioAutenticado;

/**
 * Orden directa al precio pactado de un contrato, sin RFQ (paso 18-b). Es una vía más para emitir una orden de la
 * versión 1; desde ahí la orden sigue el mismo ciclo: aceptación del proveedor, versiones, despacho, recepción,
 * factura y pago.
 * <p>Java valida todo y la persona decide emitir. La orden solo se emite si:</p>
 * <ul>
 *   <li>la solicitud está APROBADA y quien emite no es su solicitante (segregación de funciones);</li>
 *   <li>el contrato es ACTIVO y está dentro de su vigencia hoy, y su proveedor sigue HABILITADO;</li>
 *   <li>el contrato incluye todos los productos de la solicitud;</li>
 *   <li>cada cantidad cabe en lo que queda del tope de su precio pactado;</li>
 *   <li>el subtotal al precio pactado no supera el monto con que se aprobó la solicitud, y ese monto era completo.
 *       Si lo supera, debe ir por RFQ o corregirse la solicitud: así la orden directa no esquiva la aprobación.</li>
 * </ul>
 * <p>Al emitirse la solicitud pasa a ADJUDICADA. Las líneas guardan el precio pactado del que vienen para
 * descontar del tope.</p>
 */
@Service
public class OrdenDirectaService {

    private static final Logger log = LoggerFactory.getLogger(OrdenDirectaService.class);

    private final SolicitudCompraRepository solicitudRepository;
    private final ContratoRepository contratoRepository;
    private final ContratoService contratoService;
    private final OrdenCompraRepository ordenRepository;
    private final OrdenCompraService ordenService;
    private final BodegaRepository bodegaRepository;
    private final ConsecutivoGenerator consecutivos;
    private final AuditoriaService auditoriaService;

    /**
     * @param solicitudRepository solicitud que se compra
     * @param contratoRepository  contrato (se bloquea mientras se emite)
     * @param contratoService     cálculo al precio pactado y consumo del tope
     * @param ordenRepository     órdenes
     * @param ordenService        historial, aviso al proveedor y respuesta compartidos con el flujo normal
     * @param bodegaRepository    bodega destino
     * @param consecutivos        números OC-000001
     * @param auditoriaService    registro de acciones
     */
    public OrdenDirectaService(SolicitudCompraRepository solicitudRepository, ContratoRepository contratoRepository,
            ContratoService contratoService, OrdenCompraRepository ordenRepository, OrdenCompraService ordenService,
            BodegaRepository bodegaRepository, ConsecutivoGenerator consecutivos, AuditoriaService auditoriaService) {
        this.solicitudRepository = solicitudRepository;
        this.contratoRepository = contratoRepository;
        this.contratoService = contratoService;
        this.ordenRepository = ordenRepository;
        this.ordenService = ordenService;
        this.bodegaRepository = bodegaRepository;
        this.consecutivos = consecutivos;
        this.auditoriaService = auditoriaService;
    }

    /**
     * Emite la orden (versión 1) de una solicitud APROBADA al precio pactado de un contrato.
     *
     * @param req solicitud, contrato, bodega y entrega
     * @return orden EMITIDA
     * @throws NoEncontradoException   si la solicitud, el contrato o la bodega no existen
     * @throws AccesoDenegadoException si quien emite es el solicitante
     * @throws NegocioException        si no se cumple alguna regla de la lista de la clase
     */
    @Transactional
    public OrdenResponse emitir(EmitirDirectaRequest req) {
        solicitudRepository.bloquear(req.solicitudId())
                .orElseThrow(() -> new NoEncontradoException("Solicitud", req.solicitudId()));
        SolicitudCompra s = solicitudRepository.findWithLineasById(req.solicitudId())
                .orElseThrow(() -> new NoEncontradoException("Solicitud", req.solicitudId()));
        if (s.getEstado() != EstadoSolicitud.APROBADA) {
            throw new NegocioException("SOLICITUD_NO_APROBADA", "La solicitud " + s.getConsecutivo() + " está "
                    + s.getEstado() + "; solo una solicitud APROBADA se compra con un contrato");
        }
        UsuarioAutenticado sesion = SesionActual.usuario();
        if (s.getUsuarioSolicitanteId().equals(sesion.id())) {
            throw new AccesoDenegadoException("No puede emitir la orden de su propia solicitud (segregación de funciones)");
        }
        contratoRepository.bloquear(req.contratoId())
                .orElseThrow(() -> new NoEncontradoException("Contrato", req.contratoId()));
        Contrato c = contratoRepository.findDetalleById(req.contratoId())
                .orElseThrow(() -> new NoEncontradoException("Contrato", req.contratoId()));
        ContratoService.exigirVigente(c);
        if (c.getProveedor().getEstado() != EstadoProveedor.HABILITADO) {
            throw new NegocioException("PROVEEDOR_NO_HABILITADO", "El proveedor " + c.getProveedor().getRazonSocial()
                    + " está " + c.getProveedor().getEstado());
        }

        CalculoContrato calculo = contratoService.calcular(s, c, contratoService.consumoDe(c));
        if (!calculo.cubreTodo()) {
            throw new NegocioException("CONTRATO_NO_CUBRE", "El contrato " + c.getNumero()
                    + " no incluye estos productos de la solicitud: " + String.join(", ", calculo.sinCubrir())
                    + ". Use otro contrato o una RFQ");
        }
        if (!calculo.dentroDelTope()) {
            String pasados = calculo.lineas().stream().filter(CalculoContrato.Linea::excedeTope)
                    .map(l -> l.producto().getNombre() + " (pide " + l.cantidad().toPlainString() + ", quedan "
                            + l.disponible().toPlainString() + ")")
                    .reduce((a, b) -> a + "; " + b).orElse("");
            throw new NegocioException("CONTRATO_TOPE_EXCEDIDO", "La solicitud pasa del tope del contrato "
                    + c.getNumero() + ": " + pasados);
        }
        if (calculo.montoIncompleto()) {
            throw new NegocioException("MONTO_INCOMPLETO", "La solicitud " + s.getConsecutivo() + " se aprobó sin el "
                    + "costo de referencia de algún producto, así que su monto no sirve para comparar; use una RFQ");
        }
        if (!calculo.dentroDeLoAprobado()) {
            throw new NegocioException("ORDEN_SUPERA_APROBADO", "Al precio pactado la solicitud cuesta $ "
                    + calculo.subtotal().toPlainString() + " y se aprobó por $ "
                    + (calculo.montoAprobado() == null ? "0" : calculo.montoAprobado().toPlainString())
                    + "; use una RFQ o corrija la solicitud para que se apruebe de nuevo");
        }

        OrdenCompra o = new OrdenCompra();
        o.setContratoId(c.getId());
        o.setSolicitudId(s.getId());
        o.setProveedor(c.getProveedor());
        o.setBodegaDestino(bodegaActiva(req.bodegaDestinoId()));
        o.setUsuarioId(sesion.id());
        o.setConsecutivo(consecutivos.siguiente(ConsecutivoGenerator.ORDEN_COMPRA));
        o.setLugarEntrega(req.lugarEntrega().trim());
        o.setFechaEntregaEsperada(req.fechaEntregaEsperada());
        for (CalculoContrato.Linea l : calculo.lineas()) {
            OrdenCompraLinea linea = new OrdenCompraLinea();
            linea.setContratoPrecioId(l.precio().getId());
            linea.setProducto(l.producto());
            linea.setCantidad(l.cantidad().setScale(4, RoundingMode.HALF_UP));
            linea.setPrecioUnitario(l.precioUnitario());
            linea.setImpuestoPct(l.impuestoPct());
            linea.setSubtotal(l.subtotal());
            o.agregarLinea(linea);
        }
        OrdenCompraService.totalizar(o);
        OrdenCompra guardada = ordenRepository.save(o);
        s.setEstado(EstadoSolicitud.ADJUDICADA);
        ordenService.historial(guardada, EstadoOrden.EMITIDA, "Orden directa del contrato " + c.getNumero());
        ordenService.avisarProveedor(guardada, EventoNotificacion.ORDEN_EMITIDA, "Recibió la orden de compra "
                + guardada.getConsecutivo() + " por $ " + guardada.getTotal().toPlainString() + " al precio del contrato "
                + c.getNumero() + " con entrega el " + guardada.getFechaEntregaEsperada());
        auditoriaService.exito(AccionAuditoria.ORDEN_EMITIR_DIRECTA, EntidadAuditada.ORDEN_COMPRA, guardada.getId(),
                guardada.getConsecutivo() + " v1; contrato=" + c.getNumero() + "; solicitud=" + s.getConsecutivo()
                        + "; subtotal=" + guardada.getSubtotal() + "; total=" + guardada.getTotal());
        log.info("Orden directa {} emitida a {} por {} (contrato {})", guardada.getConsecutivo(),
                c.getProveedor().getRazonSocial(), guardada.getTotal(), c.getNumero());
        return ordenService.respuesta(guardada);
    }

    /**
     * Bodega existente y activa.
     *
     * @param id bodega
     * @return bodega
     * @throws NoEncontradoException si no existe
     * @throws NegocioException      si está inactiva
     */
    private Bodega bodegaActiva(Long id) {
        Bodega b = bodegaRepository.findById(id).orElseThrow(() -> new NoEncontradoException("Bodega", id));
        if (!b.isActivo()) {
            throw new NegocioException("BODEGA_INACTIVA", "La bodega " + b.getNombre() + " está inactiva");
        }
        return b;
    }
}
