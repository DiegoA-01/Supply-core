package com.proyecto.supply_core.compra.cotizacion.service;

import java.time.LocalDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.proyecto.supply_core.auditoria.enums.AccionAuditoria;
import com.proyecto.supply_core.auditoria.enums.EntidadAuditada;
import com.proyecto.supply_core.auditoria.service.AuditoriaService;
import com.proyecto.supply_core.common.exception.AccesoDenegadoException;
import com.proyecto.supply_core.common.exception.NegocioException;
import com.proyecto.supply_core.common.exception.NoEncontradoException;
import com.proyecto.supply_core.compra.cotizacion.dto.CotizacionRequest;
import com.proyecto.supply_core.compra.cotizacion.dto.CotizacionResponse;
import com.proyecto.supply_core.compra.cotizacion.entity.Cotizacion;
import com.proyecto.supply_core.compra.cotizacion.enums.EstadoCotizacion;
import com.proyecto.supply_core.compra.cotizacion.enums.OrigenCotizacion;
import com.proyecto.supply_core.compra.cotizacion.repository.CotizacionRepository;
import com.proyecto.supply_core.compra.rfq.entity.Rfq;
import com.proyecto.supply_core.compra.rfq.enums.EstadoRfq;
import com.proyecto.supply_core.compra.rfq.repository.RfqRepository;
import com.proyecto.supply_core.documento.dto.DocumentoResponse;
import com.proyecto.supply_core.documento.enums.EntidadDocumento;
import com.proyecto.supply_core.documento.service.DocumentoService;
import com.proyecto.supply_core.ia.service.ExtraccionService;
import com.proyecto.supply_core.notificacion.enums.EventoNotificacion;
import com.proyecto.supply_core.notificacion.service.NotificacionService;
import com.proyecto.supply_core.proveedor.repository.ProveedorRepository;
import com.proyecto.supply_core.security.context.SesionActual;
import com.proyecto.supply_core.security.context.UsuarioAutenticado;

/**
 * UC-07 Cotizar.
 * <ul>
 *   <li>Solo un proveedor invitado, con la RFQ ABIERTA y antes de la fecha límite. La fila de la RFQ se
 *       bloquea para que el cierre automático no se cruce con un envío.</li>
 *   <li>Una cotización por proveedor y RFQ: el proveedor la arma en BORRADOR (formulario, archivo o ambos)
 *       y la envía. Después de enviada no se edita.</li>
 *   <li>Los montos los calcula {@link CalculadoraCotizacion}.</li>
 *   <li>Sobre cerrado: mientras la RFQ esté ABIERTA, compras ve quién envió pero no los precios.</li>
 * </ul>
 */
@Service
public class CotizacionService {

    private static final Logger log = LoggerFactory.getLogger(CotizacionService.class);

    private final CotizacionRepository cotizacionRepository;
    private final RfqRepository rfqRepository;
    private final ProveedorRepository proveedorRepository;
    private final DocumentoService documentoService;
    private final AuditoriaService auditoriaService;
    private final NotificacionService notificacionService;
    private final ExtraccionService extraccionService;

    /**
     * @param cotizacionRepository cotizaciones
     * @param rfqRepository        RFQ (bloqueo y validación)
     * @param proveedorRepository  proveedor de la sesión
     * @param documentoService     archivo original
     * @param auditoriaService     registro de acciones
     * @param notificacionService  aviso al comprador
     * @param extraccionService    lectura de cotizaciones por archivo (IA)
     */
    public CotizacionService(CotizacionRepository cotizacionRepository, RfqRepository rfqRepository,
            ProveedorRepository proveedorRepository, DocumentoService documentoService,
            AuditoriaService auditoriaService, NotificacionService notificacionService,
            ExtraccionService extraccionService) {
        this.cotizacionRepository = cotizacionRepository;
        this.rfqRepository = rfqRepository;
        this.proveedorRepository = proveedorRepository;
        this.documentoService = documentoService;
        this.auditoriaService = auditoriaService;
        this.notificacionService = notificacionService;
        this.extraccionService = extraccionService;
    }

    /**
     * La cotización del proveedor de la sesión para una RFQ.
     *
     * @param rfqId RFQ
     * @return su cotización
     * @throws NoEncontradoException si aún no ha cotizado esa RFQ
     */
    @Transactional(readOnly = true)
    public CotizacionResponse mia(Long rfqId) {
        return CotizacionResponse.of(cotizacionRepository.findByRfq_IdAndProveedor_Id(rfqId, proveedorDeSesion())
                .orElseThrow(() -> new NoEncontradoException("Aún no ha cotizado esta RFQ")));
    }

