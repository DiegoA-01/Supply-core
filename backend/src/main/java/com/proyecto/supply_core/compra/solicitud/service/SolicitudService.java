package com.proyecto.supply_core.compra.solicitud.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.proyecto.supply_core.auditoria.enums.AccionAuditoria;
import com.proyecto.supply_core.auditoria.enums.EntidadAuditada;
import com.proyecto.supply_core.auditoria.service.AuditoriaService;
import com.proyecto.supply_core.catalogo.entity.UnidadMedida;
import com.proyecto.supply_core.catalogo.repository.UnidadMedidaRepository;
import com.proyecto.supply_core.catalogo.service.UnidadMedidaService;
import com.proyecto.supply_core.common.dto.PageResponse;
import com.proyecto.supply_core.common.exception.AccesoDenegadoException;
import com.proyecto.supply_core.common.exception.NegocioException;
import com.proyecto.supply_core.common.exception.NoEncontradoException;
import com.proyecto.supply_core.common.util.ConsecutivoGenerator;
import com.proyecto.supply_core.common.tenant.EmpresaContexto;
import com.proyecto.supply_core.common.util.Ordenamiento;
import com.proyecto.supply_core.compra.solicitud.dto.LineaSolicitudRequest;
import com.proyecto.supply_core.compra.solicitud.dto.SolicitudRequest;
import com.proyecto.supply_core.compra.solicitud.dto.SolicitudResponse;
import com.proyecto.supply_core.compra.solicitud.dto.SolicitudResumenResponse;
import com.proyecto.supply_core.compra.solicitud.entity.SolicitudCompra;
import com.proyecto.supply_core.compra.solicitud.entity.SolicitudCompraLinea;
import com.proyecto.supply_core.compra.solicitud.enums.EstadoSolicitud;
import com.proyecto.supply_core.compra.solicitud.repository.AprobacionSolicitudRepository;
import com.proyecto.supply_core.compra.solicitud.repository.SolicitudCompraRepository;
import com.proyecto.supply_core.empresa.entity.PoliticaAprobacion;
import com.proyecto.supply_core.empresa.enums.TipoPolitica;
import com.proyecto.supply_core.empresa.repository.CentroCostoRepository;
import com.proyecto.supply_core.empresa.service.PoliticaAprobacionService;
import com.proyecto.supply_core.inventario.entity.Producto;
import com.proyecto.supply_core.inventario.repository.ProductoRepository;
import com.proyecto.supply_core.notificacion.enums.EventoNotificacion;
import com.proyecto.supply_core.notificacion.service.NotificacionService;
import com.proyecto.supply_core.presupuesto.dto.EvaluacionPresupuesto;
import com.proyecto.supply_core.presupuesto.service.PresupuestoService;
import com.proyecto.supply_core.security.context.Roles;
import com.proyecto.supply_core.security.context.SesionActual;
import com.proyecto.supply_core.security.context.UsuarioAutenticado;
import com.proyecto.supply_core.usuario.repository.UsuarioRepository;

/**
 * UC-04 Crear solicitud de compra: crear, editar, enviar, cancelar y consultar.
 * <p>Al enviar se estima el monto (cantidad x costo de referencia del producto, convirtiendo a su
 * unidad) y se busca la política de aprobación SOLICITUD que lo cubre:</p>
 * <ul>
 *   <li>Hay política: PENDIENTE_APROBACION y se avisa al rol aprobador.</li>
 *   <li>Algún producto sin costo de referencia: se exige la política de mayor tramo.</li>
 *   <li>Ninguna política aplica: APROBADA directa y se avisa a COMPRADOR.</li>
 * </ul>
 * <p>La decisión del aprobador está en {@link AprobacionSolicitudService}.</p>
 */
@Service
public class SolicitudService {

    private static final Logger log = LoggerFactory.getLogger(SolicitudService.class);
    /** Campos por los que se puede ordenar el listado. */
    private static final Set<String> CAMPOS_ORDENABLES = Set.of("creadoEn", "enviadoEn", "fechaRequerida",
            "consecutivo", "estado", "montoEstimado");
    /** Roles que ven todas las solicitudes; el resto solo ve las propias. */
    static final String[] ROLES_VEN_TODAS = { Roles.COMPRADOR, Roles.ADMIN_COMPRAS, Roles.AUDITOR, Roles.APROBADOR };
    /** Estados en los que una solicitud ya enviada todavía no tiene RFQ: cuentan para detectar duplicadas. */
    private static final Set<EstadoSolicitud> ESTADOS_ABIERTAS = Set.of(EstadoSolicitud.PENDIENTE_APROBACION,
            EstadoSolicitud.APROBADA);

