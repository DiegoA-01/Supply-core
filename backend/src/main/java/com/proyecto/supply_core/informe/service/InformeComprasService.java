package com.proyecto.supply_core.informe.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.proyecto.supply_core.auditoria.enums.AccionAuditoria;
import com.proyecto.supply_core.auditoria.enums.EntidadAuditada;
import com.proyecto.supply_core.auditoria.service.AuditoriaService;
import com.proyecto.supply_core.catalogo.service.UnidadMedidaService;
import com.proyecto.supply_core.common.dto.PageResponse;
import com.proyecto.supply_core.common.exception.NegocioException;
import com.proyecto.supply_core.common.exception.NoEncontradoException;
import com.proyecto.supply_core.common.util.Ordenamiento;
import com.proyecto.supply_core.compra.adjudicacion.entity.Adjudicacion;
import com.proyecto.supply_core.compra.cotizacion.entity.Cotizacion;
import com.proyecto.supply_core.compra.rfq.entity.Rfq;
import com.proyecto.supply_core.compra.solicitud.entity.SolicitudCompra;
import com.proyecto.supply_core.compra.solicitud.entity.SolicitudCompraLinea;
import com.proyecto.supply_core.empresa.entity.CentroCosto;
import com.proyecto.supply_core.empresa.repository.CentroCostoRepository;
import com.proyecto.supply_core.informe.dto.EnviarInformeRequest;
import com.proyecto.supply_core.informe.dto.InformeContenido;
import com.proyecto.supply_core.informe.dto.InformeContenido.AdjudicacionFila;
import com.proyecto.supply_core.informe.dto.InformeContenido.CotizacionFila;
import com.proyecto.supply_core.informe.dto.InformeContenido.LineaFila;
import com.proyecto.supply_core.informe.dto.InformeContenido.Resumen;
import com.proyecto.supply_core.informe.dto.InformeContenido.RfqFila;
import com.proyecto.supply_core.informe.dto.InformeContenido.SolicitudFila;
import com.proyecto.supply_core.informe.dto.InformeResponse;
import com.proyecto.supply_core.informe.dto.PedirInformeRequest;
import com.proyecto.supply_core.informe.entity.InformeCompras;
import com.proyecto.supply_core.informe.enums.EstadoInforme;
import com.proyecto.supply_core.informe.repository.InformeComprasRepository;
import com.proyecto.supply_core.notificacion.enums.EventoNotificacion;
import com.proyecto.supply_core.notificacion.service.NotificacionService;
import com.proyecto.supply_core.reporte.service.ReporteService;
import com.proyecto.supply_core.security.context.Roles;
import com.proyecto.supply_core.security.context.SesionActual;
import com.proyecto.supply_core.security.context.UsuarioAutenticado;
import com.proyecto.supply_core.usuario.entity.Usuario;
import com.proyecto.supply_core.usuario.repository.UsuarioRepository;

import tools.jackson.databind.json.JsonMapper;

/**
 * Informe de compras para el dueño de la empresa. El administrador (ADMIN_SISTEMA) lo pide; el área de compras
 * (ADMIN_COMPRAS) lo revisa en vivo, escribe su análisis y lo envía. Al enviarse se guarda una foto fija del periodo
 * que ya no cambia aunque después cambien los datos. Todo lo cuenta y lo suma Java.
 */
@Service
public class InformeComprasService {

    private static final Logger log = LoggerFactory.getLogger(InformeComprasService.class);
    /** Periodo más largo que se puede pedir. */
    private static final long MAXIMO_DIAS = 366;

    private final InformeComprasRepository informes;
    private final UsuarioRepository usuarios;
    private final CentroCostoRepository centros;
    private final ReporteService reportes;
    private final NotificacionService notificaciones;
    private final AuditoriaService auditoria;
    private final UnidadMedidaService unidades;
    private final com.proyecto.supply_core.reporte.service.VentaReporteService ventaReporte;
    private final com.proyecto.supply_core.plataforma.service.AccesoEmpresaService accesoEmpresa;
    /** Convierte la foto fija a JSON y de vuelta. */
    private final JsonMapper json = JsonMapper.builder().build();