    /**
     * Crea o actualiza el borrador con los datos del formulario (reemplaza las líneas) y recalcula montos.
     *
     * @param rfqId RFQ
     * @param req   datos validados
     * @return borrador actualizado
     * @throws NegocioException si la RFQ no acepta cotizaciones, ya la envió o una línea no es de la RFQ
     */
    @Transactional
    public CotizacionResponse guardar(Long rfqId, CotizacionRequest req) {
        Rfq rfq = rfqAbiertaParaMi(rfqId);
        Cotizacion c = borradorPropio(rfq);
        c.setMoneda(req.moneda() == null ? "COP" : req.moneda());
        c.setPlazoEntregaDias(req.plazoEntregaDias());
        c.setGarantiaMeses(req.garantiaMeses());
        c.setValidezHasta(req.validezHasta());
        c.setCondicionesComerciales(req.condicionesComerciales() == null || req.condicionesComerciales().isBlank()
                ? null : req.condicionesComerciales().trim());
        c.getLineas().clear();
        cotizacionRepository.flush(); // borra las líneas viejas antes de insertar (UNIQUE cotización + línea RFQ)
        ArmadorLineas.armar(c, rfq, req.lineas());
        auditoriaService.exito(AccionAuditoria.COTIZACION_GUARDAR, EntidadAuditada.COTIZACION, c.getId(),
                rfq.getConsecutivo() + "; lineas=" + c.getLineas().size() + "; total=" + c.getTotal());
        return CotizacionResponse.of(c);
    }

    /**
     * Sube el documento original (PDF, Excel o imagen) y lo deja como fuente de la cotización.
     * Crea el borrador si no existía. El archivo anterior, si lo había, se conserva en el historial.
     *
     * @param rfqId   RFQ
     * @param archivo archivo del proveedor
     * @return borrador con el documento enlazado
     * @throws NegocioException si la RFQ no acepta cotizaciones o ya la envió
     */
    @Transactional
    public CotizacionResponse subirArchivo(Long rfqId, MultipartFile archivo) {
        Rfq rfq = rfqAbiertaParaMi(rfqId);
        Cotizacion c = borradorPropio(rfq);
        DocumentoResponse doc = documentoService.subir(EntidadDocumento.COTIZACION, c.getId(), archivo);
        c.setDocumentoOriginalId(doc.id());
        c.setOrigen(OrigenCotizacion.ARCHIVO);
        return CotizacionResponse.of(c);
    }

    /**
     * Envía la cotización al comprador. Con formulario exige al menos una línea; con archivo basta el
     * documento (la IA lo leerá en el paso 13).
     *
     * @param rfqId RFQ
     * @return cotización ENVIADA
     * @throws NegocioException si la RFQ ya no acepta cotizaciones, ya se envió o está incompleta
     */
    @Transactional
    public CotizacionResponse enviar(Long rfqId) {
        Rfq rfq = rfqAbiertaParaMi(rfqId);
        Cotizacion c = cotizacionRepository.findByRfq_IdAndProveedor_Id(rfqId, proveedorDeSesion())
                .orElseThrow(() -> new NoEncontradoException("Aún no ha cotizado esta RFQ"));
        exigirBorrador(c);
        if (c.getOrigen() == OrigenCotizacion.ARCHIVO ? c.getDocumentoOriginalId() == null : c.getLineas().isEmpty()) {
            throw new NegocioException("COTIZACION_INCOMPLETA",
                    "Agregue al menos un precio o suba el documento de su cotización antes de enviarla");
        }
        c.setEstado(EstadoCotizacion.ENVIADA);
        c.setEnviadoEn(LocalDateTime.now());
        if (c.getOrigen() == OrigenCotizacion.ARCHIVO) {
            extraccionService.programar(c); // la IA lo lee en segundo plano (o queda para digitar)
        }
        notificacionService.notificar(rfq.getUsuarioId(), EventoNotificacion.COTIZACION_RECIBIDA,
                c.getProveedor().getRazonSocial() + " envió su cotización para la " + rfq.getConsecutivo());
        auditoriaService.exito(AccionAuditoria.COTIZACION_ENVIAR, EntidadAuditada.COTIZACION, c.getId(),
                rfq.getConsecutivo() + "; origen=" + c.getOrigen() + "; total=" + c.getTotal());
        log.info("Cotización {} de {} enviada para {}", c.getId(), c.getProveedor().getRazonSocial(),
                rfq.getConsecutivo());
        return CotizacionResponse.of(c);
    }

