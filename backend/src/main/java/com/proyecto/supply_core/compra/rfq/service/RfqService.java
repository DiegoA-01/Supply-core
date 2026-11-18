package com.proyecto.supply_core.compra.rfq.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.proyecto.supply_core.auditoria.enums.AccionAuditoria;
import com.proyecto.supply_core.auditoria.enums.EntidadAuditada;
import com.proyecto.supply_core.auditoria.service.AuditoriaService;
import com.proyecto.supply_core.catalogo.entity.CategoriaProducto;
import com.proyecto.supply_core.common.dto.PageResponse;
import com.proyecto.supply_core.common.exception.AccesoDenegadoException;
import com.proyecto.supply_core.common.exception.NegocioException;
import com.proyecto.supply_core.common.exception.NoEncontradoException;
import com.proyecto.supply_core.common.util.ConsecutivoGenerator;
import com.proyecto.supply_core.common.util.Ordenamiento;
import com.proyecto.supply_core.compra.rfq.dto.EspecificacionRequest;
import com.proyecto.supply_core.compra.rfq.dto.RfqCrearRequest;
import com.proyecto.supply_core.compra.rfq.dto.RfqDatosRequest;
import com.proyecto.supply_core.compra.rfq.dto.RfqPortalResponse;
import com.proyecto.supply_core.compra.rfq.dto.RfqResponse;
import com.proyecto.supply_core.compra.rfq.dto.RfqResumenResponse;
import com.proyecto.supply_core.compra.rfq.entity.Rfq;
import com.proyecto.supply_core.compra.rfq.entity.RfqLinea;
import com.proyecto.supply_core.compra.rfq.entity.RfqProveedor;
import com.proyecto.supply_core.compra.rfq.enums.EstadoRfq;
import com.proyecto.supply_core.compra.rfq.repository.RfqRepository;
import com.proyecto.supply_core.compra.solicitud.entity.SolicitudCompra;
import com.proyecto.supply_core.compra.solicitud.entity.SolicitudCompraLinea;
import com.proyecto.supply_core.compra.solicitud.enums.EstadoSolicitud;
import com.proyecto.supply_core.compra.solicitud.repository.SolicitudCompraRepository;
import com.proyecto.supply_core.notificacion.enums.EventoNotificacion;
import com.proyecto.supply_core.notificacion.service.NotificacionService;
import com.proyecto.supply_core.proveedor.entity.Proveedor;
import com.proyecto.supply_core.proveedor.enums.EstadoProveedor;
import com.proyecto.supply_core.proveedor.repository.ProveedorRepository;
import com.proyecto.supply_core.proveedor.service.ProveedorService;
import com.proyecto.supply_core.security.context.SesionActual;
import com.proyecto.supply_core.security.context.UsuarioAutenticado;
import com.proyecto.supply_core.usuario.entity.Usuario;
import com.proyecto.supply_core.usuario.repository.UsuarioRepository;

/**
 * UC-06 Crear RFQ: crear desde una solicitud APROBADA, editar en BORRADOR, abrir, cerrar y cancelar;
 * más la vista del proveedor invitado.
 * <ul>
 *   <li>Las líneas se copian de la solicitud; el comprador agrega especificaciones.</li>
 *   <li>Sin invitados explícitos se invita a todos los HABILITADOS de las categorías de las líneas.
 *       Si una categoría no tiene ninguno, se devuelve una alerta.</li>
 *   <li>Abrir pasa la solicitud a EN_RFQ y avisa a los invitados. Cancelar la devuelve a APROBADA.</li>
 *   <li>El cierre es manual o automático al vencer la fecha límite ({@link #cerrarVencidas}).</li>
 * </ul>
 */
@Service
public class RfqService {

    private static final Logger log = LoggerFactory.getLogger(RfqService.class);
    /** Campos por los que se puede ordenar. */
    private static final Set<String> CAMPOS_ORDENABLES = Set.of("creadoEn", "fechaLimite", "consecutivo", "estado");