    private final SolicitudCompraRepository solicitudRepository;
    private final AprobacionSolicitudRepository aprobacionRepository;
    private final ProductoRepository productoRepository;
    private final UnidadMedidaRepository unidadRepository;
    private final UnidadMedidaService unidadMedidaService;
    private final UsuarioRepository usuarioRepository;
    private final CentroCostoRepository centroCostoRepository;
    private final PoliticaAprobacionService politicaService;
    private final ConsecutivoGenerator consecutivos;
    private final AuditoriaService auditoriaService;
    private final NotificacionService notificacionService;
    private final PresupuestoService presupuestoService;

    /**
     * @param solicitudRepository  solicitudes
     * @param aprobacionRepository historial de decisiones
     * @param productoRepository   productos de las líneas
     * @param unidadRepository     unidades de las líneas
     * @param unidadMedidaService  conversión de unidades
     * @param usuarioRepository    solicitante (centro de costo)
     * @param centroCostoRepository verificación del centro de costo
     * @param politicaService      políticas de aprobación
     * @param consecutivos         números SOL-000001
     * @param auditoriaService     registro de acciones
     * @param notificacionService  avisos a aprobadores y compradores
     * @param presupuestoService   control de presupuesto por centro de costo (18-a)
     */
    public SolicitudService(SolicitudCompraRepository solicitudRepository,
            AprobacionSolicitudRepository aprobacionRepository, ProductoRepository productoRepository,
            UnidadMedidaRepository unidadRepository, UnidadMedidaService unidadMedidaService,
            UsuarioRepository usuarioRepository, CentroCostoRepository centroCostoRepository,
            PoliticaAprobacionService politicaService, ConsecutivoGenerator consecutivos,
            AuditoriaService auditoriaService, NotificacionService notificacionService,
            PresupuestoService presupuestoService) {
        this.solicitudRepository = solicitudRepository;
        this.aprobacionRepository = aprobacionRepository;
        this.productoRepository = productoRepository;
        this.unidadRepository = unidadRepository;
        this.unidadMedidaService = unidadMedidaService;
        this.usuarioRepository = usuarioRepository;
        this.centroCostoRepository = centroCostoRepository;
        this.politicaService = politicaService;
        this.consecutivos = consecutivos;
        this.auditoriaService = auditoriaService;
        this.notificacionService = notificacionService;
        this.presupuestoService = presupuestoService;
    }

    /**
     * Crea una solicitud en BORRADOR con el centro de costo del solicitante.
     *
     * @param req datos validados
     * @return solicitud creada
     * @throws NegocioException si el solicitante no tiene centro de costo activo
     *                          o una unidad no mide lo mismo que su producto
     */
    @Transactional
    public SolicitudResponse crear(SolicitudRequest req) {
        UsuarioAutenticado sesion = SesionActual.usuario();
        SolicitudCompra s = new SolicitudCompra();
        s.setCentroCostoId(centroCostoDe(sesion.id()));
        s.setUsuarioSolicitanteId(sesion.id());
        s.setConsecutivo(consecutivos.siguiente(ConsecutivoGenerator.SOLICITUD));
        copiar(s, req);
        SolicitudCompra guardada = solicitudRepository.save(s);
        auditoriaService.exito(AccionAuditoria.SOLICITUD_CREAR, EntidadAuditada.SOLICITUD_COMPRA, guardada.getId(),
                guardada.getConsecutivo() + "; lineas=" + guardada.getLineas().size());
        return respuesta(guardada);
    }

    /**
     * Edita una solicitud propia en BORRADOR o DEVUELTA (reemplaza sus líneas).
     *
     * @param id  id de la solicitud
     * @param req datos validados
     * @return solicitud actualizada
     * @throws AccesoDenegadoException si no es el solicitante
     * @throws NegocioException        si ya no es editable
     */
    @Transactional
    public SolicitudResponse actualizar(Long id, SolicitudRequest req) {
        SolicitudCompra s = propiaEditable(id);
        s.getLineas().clear();
        solicitudRepository.flush(); // borra las líneas viejas antes de insertar las nuevas
        copiar(s, req);
        auditoriaService.exito(AccionAuditoria.SOLICITUD_EDITAR, EntidadAuditada.SOLICITUD_COMPRA, id,
                s.getConsecutivo() + "; lineas=" + s.getLineas().size());
        return respuesta(s);
    }