    /**
     * Cotizaciones recibidas en una RFQ (vista de compras). Las BORRADOR no aparecen; si la RFQ sigue
     * ABIERTA salen selladas (sin precios).
     *
     * @param rfqId RFQ
     * @return cotizaciones enviadas, de la primera a la última
     * @throws NoEncontradoException si la RFQ no existe
     */
    @Transactional(readOnly = true)
    public List<CotizacionResponse> deRfq(Long rfqId) {
        Rfq rfq = rfqRepository.findById(rfqId).orElseThrow(() -> new NoEncontradoException("RFQ", rfqId));
        boolean sellada = rfq.getEstado() == EstadoRfq.ABIERTA;
        return cotizacionRepository.findByRfq_IdAndEstadoNotOrderByEnviadoEnAsc(rfqId, EstadoCotizacion.BORRADOR)
                .stream().map(c -> sellada ? CotizacionResponse.sellada(c) : CotizacionResponse.of(c)).toList();
    }

    // ----------------------------------------------------------------- apoyo

    /**
     * Bloquea la RFQ y verifica que el proveedor de la sesión pueda cotizarla ahora.
     *
     * @param rfqId RFQ
     * @return RFQ bloqueada con líneas e invitados
     * @throws NoEncontradoException si no existe o el proveedor no está invitado (no se revela que existe)
     * @throws NegocioException      si no está ABIERTA o ya pasó la fecha límite
     */
    private Rfq rfqAbiertaParaMi(Long rfqId) {
        Long proveedorId = proveedorDeSesion();
        rfqRepository.bloquear(rfqId).orElseThrow(() -> new NoEncontradoException("RFQ", rfqId));
        Rfq rfq = rfqRepository.findWithDetalleById(rfqId)
                .filter(r -> r.getEstado().visibleParaProveedor() && r.estaInvitado(proveedorId))
                .orElseThrow(() -> new NoEncontradoException("RFQ", rfqId));
        if (rfq.getEstado() != EstadoRfq.ABIERTA || !rfq.getFechaLimite().isAfter(LocalDateTime.now())) {
            throw new NegocioException("RFQ_NO_RECIBE_COTIZACIONES",
                    "La " + rfq.getConsecutivo() + " ya no recibe cotizaciones (cerró el " + rfq.getFechaLimite() + ")");
        }
        return rfq;
    }

    /**
     * Borrador del proveedor de la sesión para la RFQ; lo crea si no existe.
     *
     * @param rfq RFQ abierta
     * @return cotización en BORRADOR (guardada, con id)
     * @throws NegocioException si ya la envió
     */
    private Cotizacion borradorPropio(Rfq rfq) {
        Long proveedorId = proveedorDeSesion();
        Cotizacion c = cotizacionRepository.findByRfq_IdAndProveedor_Id(rfq.getId(), proveedorId).orElseGet(() -> {
            Cotizacion nueva = new Cotizacion();
            nueva.setRfq(rfq);
            nueva.setProveedor(proveedorRepository.getReferenceById(proveedorId));
            return cotizacionRepository.save(nueva);
        });
        exigirBorrador(c);
        return c;
    }

    /**
     * Verifica que la cotización siga en BORRADOR.
     *
     * @param c cotización
     * @throws NegocioException si ya se envió
     */
    private static void exigirBorrador(Cotizacion c) {
        if (c.getEstado() != EstadoCotizacion.BORRADOR) {
            throw new NegocioException("COTIZACION_ENVIADA",
                    "La cotización ya fue enviada (" + c.getEstado() + ") y no se puede modificar");
        }
    }

    /**
     * Proveedor del usuario de la sesión.
     *
     * @return id del proveedor
     * @throws AccesoDenegadoException si el usuario no pertenece a un proveedor
     */
    private static Long proveedorDeSesion() {
        UsuarioAutenticado u = SesionActual.usuario();
        if (!u.esProveedor()) {
            throw new AccesoDenegadoException("Su usuario no pertenece a un proveedor");
        }
        return u.proveedorId();
    }
}