    private final RfqRepository rfqRepository;
    private final SolicitudCompraRepository solicitudRepository;
    private final ProveedorRepository proveedorRepository;
    private final ProveedorService proveedorService;
    private final UsuarioRepository usuarioRepository;
    private final ConsecutivoGenerator consecutivos;
    private final AuditoriaService auditoriaService;
    private final NotificacionService notificacionService;

    /**
     * @param rfqRepository       RFQ
     * @param solicitudRepository solicitudes de origen
     * @param proveedorRepository proveedores invitados
     * @param proveedorService    proveedores habilitados por categoría
     * @param usuarioRepository   usuarios del portal de cada proveedor
     * @param consecutivos        números RFQ-000001
     * @param auditoriaService    registro de acciones
     * @param notificacionService avisos a invitados y comprador
     */
    public RfqService(RfqRepository rfqRepository, SolicitudCompraRepository solicitudRepository,
            ProveedorRepository proveedorRepository, ProveedorService proveedorService,
            UsuarioRepository usuarioRepository, ConsecutivoGenerator consecutivos,
            AuditoriaService auditoriaService, NotificacionService notificacionService) {
        this.rfqRepository = rfqRepository;
        this.solicitudRepository = solicitudRepository;
        this.proveedorRepository = proveedorRepository;
        this.proveedorService = proveedorService;
        this.usuarioRepository = usuarioRepository;
        this.consecutivos = consecutivos;
        this.auditoriaService = auditoriaService;
        this.notificacionService = notificacionService;
    }

    /**
     * Crea una RFQ en BORRADOR desde una solicitud APROBADA.
     *
     * @param req solicitud y datos validados
     * @return RFQ con alertas
     * @throws NegocioException si la solicitud no está APROBADA o ya tiene una RFQ vigente
     */
    @Transactional
    public RfqResponse crear(RfqCrearRequest req) {
        SolicitudCompra solicitud = solicitudBloqueada(req.solicitudId());
        if (solicitud.getEstado() != EstadoSolicitud.APROBADA) {
            throw new NegocioException("SOLICITUD_NO_APROBADA",
                    "La solicitud " + solicitud.getConsecutivo() + " está " + solicitud.getEstado()
                            + "; solo se crea una RFQ desde una solicitud APROBADA");
        }
        if (rfqRepository.existsBySolicitud_IdAndEstadoNot(solicitud.getId(), EstadoRfq.CANCELADA)) {
            throw new NegocioException("RFQ_EXISTENTE",
                    "La solicitud " + solicitud.getConsecutivo() + " ya tiene una RFQ vigente");
        }
        Rfq rfq = new Rfq();
        rfq.setSolicitud(solicitud);
        rfq.setUsuarioId(SesionActual.usuario().id());
        rfq.setConsecutivo(consecutivos.siguiente(ConsecutivoGenerator.RFQ));
        rfq.setFechaLimite(req.datos().fechaLimite());
        for (SolicitudCompraLinea l : solicitud.getLineas()) {
            RfqLinea linea = new RfqLinea();
            linea.setProducto(l.getProducto());
            linea.setUnidadMedida(l.getUnidadMedida());
            linea.setCantidad(l.getCantidad());
            rfq.agregarLinea(linea);
        }
        Rfq guardada = rfqRepository.save(rfq); // con id: ya se puede invitar
        aplicarDatos(guardada, req.datos());
        auditoriaService.exito(AccionAuditoria.RFQ_CREAR, EntidadAuditada.RFQ, guardada.getId(),
                guardada.getConsecutivo() + "; solicitud=" + solicitud.getConsecutivo() + "; invitados="
                        + guardada.getInvitados().size());
        return respuesta(guardada);
    }

    /**
     * Edita una RFQ en BORRADOR.
     *
     * @param id    id de la RFQ
     * @param datos datos validados
     * @return RFQ con alertas
     * @throws NegocioException si ya no está en BORRADOR
     */
    @Transactional
    public RfqResponse actualizar(Long id, RfqDatosRequest datos) {
        Rfq rfq = bloquear(id);
        exigirEstado(rfq, EstadoRfq.BORRADOR, "editar");
        aplicarDatos(rfq, datos);
        auditoriaService.exito(AccionAuditoria.RFQ_EDITAR, EntidadAuditada.RFQ, id,
                rfq.getConsecutivo() + "; invitados=" + rfq.getInvitados().size());
        return respuesta(rfq);
    }