    /**
     * @param informes       informes y consultas del periodo
     * @param usuarios       nombres de quien pide y envía
     * @param centros        nombres de los centros de costo
     * @param reportes       reporte de gasto que se incluye en el informe
     * @param notificaciones avisos de pedido y de envío
     * @param auditoria      registro de pedir y enviar
     * @param unidades       conversión de la cantidad pedida a la unidad del producto
     * @param ventaReporte   resumen de ventas del periodo (spec 009)
     * @param accesoEmpresa  si la empresa tiene el módulo de ventas
     */
    public InformeComprasService(InformeComprasRepository informes, UsuarioRepository usuarios,
            CentroCostoRepository centros, ReporteService reportes, NotificacionService notificaciones,
            AuditoriaService auditoria, UnidadMedidaService unidades,
            com.proyecto.supply_core.reporte.service.VentaReporteService ventaReporte,
            com.proyecto.supply_core.plataforma.service.AccesoEmpresaService accesoEmpresa) {
        this.ventaReporte = ventaReporte;
        this.accesoEmpresa = accesoEmpresa;
        this.informes = informes;
        this.usuarios = usuarios;
        this.centros = centros;
        this.reportes = reportes;
        this.notificaciones = notificaciones;
        this.auditoria = auditoria;
        this.unidades = unidades;
    }

    /**
     * Arma el informe del periodo con los datos actuales, sin guardarlo (lo que revisa compras antes de enviar).
     *
     * @param desde primer día
     * @param hasta último día
     * @return contenido del periodo
     * @throws NegocioException si el periodo está al revés o es de más de un año
     */
    @Transactional(readOnly = true)
    public InformeContenido vistaPrevia(LocalDate desde, LocalDate hasta) {
        return armar(desde, hasta);
    }

    /**
     * El administrador de la empresa pide un informe; al área de compras le llega el aviso.
     *
     * @param req periodo y mensaje
     * @return informe en estado PEDIDO
     * @throws NegocioException si el periodo no es válido
     */
    @Transactional
    public InformeResponse pedir(PedirInformeRequest req) {
        validarPeriodo(req.desde(), req.hasta());
        UsuarioAutenticado sesion = SesionActual.usuario();
        InformeCompras i = new InformeCompras();
        i.setEstado(EstadoInforme.PEDIDO);
        i.setPeriodoDesde(req.desde());
        i.setPeriodoHasta(req.hasta());
        i.setMensajePedido(req.mensaje() == null || req.mensaje().isBlank() ? null : req.mensaje().trim());
        i.setPedidoPorUsuarioId(sesion.id());
        i.setPedidoEn(LocalDateTime.now());
        informes.save(i);
        notificaciones.notificarRol(Roles.ADMIN_COMPRAS, EventoNotificacion.INFORME_PEDIDO,
                sesion.nombre() + " pidió un informe de compras del " + req.desde() + " al " + req.hasta()
                        + (i.getMensajePedido() == null ? "" : ": " + i.getMensajePedido()));
        auditoria.exito(AccionAuditoria.INFORME_PEDIR, EntidadAuditada.INFORME_COMPRAS, i.getId(),
                "periodo=" + req.desde() + ".." + req.hasta());
        return respuesta(i, false);
    }

    /**
     * El área de compras envía el informe: guarda la foto fija del periodo y su análisis, y avisa al administrador.
     * Si responde a un pedido, completa ese pedido; si no, crea uno nuevo ya enviado.
     *
     * @param req periodo, análisis y pedido que responde
     * @return informe ENVIADO con su contenido
     * @throws NoEncontradoException si el pedido no existe en la empresa
     * @throws NegocioException      si el pedido ya se respondió o el periodo no es válido
     */
    @Transactional
    public InformeResponse enviar(EnviarInformeRequest req) {
        UsuarioAutenticado sesion = SesionActual.usuario();
        InformeCompras i;
        if (req.pedidoId() != null) {
            i = informes.findById(req.pedidoId()).orElseThrow(() -> new NoEncontradoException("Informe", req.pedidoId()));
            if (i.getEstado() != EstadoInforme.PEDIDO) {
                throw new NegocioException("INFORME_YA_ENVIADO", "Ese pedido ya se respondió con un informe");
            }
        } else {
            i = new InformeCompras();
        }
        InformeContenido contenido = armar(req.desde(), req.hasta());
        i.setEstado(EstadoInforme.ENVIADO);
        i.setPeriodoDesde(req.desde());
        i.setPeriodoHasta(req.hasta());
        i.setAnalisis(req.analisis().trim());
        i.setContenido(json.writeValueAsString(contenido));
        i.setEnviadoPorUsuarioId(sesion.id());
        i.setEnviadoEn(LocalDateTime.now());
        informes.save(i);

        String mensaje = sesion.nombre() + " te envió el informe de compras del " + req.desde() + " al " + req.hasta();
        if (i.getPedidoPorUsuarioId() != null) {
            notificaciones.notificar(i.getPedidoPorUsuarioId(), EventoNotificacion.INFORME_RECIBIDO, mensaje);
        } else {
            notificaciones.notificarRol(Roles.ADMIN_SISTEMA, EventoNotificacion.INFORME_RECIBIDO, mensaje);
        }
        auditoria.exito(AccionAuditoria.INFORME_ENVIAR, EntidadAuditada.INFORME_COMPRAS, i.getId(),
                "periodo=" + req.desde() + ".." + req.hasta() + "; pedido=" + req.pedidoId()
                        + "; solicitudes=" + contenido.resumen().solicitudes()
                        + "; adjudicado=" + contenido.resumen().montoAdjudicado());
        log.info("Informe de compras {} enviado por el usuario {}", i.getId(), sesion.id());
        return respuesta(i, true);
    }