    /**
     * Envía una solicitud propia: estima el monto, asigna la política y la pasa a aprobación
     * (o la deja APROBADA si ninguna política aplica).
     *
     * @param id id de la solicitud
     * @return solicitud enviada
     * @throws AccesoDenegadoException si no es el solicitante
     * @throws NegocioException        si ya no es editable o su centro de costo se desactivó
     */
    @Transactional
    public SolicitudResponse enviar(Long id) {
        return enviar(id, null);
    }

    /**
     * Envía una solicitud propia aplicando los controles del paso 18-a:
     * <ul>
     *   <li><b>Presupuesto:</b> si el centro de costo tiene presupuesto en el mes de la fecha requerida y el monto
     *       estimado lo excede, se rechaza con 409 {@code PRESUPUESTO_EXCEDIDO}. Con justificación se envía como
     *       excepción y pasa siempre por la política de mayor tramo.</li>
     *   <li><b>Duplicadas:</b> si otra solicitud abierta (pendiente o aprobada) del mismo centro de costo ya pide
     *       alguno de los productos, se rechaza con 409 {@code SOLICITUD_DUPLICADA}. Con justificación se envía.</li>
     * </ul>
     * <p>Toda excepción queda guardada en la solicitud y en la auditoría.</p>
     *
     * @param id                     id de la solicitud
     * @param justificacionExcepcion por qué se envía a pesar de un exceso o un duplicado ({@code null} o vacía = sin
     *                               excepción)
     * @return solicitud enviada
     * @throws AccesoDenegadoException si no es el solicitante
     * @throws NegocioException        si ya no es editable, su centro de costo se desactivó, excede el presupuesto
     *                                 o está duplicada sin justificación, o no hay aprobador para una excepción
     */
    @Transactional
    public SolicitudResponse enviar(Long id, String justificacionExcepcion) {
        SolicitudCompra s = propiaEditable(id);
        if (!centroCostoRepository.existsByIdAndActivoTrue(s.getCentroCostoId())) {
            throw new NegocioException("CENTRO_COSTO_INACTIVO",
                    "El centro de costo de la solicitud está inactivo; contacte al administrador");
        }
        Estimacion e = estimar(s);
        String justificacion = justificacionExcepcion == null || justificacionExcepcion.isBlank() ? null
                : justificacionExcepcion.trim();
        EvaluacionPresupuesto presupuesto = presupuestoService.evaluar(s.getCentroCostoId(), s.getFechaRequerida(),
                e.monto(), s.getId());
        if (presupuesto.excede() && justificacion == null) {
            throw new NegocioException("PRESUPUESTO_EXCEDIDO", mensajeExceso(s, e, presupuesto));
        }
        List<String> duplicadas = duplicadas(s);
        if (!duplicadas.isEmpty() && justificacion == null) {
            throw new NegocioException("SOLICITUD_DUPLICADA", "Ya hay solicitudes abiertas de este centro de costo con "
                    + "alguno de los mismos productos: " + String.join(", ", duplicadas)
                    + ". Envíela con una justificación si de verdad es otra compra");
        }
        s.setMontoEstimado(e.monto());
        s.setMontoIncompleto(e.incompleto());
        s.setExcedePresupuesto(presupuesto.excede());
        s.setJustificacionExcepcion(presupuesto.excede() || !duplicadas.isEmpty() ? justificacion : null);
        Optional<PoliticaAprobacion> politica = presupuesto.excede() || e.incompleto()
                ? politicaService.deMayorTramo(TipoPolitica.SOLICITUD)
                : politicaService.aplicable(TipoPolitica.SOLICITUD, e.monto());
        if (presupuesto.excede() && politica.isEmpty()) {
            throw new NegocioException("PRESUPUESTO_EXCEDIDO", mensajeExceso(s, e, presupuesto)
                    + ". La empresa no tiene una política de aprobación que pueda autorizar la excepción");
        }
        s.setEnviadoEn(LocalDateTime.now());
        s.setSlaAvisoEn(null);
        s.setEscaladaEn(null);

        if (politica.isPresent()) {
            PoliticaAprobacion p = politica.get();
            s.setPoliticaAprobacionId(p.getId());
            s.setEstado(EstadoSolicitud.PENDIENTE_APROBACION);
            // Si quien pide aprueba con ese mismo rol (p. ej. el jefe de compras), la decide el dueño de la empresa.
            // Si quien pide ES el dueño, no hay a quién subirla: la decide otra persona con el rol de la política
            // (nadie aprueba lo suyo, lo garantiza la aprobación).
            var roles = SesionActual.usuario().roles();
            s.setApruebaDueno(roles.contains(p.getRolAprobador().getCodigo()) && !roles.contains(Roles.ADMIN_SISTEMA));
            notificacionService.notificarRol(s.isApruebaDueno() ? Roles.ADMIN_SISTEMA : p.getRolAprobador().getCodigo(),
                    EventoNotificacion.SOLICITUD_PENDIENTE_APROBACION, "La solicitud " + s.getConsecutivo()
                            + " por " + describirMonto(e) + " espera su aprobación");
        } else {
            s.setPoliticaAprobacionId(null);
            s.setEstado(EstadoSolicitud.APROBADA);
            notificacionService.notificarRol(Roles.COMPRADOR, EventoNotificacion.SOLICITUD_APROBADA,
                    "La solicitud " + s.getConsecutivo() + " quedó aprobada (sin política aplicable) y puede pasar a RFQ");
        }
        auditoriaService.exito(AccionAuditoria.SOLICITUD_ENVIAR, EntidadAuditada.SOLICITUD_COMPRA, id,
                s.getConsecutivo() + "; monto=" + describirMonto(e) + "; estado=" + s.getEstado()
                        + "; politica=" + s.getPoliticaAprobacionId());
        if (s.getJustificacionExcepcion() != null) {
            auditoriaService.exito(AccionAuditoria.SOLICITUD_EXCEPCION, EntidadAuditada.SOLICITUD_COMPRA, id,
                    s.getConsecutivo() + "; excedePresupuesto=" + presupuesto.excede() + "; duplicadas=" + duplicadas
                            + "; justificacion=" + s.getJustificacionExcepcion());
        }
        log.info("Solicitud {} enviada: {} ({})", s.getConsecutivo(), s.getEstado(), describirMonto(e));
        return respuesta(s);
    }