    /**
     * Abre la RFQ: la solicitud pasa a EN_RFQ y se avisa a los invitados.
     *
     * @param id id de la RFQ
     * @return RFQ abierta
     * @throws NegocioException si no está en BORRADOR, no tiene invitados, la fecha límite ya pasó
     *                          o la solicitud dejó de estar APROBADA
     */
    @Transactional
    public RfqResponse abrir(Long id) {
        Rfq rfq = bloquear(id);
        exigirEstado(rfq, EstadoRfq.BORRADOR, "abrir");
        if (rfq.getInvitados().isEmpty()) {
            throw new NegocioException("RFQ_SIN_PROVEEDORES", "Invite al menos un proveedor antes de abrir la RFQ");
        }
        if (!rfq.getFechaLimite().isAfter(LocalDateTime.now())) {
            throw new NegocioException("FECHA_LIMITE_VENCIDA", "La fecha límite ya pasó; actualícela antes de abrir");
        }
        SolicitudCompra solicitud = solicitudBloqueada(rfq.getSolicitud().getId());
        if (solicitud.getEstado() != EstadoSolicitud.APROBADA) {
            throw new NegocioException("SOLICITUD_NO_APROBADA",
                    "La solicitud " + solicitud.getConsecutivo() + " está " + solicitud.getEstado());
        }
        solicitud.setEstado(EstadoSolicitud.EN_RFQ);
        rfq.setEstado(EstadoRfq.ABIERTA);
        rfq.setAbiertaEn(LocalDateTime.now());
        avisarInvitados(rfq, EventoNotificacion.RFQ_INVITACION, "Fue invitado a cotizar la " + rfq.getConsecutivo()
                + " (" + rfq.getLineas().size() + " productos) hasta el " + rfq.getFechaLimite());
        auditoriaService.exito(AccionAuditoria.RFQ_ABRIR, EntidadAuditada.RFQ, id,
                rfq.getConsecutivo() + "; invitados=" + rfq.getInvitados().size());
        log.info("RFQ {} abierta con {} invitados", rfq.getConsecutivo(), rfq.getInvitados().size());
        return respuesta(rfq);
    }

    /**
     * Cierra manualmente una RFQ ABIERTA (ya no recibe cotizaciones).
     *
     * @param id id de la RFQ
     * @return RFQ cerrada
     * @throws NegocioException si no está ABIERTA
     */
    @Transactional
    public RfqResponse cerrar(Long id) {
        Rfq rfq = bloquear(id);
        exigirEstado(rfq, EstadoRfq.ABIERTA, "cerrar");
        aplicarCierre(rfq, "manual");
        return respuesta(rfq);
    }

    /**
     * Cierra las RFQ ABIERTAS cuya fecha límite ya pasó (lo llama {@link RfqCierreJob}; sin sesión).
     *
     * @return cantidad cerrada
     */
    @Transactional
    public int cerrarVencidas() {
        LocalDateTime ahora = LocalDateTime.now();
        int cerradas = 0;
        for (Long id : rfqRepository.vencidas(EstadoRfq.ABIERTA, ahora)) {
            Rfq rfq = bloquear(id);
            if (rfq.getEstado() == EstadoRfq.ABIERTA && !rfq.getFechaLimite().isAfter(ahora)) {
                aplicarCierre(rfq, "automático por fecha límite");
                cerradas++;
            }
        }
        return cerradas;
    }