    /**
     * Informes de la empresa (pedidos y enviados), del más reciente al más antiguo, sin el contenido.
     *
     * @param pageable página
     * @return página de informes
     */
    @Transactional(readOnly = true)
    public PageResponse<InformeResponse> listar(Pageable pageable) {
        var pagina = informes.recientes(Ordenamiento.validar(pageable, Set.of("creadoEn")));
        Map<Long, String> nombres = nombres(pagina.getContent().stream()
                .flatMap(i -> Stream.of(i.getPedidoPorUsuarioId(), i.getEnviadoPorUsuarioId()))
                .filter(Objects::nonNull).collect(Collectors.toSet()));
        return PageResponse.of(pagina, i -> respuesta(i, false, nombres));
    }

    /**
     * Detalle de un informe con su análisis y su foto fija.
     *
     * @param id informe
     * @return informe completo
     * @throws NoEncontradoException si no existe en la empresa
     */
    @Transactional(readOnly = true)
    public InformeResponse obtener(Long id) {
        return respuesta(buscar(id), true);
    }

    /**
     * El administrador abrió el informe: se guarda la primera vez que lo leyó.
     *
     * @param id informe ENVIADO
     * @return informe completo
     * @throws NoEncontradoException si no existe en la empresa
     * @throws NegocioException      si todavía no se envió
     */
    @Transactional
    public InformeResponse marcarLeido(Long id) {
        InformeCompras i = buscar(id);
        if (i.getEstado() != EstadoInforme.ENVIADO) {
            throw new NegocioException("INFORME_SIN_ENVIAR", "El informe todavía no se ha enviado");
        }
        if (i.getLeidoEn() == null) {
            i.setLeidoEn(LocalDateTime.now());
        }
        return respuesta(i, true);
    }

    // ----------------------------------------------------------------- armado

    /**
     * Arma la foto del periodo: solicitudes enviadas, RFQ creadas, cotizaciones recibidas, adjudicaciones y gasto.
     *
     * @param desde primer día
     * @param hasta último día
     * @return contenido del informe
     */
    private InformeContenido armar(LocalDate desde, LocalDate hasta) {
        validarPeriodo(desde, hasta);
        LocalDateTime ini = desde.atStartOfDay();
        LocalDateTime fin = hasta.plusDays(1).atStartOfDay();

        List<SolicitudCompra> solicitudes = informes.solicitudesEnviadas(ini, fin);
        List<Rfq> rfqs = informes.rfqsCreadas(ini, fin);
        List<Cotizacion> cotizaciones = informes.cotizacionesRecibidas(ini, fin);
        List<Adjudicacion> adjudicaciones = informes.adjudicaciones(ini, fin);

        Map<Long, String> nombres = nombres(solicitudes.stream().map(SolicitudCompra::getUsuarioSolicitanteId)
                .collect(Collectors.toSet()));
        Map<Long, String> nombresCentros = centros.findAllById(solicitudes.stream().map(SolicitudCompra::getCentroCostoId)
                .filter(Objects::nonNull).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(CentroCosto::getId, CentroCosto::getNombre));

        Map<String, Long> porEstado = new TreeMap<>(solicitudes.stream()
                .collect(Collectors.groupingBy(s -> s.getEstado().name(), Collectors.counting())));
        Resumen resumen = new Resumen(solicitudes.size(),
                sumar(solicitudes.stream().map(SolicitudCompra::getMontoEstimado).toList()), porEstado,
                rfqs.size(), cotizaciones.size(), adjudicaciones.size(),
                adjudicaciones.stream().filter(a -> !a.isSigueRecomendacion()).count(),
                sumar(adjudicaciones.stream().map(Adjudicacion::getMontoTotal).toList()));

        boolean recortado = solicitudes.size() > InformeContenido.MAXIMO_FILAS || rfqs.size() > InformeContenido.MAXIMO_FILAS
                || cotizaciones.size() > InformeContenido.MAXIMO_FILAS
                || adjudicaciones.size() > InformeContenido.MAXIMO_FILAS;

        return new InformeContenido(desde, hasta, LocalDateTime.now(), resumen,
                primeras(solicitudes, s -> fila(s, nombres.getOrDefault(s.getUsuarioSolicitanteId(), "—"),
                        nombresCentros.getOrDefault(s.getCentroCostoId(), "—"))),
                primeras(rfqs, r -> new RfqFila(r.getConsecutivo(), r.getSolicitud().getConsecutivo(), r.getEstado().name(),
                        r.getInvitados().size(), informes.cotizacionesDeRfq(r.getId()), r.getFechaLimite())),
                primeras(cotizaciones, c -> new CotizacionFila(c.getRfq().getConsecutivo(),
                        c.getProveedor().getRazonSocial(), c.getEstado().name(), c.getTotal(), c.getPlazoEntregaDias(),
                        c.getEnviadoEn())),
                primeras(adjudicaciones, a -> new AdjudicacionFila(a.getRfq().getConsecutivo(),
                        a.getCotizacion().getProveedor().getRazonSocial(), a.getMontoTotal(), a.isSigueRecomendacion(),
                        a.getJustificacion(), a.getEstado().name(), a.getDecididoEn())),
                reportes.gasto(desde, hasta, null, null), recortado, reportes.gastoPorSede(desde, hasta),
                ventas(desde, hasta));
    }