    /**
     * Cancela una solicitud que aún no pasó a RFQ. Puede hacerlo el solicitante o ADMIN_COMPRAS.
     *
     * @param id id de la solicitud
     * @return solicitud cancelada
     * @throws AccesoDenegadoException si no es el solicitante ni ADMIN_COMPRAS
     * @throws NegocioException        si ya no se puede cancelar
     */
    @Transactional
    public SolicitudResponse cancelar(Long id) {
        UsuarioAutenticado sesion = SesionActual.usuario();
        SolicitudCompra s = bloquear(id);
        if (!s.getUsuarioSolicitanteId().equals(sesion.id()) && !sesion.tieneAlgunRol(Roles.ADMIN_COMPRAS)) {
            throw new AccesoDenegadoException("Solo el solicitante o compras pueden cancelar la solicitud");
        }
        if (!s.getEstado().esCancelable()) {
            throw new NegocioException("TRANSICION_INVALIDA",
                    "Una solicitud " + s.getEstado() + " no se puede cancelar");
        }
        EstadoSolicitud anterior = s.getEstado();
        s.setEstado(EstadoSolicitud.CANCELADA);
        auditoriaService.exito(AccionAuditoria.SOLICITUD_CANCELAR, EntidadAuditada.SOLICITUD_COMPRA, id,
                s.getConsecutivo() + "; estado anterior=" + anterior);
        return respuesta(s);
    }