    /**
     * Cancela una RFQ en BORRADOR o ABIERTA. La solicitud vuelve a APROBADA para otra RFQ.
     *
     * @param id id de la RFQ
     * @return RFQ cancelada
     * @throws NegocioException si ya está cerrada, adjudicada o cancelada
     */
    @Transactional
    public RfqResponse cancelar(Long id) {
        Rfq rfq = bloquear(id);
        if (!rfq.getEstado().esCancelable()) {
            throw new NegocioException("TRANSICION_INVALIDA", "Una RFQ " + rfq.getEstado() + " no se puede cancelar");
        }
        EstadoRfq anterior = rfq.getEstado();
        rfq.setEstado(EstadoRfq.CANCELADA);
        SolicitudCompra solicitud = solicitudBloqueada(rfq.getSolicitud().getId());
        if (solicitud.getEstado() == EstadoSolicitud.EN_RFQ) {
            solicitud.setEstado(EstadoSolicitud.APROBADA);
        }
        if (anterior == EstadoRfq.ABIERTA) {
            avisarInvitados(rfq, EventoNotificacion.RFQ_CANCELADA,
                    "La " + rfq.getConsecutivo() + " fue cancelada; ya no se reciben cotizaciones");
        }
        auditoriaService.exito(AccionAuditoria.RFQ_CANCELAR, EntidadAuditada.RFQ, id,
                rfq.getConsecutivo() + "; estado anterior=" + anterior);
        return respuesta(rfq);
    }

    /**
     * Detalle interno.
     *
     * @param id id de la RFQ
     * @return RFQ con líneas, invitados y alertas
     * @throws NoEncontradoException si no existe
     */
    @Transactional(readOnly = true)
    public RfqResponse obtener(Long id) {
        return respuesta(rfqRepository.findWithDetalleById(id).orElseThrow(() -> new NoEncontradoException("RFQ", id)));
    }

    /**
     * Listado interno con filtros.
     *
     * @param texto       coincidencia en el consecutivo
     * @param estado      estado
     * @param solicitudId solicitud de origen
     * @param pageable    página y orden
     * @return página de RFQ
     */
    @Transactional(readOnly = true)
    public PageResponse<RfqResumenResponse> buscar(String texto, EstadoRfq estado, Long solicitudId,
            Pageable pageable) {
        String filtro = texto == null || texto.isBlank() ? null : texto.trim().toUpperCase();
        return PageResponse.of(rfqRepository.buscar(filtro, estado, solicitudId,
                Ordenamiento.validar(pageable, CAMPOS_ORDENABLES)), RfqResumenResponse::of);
    }

    /**
     * RFQ en las que está invitado el proveedor de la sesión (nunca las BORRADOR).
     *
     * @param pageable página y orden
     * @return página de RFQ
     * @throws AccesoDenegadoException si el usuario no pertenece a un proveedor
     */
    @Transactional(readOnly = true)
    public PageResponse<RfqResumenResponse> invitadas(Pageable pageable) {
        return PageResponse.of(rfqRepository.invitadasDe(proveedorDeSesion(),
                Ordenamiento.validar(pageable, CAMPOS_ORDENABLES)), RfqResumenResponse::of);
    }

    /**
     * Detalle para el proveedor: solo si está invitado y la RFQ ya no es BORRADOR (si no, 404).
     *
     * @param id id de la RFQ
     * @return RFQ sin datos internos
     * @throws NoEncontradoException si no existe o no está invitado
     */
    @Transactional(readOnly = true)
    public RfqPortalResponse obtenerParaProveedor(Long id) {
        Long proveedorId = proveedorDeSesion();
        Rfq rfq = rfqRepository.findWithDetalleById(id)
                .filter(r -> r.getEstado().visibleParaProveedor() && r.estaInvitado(proveedorId))
                .orElseThrow(() -> new NoEncontradoException("RFQ", id));
        return RfqPortalResponse.of(rfq);
    }

    // ----------------------------------------------------------------- apoyo

    /**
     * Fecha, condiciones, especificaciones e invitados.
     *
     * @param rfq   RFQ guardada (con id)
     * @param datos datos validados
     */
    private void aplicarDatos(Rfq rfq, RfqDatosRequest datos) {
        rfq.setFechaLimite(datos.fechaLimite());
        rfq.setCondiciones(datos.condiciones() == null || datos.condiciones().isBlank()
                ? null : datos.condiciones().trim());
        aplicarEspecificaciones(rfq, datos.especificaciones());

        Set<Proveedor> destino = datos.proveedorIds() == null || datos.proveedorIds().isEmpty()
                ? sugeridos(rfq) : validarInvitados(rfq, datos.proveedorIds());
        Set<Long> idsDestino = destino.stream().map(Proveedor::getId).collect(Collectors.toSet());
        rfq.getInvitados().removeIf(i -> !idsDestino.contains(i.getProveedor().getId()));
        destino.forEach(rfq::invitar);
    }