    /**
     * Resumen de ventas del periodo con el mismo cálculo del reporte de ventas (spec 009).
     *
     * @param desde primer día
     * @param hasta último día
     * @return resumen, o {@code null} si la empresa no tiene el módulo de ventas
     */
    private InformeContenido.VentasResumen ventas(LocalDate desde, LocalDate hasta) {
        Long empresa = com.proyecto.supply_core.common.tenant.EmpresaContexto.opcional();
        com.proyecto.supply_core.plataforma.service.AccesoEmpresaService.Resumen acceso =
                empresa == null ? null : accesoEmpresa.resumen(empresa);
        if (acceso == null || !acceso.modulos().contains(com.proyecto.supply_core.security.context.Modulos.VENTAS)) {
            return null;
        }
        com.proyecto.supply_core.reporte.dto.VentaReporte r = ventaReporte.deEmpresa(desde, hasta,
                com.proyecto.supply_core.reporte.enums.AgrupacionVenta.PRODUCTO);
        return new InformeContenido.VentasResumen(r.totales().ventas(), r.totales().neto(),
                r.totales().ticketPromedio(), r.totales().margen(), r.totales().margenPct(),
                r.filas().stream().limit(5).toList());
    }

    /**
     * Fila de una solicitud con sus productos y la comprobación del monto: cada línea es cantidad (en la unidad del
     * producto) por costo de referencia, y la suma se compara con el monto estimado que se guardó al enviarla.
     *
     * @param s           solicitud
     * @param solicitante nombre de quien la pidió
     * @param centroCosto nombre del centro de costo
     * @return fila del informe
     */
    private SolicitudFila fila(SolicitudCompra s, String solicitante, String centroCosto) {
        List<LineaFila> lineas = s.getLineas().stream().map(this::linea).toList();
        boolean completas = !lineas.isEmpty() && lineas.stream().allMatch(l -> l.subtotal() != null);
        // se suma sin redondear cada línea, igual que el monto estimado, para no descuadrar por centavos
        BigDecimal calculado = completas ? sumar(lineas.stream()
                .map(l -> l.cantidadEnUnidadProducto().multiply(l.costoReferencia())).toList())
                .setScale(2, RoundingMode.HALF_UP) : null;
        Boolean cuadra = calculado == null || s.getMontoEstimado() == null ? null
                : calculado.compareTo(s.getMontoEstimado()) == 0;
        return new SolicitudFila(s.getConsecutivo(), solicitante, centroCosto, s.getEstado().name(), s.getMontoEstimado(),
                s.isMontoIncompleto(), s.getMotivo(), s.getEnviadoEn(), lineas, calculado, cuadra);
    }