    /**
     * Detalle de una solicitud. El solicitante solo ve las suyas; compras, aprobadores y auditoría ven todas.
     *
     * @param id id de la solicitud
     * @return solicitud con líneas e historial
     * @throws NoEncontradoException si no existe o no puede verla
     */
    @Transactional(readOnly = true)
    public SolicitudResponse obtener(Long id) {
        SolicitudCompra s = solicitudRepository.findWithLineasById(id)
                .orElseThrow(() -> new NoEncontradoException("Solicitud", id));
        UsuarioAutenticado sesion = SesionActual.usuario();
        if (!sesion.tieneAlgunRol(ROLES_VEN_TODAS) && !s.getUsuarioSolicitanteId().equals(sesion.id())) {
            throw new NoEncontradoException("Solicitud", id);
        }
        return respuesta(s);
    }

    /**
     * Lista solicitudes con filtros. Quien no tiene un rol de {@link #ROLES_VEN_TODAS} solo ve las propias.
     *
     * @param texto         coincidencia en el consecutivo
     * @param estado        estado
     * @param solicitanteId solicitante (ignorado si solo puede ver las propias)
     * @param centroCostoId centro de costo
     * @param pageable      página y orden
     * @return página de solicitudes
     */
    @Transactional(readOnly = true)
    public PageResponse<SolicitudResumenResponse> buscar(String texto, EstadoSolicitud estado, Long solicitanteId,
            Long centroCostoId, Pageable pageable) {
        UsuarioAutenticado sesion = SesionActual.usuario();
        Long solicitante = sesion.tieneAlgunRol(ROLES_VEN_TODAS) ? solicitanteId : sesion.id();
        String filtro = texto == null || texto.isBlank() ? null : texto.trim().toUpperCase();
        return PageResponse.of(solicitudRepository.buscar(filtro, estado, solicitante, centroCostoId,
                Ordenamiento.validar(pageable, CAMPOS_ORDENABLES)), SolicitudResumenResponse::of);
    }

    // ----------------------------------------------------------------- apoyo

    /**
     * Respuesta completa con el historial de decisiones.
     *
     * @param s solicitud
     * @return DTO
     */
    SolicitudResponse respuesta(SolicitudCompra s) {
        return SolicitudResponse.of(s,
                s.getId() == null ? List.of()
                        : aprobacionRepository.findBySolicitudCompraIdOrderByDecididoEnAscIdAsc(s.getId()));
    }

    /**
     * Bloquea la fila y la carga con sus líneas.
     *
     * @param id id de la solicitud
     * @return solicitud bloqueada
     * @throws NoEncontradoException si no existe
     */
    SolicitudCompra bloquear(Long id) {
        solicitudRepository.bloquear(id).orElseThrow(() -> new NoEncontradoException("Solicitud", id));
        return solicitudRepository.findWithLineasById(id).orElseThrow(() -> new NoEncontradoException("Solicitud", id));
    }

    /**
     * Carga una solicitud del usuario de la sesión que todavía se puede editar.
     *
     * @param id id de la solicitud
     * @return solicitud bloqueada
     * @throws AccesoDenegadoException si no es el solicitante
     * @throws NegocioException        si no está en BORRADOR ni DEVUELTA
     */
    private SolicitudCompra propiaEditable(Long id) {
        SolicitudCompra s = bloquear(id);
        if (!s.getUsuarioSolicitanteId().equals(SesionActual.usuario().id())) {
            throw new AccesoDenegadoException("Solo el solicitante puede modificar o enviar la solicitud");
        }
        if (!s.getEstado().esEditable()) {
            throw new NegocioException("SOLICITUD_NO_EDITABLE",
                    "La solicitud está " + s.getEstado() + "; solo se edita o envía en BORRADOR o DEVUELTA");
        }
        return s;
    }

    /**
     * Centro de costo activo del solicitante (UC-04: requisito para solicitar).
     *
     * @param usuarioId solicitante
     * @return id del centro de costo
     * @throws NegocioException si no tiene o está inactivo
     */
    private Long centroCostoDe(Long usuarioId) {
        Long centroCostoId = usuarioRepository.findById(usuarioId).map(u -> u.getCentroCostoId()).orElse(null);
        if (centroCostoId == null) {
            throw new NegocioException("SIN_CENTRO_COSTO",
                    "Su usuario no tiene centro de costo; pida al administrador que se lo asigne");
        }
        if (!centroCostoRepository.existsByIdAndActivoTrue(centroCostoId)) {
            throw new NegocioException("CENTRO_COSTO_INACTIVO",
                    "Su centro de costo está inactivo; contacte al administrador");
        }
        return centroCostoId;
    }