    /**
     * Asigna especificaciones por producto (las líneas sin especificación quedan vacías).
     *
     * @param rfq              RFQ
     * @param especificaciones especificaciones recibidas (puede ser {@code null})
     * @throws NegocioException si un producto no está en la RFQ o se repite
     */
    private static void aplicarEspecificaciones(Rfq rfq, List<EspecificacionRequest> especificaciones) {
        Map<Long, String> porProducto = new LinkedHashMap<>();
        if (especificaciones != null) {
            for (EspecificacionRequest e : especificaciones) {
                if (porProducto.put(e.productoId(), e.texto().trim()) != null) {
                    throw new NegocioException("ESPECIFICACION_INVALIDA",
                            "El producto " + e.productoId() + " tiene más de una especificación");
                }
            }
        }
        Set<Long> productos = rfq.getLineas().stream().map(l -> l.getProducto().getId()).collect(Collectors.toSet());
        porProducto.keySet().stream().filter(p -> !productos.contains(p)).findFirst().ifPresent(p -> {
            throw new NegocioException("ESPECIFICACION_INVALIDA", "El producto " + p + " no está en la RFQ");
        });
        rfq.getLineas().forEach(l -> l.setEspecificaciones(porProducto.get(l.getProducto().getId())));
    }

    /**
     * Todos los proveedores HABILITADOS de las categorías de las líneas.
     *
     * @param rfq RFQ con líneas
     * @return proveedores sugeridos
     */
    private Set<Proveedor> sugeridos(Rfq rfq) {
        Set<Proveedor> todos = new LinkedHashSet<>();
        categorias(rfq).keySet().forEach(c -> todos.addAll(proveedorService.habilitadosDeCategoria(c)));
        return todos;
    }

    /**
     * Verifica cada invitado explícito: debe existir, estar HABILITADO y cotizar alguna categoría de la RFQ.
     *
     * @param rfq RFQ con líneas
     * @param ids ids recibidos
     * @return proveedores válidos
     * @throws NoEncontradoException si alguno no existe
     * @throws NegocioException      si alguno no está habilitado o no cotiza esas categorías
     */
    private Set<Proveedor> validarInvitados(Rfq rfq, Set<Long> ids) {
        Set<Long> categorias = categorias(rfq).keySet();
        Set<Proveedor> validos = new LinkedHashSet<>();
        for (Long id : ids) {
            Proveedor p = proveedorRepository.findWithCategoriasById(id)
                    .orElseThrow(() -> new NoEncontradoException("Proveedor", id));
            if (p.getEstado() != EstadoProveedor.HABILITADO) {
                throw new NegocioException("PROVEEDOR_NO_HABILITADO",
                        p.getRazonSocial() + " está " + p.getEstado() + " y no puede ser invitado");
            }
            if (p.getCategorias().stream().noneMatch(c -> categorias.contains(c.getId()))) {
                throw new NegocioException("PROVEEDOR_SIN_CATEGORIA",
                        p.getRazonSocial() + " no cotiza ninguna categoría de esta RFQ");
            }
            validos.add(p);
        }
        return validos;
    }

    /**
     * Categorías de los productos de la RFQ.
     *
     * @param rfq RFQ con líneas
     * @return id → nombre, en el orden de las líneas
     */
    private static Map<Long, String> categorias(Rfq rfq) {
        Map<Long, String> categorias = new LinkedHashMap<>();
        for (RfqLinea l : rfq.getLineas()) {
            CategoriaProducto c = l.getProducto().getCategoria();
            categorias.putIfAbsent(c.getId(), c.getNombre());
        }
        return categorias;
    }