    /**
     * Cuenta de una línea pedida: cantidad pasada a la unidad del producto por su costo de referencia.
     *
     * @param l línea de la solicitud
     * @return línea del informe (sin subtotal si no hay costo o no se puede convertir la unidad)
     */
    private LineaFila linea(SolicitudCompraLinea l) {
        var producto = l.getProducto();
        BigDecimal enUnidadProducto;
        try {
            enUnidadProducto = unidades.convertir(l.getCantidad(), l.getUnidadMedida().getId(),
                    producto.getUnidadMedida().getId());
        } catch (RuntimeException e) {
            enUnidadProducto = null; // unidades que ya no se pueden convertir: se muestra sin subtotal
        }
        BigDecimal costo = producto.getCostoReferencia();
        BigDecimal subtotal = costo == null || enUnidadProducto == null ? null
                : enUnidadProducto.multiply(costo).setScale(2, RoundingMode.HALF_UP);
        return new LineaFila(producto.getCodigo(), producto.getNombre(), l.getCantidad(), l.getUnidadMedida().getCodigo(),
                enUnidadProducto, producto.getUnidadMedida().getCodigo(), costo, subtotal);
    }

    /**
     * Exige un periodo en orden y de máximo un año.
     *
     * @param desde primer día
     * @param hasta último día
     * @throws NegocioException si no cumple
     */
    private static void validarPeriodo(LocalDate desde, LocalDate hasta) {
        if (desde == null || hasta == null || hasta.isBefore(desde)) {
            throw new NegocioException("PERIODO_INVALIDO", "La fecha final no puede ser anterior a la inicial");
        }
        if (ChronoUnit.DAYS.between(desde, hasta) >= MAXIMO_DIAS) {
            throw new NegocioException("PERIODO_INVALIDO", "El informe puede abarcar como máximo un año");
        }
    }

    /**
     * Convierte las primeras filas de una lista (máximo {@link InformeContenido#MAXIMO_FILAS}).
     *
     * @param <E>   entidad
     * @param <T>   fila del informe
     * @param lista entidades
     * @param fila  conversión
     * @return filas
     */
    private static <E, T> List<T> primeras(List<E> lista, Function<E, T> fila) {
        return lista.stream().limit(InformeContenido.MAXIMO_FILAS).map(fila).toList();
    }

    /**
     * Suma montos ignorando los vacíos.
     *
     * @param montos montos
     * @return suma (cero si no hay)
     */
    private static BigDecimal sumar(List<BigDecimal> montos) {
        return montos.stream().filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // ----------------------------------------------------------------- respuestas

    /**
     * Informe de la empresa de la sesión.
     *
     * @param id informe
     * @return entidad
     * @throws NoEncontradoException si no existe o es de otra empresa
     */
    private InformeCompras buscar(Long id) {
        return informes.findById(id).orElseThrow(() -> new NoEncontradoException("Informe", id));
    }

    /**
     * Nombres de usuarios por id (admite consultar con {@code null}).
     *
     * @param ids usuarios
     * @return id → nombre completo
     */
    private Map<Long, String> nombres(Set<Long> ids) {
        if (ids.isEmpty()) {
            return new java.util.HashMap<>();
        }
        return usuarios.findAllById(new HashSet<>(ids)).stream()
                .collect(Collectors.toMap(Usuario::getId, Usuario::getNombreCompleto));
    }

    /**
     * Respuesta de un informe buscando los nombres de quien lo pidió y lo envió.
     *
     * @param i        informe
     * @param completo si incluye análisis y contenido
     * @return DTO
     */
    private InformeResponse respuesta(InformeCompras i, boolean completo) {
        Set<Long> ids = new HashSet<>();
        if (i.getPedidoPorUsuarioId() != null) {
            ids.add(i.getPedidoPorUsuarioId());
        }
        if (i.getEnviadoPorUsuarioId() != null) {
            ids.add(i.getEnviadoPorUsuarioId());
        }
        return respuesta(i, completo, nombres(ids));
    }

    /**
     * Respuesta de un informe con los nombres ya resueltos.
     *
     * @param i        informe
     * @param completo si incluye análisis y contenido
     * @param nombres  id → nombre de usuario
     * @return DTO
     */
    private InformeResponse respuesta(InformeCompras i, boolean completo, Map<Long, String> nombres) {
        InformeContenido contenido = completo && i.getContenido() != null
                ? json.readValue(i.getContenido(), InformeContenido.class) : null;
        return new InformeResponse(i.getId(), i.getEstado(), i.getPeriodoDesde(), i.getPeriodoHasta(),
                i.getMensajePedido(), nombres.get(i.getPedidoPorUsuarioId()), i.getPedidoEn(),
                nombres.get(i.getEnviadoPorUsuarioId()), i.getEnviadoEn(), i.getLeidoEn(),
                completo ? i.getAnalisis() : null, contenido);
    }
}