    /**
     * Copia motivo, fecha y líneas del request. Verifica que cada unidad mida lo mismo que la del producto.
     *
     * @param s   solicitud destino (sin líneas)
     * @param req datos validados
     */
    private void copiar(SolicitudCompra s, SolicitudRequest req) {
        s.setMotivo(req.motivo().trim());
        s.setFechaRequerida(req.fechaRequerida());
        for (LineaSolicitudRequest l : req.lineas()) {
            Producto producto = productoRepository.findWithRelacionesById(l.productoId())
                    .filter(Producto::isActivo)
                    .orElseThrow(() -> new NegocioException("PRODUCTO_INACTIVO",
                            "El producto " + l.productoId() + " no existe o está inactivo"));
            UnidadMedida unidad = unidadRepository.findById(l.unidadMedidaId())
                    .orElseThrow(() -> new NoEncontradoException("Unidad de medida", l.unidadMedidaId()));
            // lanza 409 UNIDADES_INCOMPATIBLES si la unidad no mide lo mismo que la del producto
            unidadMedidaService.convertir(l.cantidad(), unidad.getId(), producto.getUnidadMedida().getId());
            SolicitudCompraLinea linea = new SolicitudCompraLinea();
            linea.setProducto(producto);
            linea.setUnidadMedida(unidad);
            linea.setCantidad(l.cantidad());
            s.agregarLinea(linea);
        }
    }

    /**
     * Monto estimado: suma de (cantidad convertida a la unidad del producto x costo de referencia).
     *
     * @param s solicitud con líneas
     * @return monto (2 decimales) y si faltó el costo de algún producto
     */
    Estimacion estimar(SolicitudCompra s) {
        BigDecimal total = BigDecimal.ZERO;
        boolean incompleto = false;
        for (SolicitudCompraLinea l : s.getLineas()) {
            BigDecimal costo = l.getProducto().getCostoReferencia();
            if (costo == null) {
                incompleto = true;
                continue;
            }
            BigDecimal cantidad = unidadMedidaService.convertir(l.getCantidad(), l.getUnidadMedida().getId(),
                    l.getProducto().getUnidadMedida().getId());
            total = total.add(cantidad.multiply(costo));
        }
        return new Estimacion(total.setScale(2, RoundingMode.HALF_UP), incompleto);
    }

    /**
     * Consecutivos de otras solicitudes abiertas del mismo centro de costo que piden alguno de los productos.
     *
     * @param s solicitud que se envía
     * @return consecutivos, vacío si no hay duplicadas
     */
    private List<String> duplicadas(SolicitudCompra s) {
        List<Long> productos = s.getLineas().stream().map(l -> l.getProducto().getId()).distinct().toList();
        if (productos.isEmpty()) {
            return List.of();
        }
        return solicitudRepository.consecutivosConProductos(s.getCentroCostoId(), productos, ESTADOS_ABIERTAS,
                s.getId());
    }

    /**
     * Mensaje del 409 cuando el monto excede el presupuesto del mes.
     *
     * @param s  solicitud
     * @param e  estimación
     * @param ev evaluación del presupuesto
     * @return texto con tope, comprometido y monto de la solicitud
     */
    private static String mensajeExceso(SolicitudCompra s, Estimacion e, EvaluacionPresupuesto ev) {
        return "El presupuesto del centro de costo para " + PresupuestoService.periodo(s.getFechaRequerida().getYear(),
                s.getFechaRequerida().getMonthValue()) + " es $ " + ev.presupuesto().toPlainString()
                + ", ya hay $ " + ev.comprometido().toPlainString() + " comprometidos y esta solicitud suma "
                + describirMonto(e) + ". Envíela con una justificación para que la apruebe el nivel más alto";
    }

    /**
     * Texto del monto para avisos y auditoría.
     *
     * @param e estimación
     * @return p. ej. "$ 1500.00" o "$ 1500.00 + productos sin costo"
     */
    private static String describirMonto(Estimacion e) {
        return "$ " + e.monto().toPlainString() + (e.incompleto() ? " + productos sin costo de referencia" : "");
    }

    /**
     * Resultado de la estimación.
     *
     * @param monto      suma de las líneas con costo
     * @param incompleto {@code true} si alguna línea no tenía costo de referencia
     */
    record Estimacion(BigDecimal monto, boolean incompleto) {
    }
}