    /**
     * Alertas para el comprador mientras la RFQ está en BORRADOR.
     *
     * @param rfq RFQ
     * @return avisos (vacío si todo está bien)
     */
    private List<String> alertas(Rfq rfq) {
        List<String> alertas = new ArrayList<>();
        if (rfq.getEstado() != EstadoRfq.BORRADOR) {
            return alertas;
        }
        categorias(rfq).forEach((id, nombre) -> {
            if (proveedorService.habilitadosDeCategoria(id).isEmpty()) {
                alertas.add("No hay proveedores habilitados para la categoría " + nombre);
            }
        });
        if (rfq.getInvitados().isEmpty()) {
            alertas.add("La RFQ no tiene proveedores invitados; no se podrá abrir");
        }
        return alertas;
    }

    /**
     * Marca la RFQ como CERRADA, avisa y audita.
     *
     * @param rfq    RFQ ABIERTA bloqueada
     * @param motivo "manual" o "automático por fecha límite"
     */
    private void aplicarCierre(Rfq rfq, String motivo) {
        rfq.setEstado(EstadoRfq.CERRADA);
        rfq.setCerradaEn(LocalDateTime.now());
        String mensaje = "La " + rfq.getConsecutivo() + " se cerró (" + motivo + "); ya no se reciben cotizaciones";
        notificacionService.notificar(rfq.getUsuarioId(), EventoNotificacion.RFQ_CERRADA, mensaje);
        avisarInvitados(rfq, EventoNotificacion.RFQ_CERRADA, mensaje);
        auditoriaService.exito(AccionAuditoria.RFQ_CERRAR, EntidadAuditada.RFQ, rfq.getId(),
                rfq.getConsecutivo() + "; cierre " + motivo);
        log.info("RFQ {} cerrada ({})", rfq.getConsecutivo(), motivo);
    }

    /**
     * Notifica a los usuarios del portal de cada proveedor invitado.
     *
     * @param rfq     RFQ con invitados
     * @param evento  evento
     * @param mensaje texto
     */
    private void avisarInvitados(Rfq rfq, EventoNotificacion evento, String mensaje) {
        for (RfqProveedor invitado : rfq.getInvitados()) {
            for (Usuario u : usuarioRepository.findByProveedorId(invitado.getProveedor().getId())) {
                notificacionService.notificar(u.getId(), evento, mensaje);
            }
        }
    }

    /**
     * Verifica el estado requerido para una operación.
     *
     * @param rfq       RFQ
     * @param requerido estado necesario
     * @param operacion nombre de la operación (para el mensaje)
     * @throws NegocioException si el estado no coincide
     */
    private static void exigirEstado(Rfq rfq, EstadoRfq requerido, String operacion) {
        if (rfq.getEstado() != requerido) {
            throw new NegocioException("TRANSICION_INVALIDA", "Solo se puede " + operacion + " una RFQ en "
                    + requerido + "; la " + rfq.getConsecutivo() + " está " + rfq.getEstado());
        }
    }

    /**
     * Bloquea la RFQ y la carga con su detalle.
     *
     * @param id id de la RFQ
     * @return RFQ bloqueada
     * @throws NoEncontradoException si no existe
     */
    private Rfq bloquear(Long id) {
        rfqRepository.bloquear(id).orElseThrow(() -> new NoEncontradoException("RFQ", id));
        return rfqRepository.findWithDetalleById(id).orElseThrow(() -> new NoEncontradoException("RFQ", id));
    }

    /**
     * Bloquea la solicitud y la carga con sus líneas.
     *
     * @param id id de la solicitud
     * @return solicitud bloqueada
     * @throws NoEncontradoException si no existe
     */
    private SolicitudCompra solicitudBloqueada(Long id) {
        solicitudRepository.bloquear(id).orElseThrow(() -> new NoEncontradoException("Solicitud", id));
        return solicitudRepository.findWithLineasById(id).orElseThrow(() -> new NoEncontradoException("Solicitud", id));
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

    /**
     * Respuesta interna con alertas.
     *
     * @param rfq RFQ con detalle
     * @return DTO
     */
    private RfqResponse respuesta(Rfq rfq) {
        return RfqResponse.of(rfq, alertas(rfq));
    }
}
