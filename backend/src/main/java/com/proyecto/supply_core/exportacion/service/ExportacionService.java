package com.proyecto.supply_core.exportacion.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import com.proyecto.supply_core.auditoria.enums.AccionAuditoria;
import com.proyecto.supply_core.auditoria.enums.EntidadAuditada;
import com.proyecto.supply_core.auditoria.service.AuditoriaService;
import com.proyecto.supply_core.common.dto.PageResponse;
import com.proyecto.supply_core.common.exception.FormatoNoSoportadoException;
import com.proyecto.supply_core.common.exception.NegocioException;
import com.proyecto.supply_core.common.exception.ParametroInvalidoException;
import com.proyecto.supply_core.compra.orden.dto.OrdenResponse;
import com.proyecto.supply_core.compra.orden.service.OrdenCompraService;
import com.proyecto.supply_core.compra.solicitud.dto.SolicitudResumenResponse;
import com.proyecto.supply_core.compra.solicitud.enums.EstadoSolicitud;
import com.proyecto.supply_core.compra.solicitud.service.SolicitudService;
import com.proyecto.supply_core.empresa.dto.EmpresaResponse;
import com.proyecto.supply_core.empresa.service.EmpresaService;
import com.proyecto.supply_core.exportacion.dto.ArchivoExportado;
import com.proyecto.supply_core.exportacion.enums.DestinoExportacion;
import com.proyecto.supply_core.exportacion.enums.FormatoExportacion;
import com.proyecto.supply_core.exportacion.generador.ExcelGenerador;
import com.proyecto.supply_core.exportacion.generador.OrdenPdfGenerador;
import com.proyecto.supply_core.inventario.dto.MovimientoFiltro;
import com.proyecto.supply_core.inventario.dto.MovimientoResponse;
import com.proyecto.supply_core.inventario.dto.StockResponse;
import com.proyecto.supply_core.inventario.service.MovimientoInventarioService;
import com.proyecto.supply_core.notificacion.email.Adjunto;
import com.proyecto.supply_core.notificacion.email.EmailClient;
import com.proyecto.supply_core.security.context.SesionActual;
import com.proyecto.supply_core.security.context.UsuarioAutenticado;

/**
 * Exporta información a Excel o PDF y la entrega como descarga o por correo.
 * <p>No consulta la base de datos por su cuenta: usa los mismos Services que las pantallas, así que cada rol exporta
 * solo lo que ya puede ver (p. ej. el solicitante, solo sus solicitudes) y cada empresa solo sus datos. Tampoco
 * calcula montos: escribe los que esos Services devuelven. Cada exportación queda en la auditoría.</p>
 */
@Service
public class ExportacionService {

    /** Tope de filas por archivo; por encima hay que filtrar. */
    public static final int MAX_FILAS = 10_000;

    private static final DateTimeFormatter SELLO = DateTimeFormatter.ofPattern("yyyyMMdd-HHmm");
    private static final Set<FormatoExportacion> SOLO_EXCEL = Set.of(FormatoExportacion.XLSX);
    private static final Set<FormatoExportacion> SOLO_PDF = Set.of(FormatoExportacion.PDF);

    private final MovimientoInventarioService movimientoService;
    private final SolicitudService solicitudService;
    private final OrdenCompraService ordenService;
    private final EmpresaService empresaService;
    private final ExcelGenerador excel;
    private final OrdenPdfGenerador ordenPdf;
    private final AuditoriaService auditoriaService;
    private final EmailClient emailClient;

    /**
     * @param movimientoService existencias y kardex
     * @param solicitudService  solicitudes de compra (aplica quién ve cuáles)
     * @param ordenService      órdenes de compra
     * @param empresaService    datos de la empresa para el encabezado
     * @param excel             generador de libros de Excel
     * @param ordenPdf          generador del PDF de la orden
     * @param auditoriaService  registro de cada exportación
     * @param emailClient       envío del adjunto
     */
    public ExportacionService(MovimientoInventarioService movimientoService, SolicitudService solicitudService,
            OrdenCompraService ordenService, EmpresaService empresaService, ExcelGenerador excel,
            OrdenPdfGenerador ordenPdf, AuditoriaService auditoriaService, EmailClient emailClient) {
        this.movimientoService = movimientoService;
        this.solicitudService = solicitudService;
        this.ordenService = ordenService;
        this.empresaService = empresaService;
        this.excel = excel;
        this.ordenPdf = ordenPdf;
        this.auditoriaService = auditoriaService;
        this.emailClient = emailClient;
    }

    /**
     * Existencias por producto y bodega en Excel.
     *
     * @param formato    {@code xlsx}
     * @param productoId filtro opcional
     * @param bodegaId   filtro opcional
     * @param destino    descarga o correo
     * @return archivo generado
     * @throws FormatoNoSoportadoException si el formato no es xlsx
     * @throws ParametroInvalidoException  si supera {@link #MAX_FILAS}
     */
    public ArchivoExportado stock(String formato, Long productoId, Long bodegaId, DestinoExportacion destino) {
        FormatoExportacion f = exigir(formato, SOLO_EXCEL, "El stock");
        List<StockResponse> stock = movimientoService.existencias(productoId, bodegaId);
        exigirTope(stock.size());
        List<List<Object>> filas = stock.stream().<List<Object>>map(s -> Arrays.asList(s.productoCodigo(),
                s.productoNombre(), s.unidad(), s.bodegaNombre(), s.cantidad())).toList();
        byte[] bytes = excel.generar("Stock por bodega", empresa(), usuario().nombre(),
                List.of("Código", "Producto", "Unidad", "Bodega", "Cantidad"), filas);
        ArchivoExportado archivo = new ArchivoExportado(nombre("inventario-stock", f), f, bytes, filas.size());
        return entregar(archivo, destino, bodegaId, "STOCK; bodegaId=" + bodegaId + "; productoId=" + productoId);
    }

    /**
     * Kardex de un producto en Excel, del movimiento más antiguo al más reciente.
     *
     * @param formato {@code xlsx}
     * @param filtro  filtros; el producto es obligatorio
     * @param destino descarga o correo
     * @return archivo generado
     * @throws FormatoNoSoportadoException si el formato no es xlsx
     * @throws ParametroInvalidoException  si falta el producto o supera {@link #MAX_FILAS}
     */
    public ArchivoExportado kardex(String formato, MovimientoFiltro filtro, DestinoExportacion destino) {
        FormatoExportacion f = exigir(formato, SOLO_EXCEL, "El kardex");
        if (filtro == null || filtro.productoId() == null) {
            throw new ParametroInvalidoException("El kardex se exporta por producto: indica productoId");
        }
        PageResponse<MovimientoResponse> pagina = movimientoService.buscar(filtro,
                PageRequest.of(0, MAX_FILAS, Sort.by(Sort.Direction.ASC, "creadoEn")));
        exigirTope(pagina.totalElementos());
        List<List<Object>> filas = pagina.contenido().stream().<List<Object>>map(m -> Arrays.asList(m.creadoEn(),
                m.tipo().name(), m.bodegaOrigenNombre(), m.bodegaDestinoNombre(), m.cantidad(), m.stockResultante(),
                m.stockResultanteDestino(), m.motivo(),
                m.referenciaTipo() == null ? null : m.referenciaTipo() + " #" + m.referenciaId(), m.usuarioId()))
                .toList();
        String titulo = pagina.contenido().isEmpty() ? "Kardex producto " + filtro.productoId()
                : "Kardex " + pagina.contenido().getFirst().productoCodigo() + " - "
                        + pagina.contenido().getFirst().productoNombre();
        byte[] bytes = excel.generar(titulo, empresa(), usuario().nombre(),
                List.of("Fecha", "Tipo", "Bodega origen", "Bodega destino", "Cantidad", "Stock resultante",
                        "Stock resultante destino", "Motivo", "Referencia", "Usuario (id)"),
                filas);
        ArchivoExportado archivo = new ArchivoExportado(nombre("kardex-producto-" + filtro.productoId(), f), f, bytes,
                filas.size());
        return entregar(archivo, destino, filtro.productoId(), "KARDEX; productoId=" + filtro.productoId());
    }

    /**
     * Solicitudes de compra en Excel, más recientes primero. El solicitante solo exporta las suyas.
     *
     * @param formato       {@code xlsx}
     * @param texto         coincidencia en el consecutivo
     * @param estado        estado
     * @param solicitanteId solicitante (ignorado si solo puede ver las propias)
     * @param centroCostoId centro de costo
     * @param destino       descarga o correo
     * @return archivo generado
     * @throws FormatoNoSoportadoException si el formato no es xlsx
     * @throws ParametroInvalidoException  si supera {@link #MAX_FILAS}
     */
    public ArchivoExportado solicitudes(String formato, String texto, EstadoSolicitud estado, Long solicitanteId,
            Long centroCostoId, DestinoExportacion destino) {
        FormatoExportacion f = exigir(formato, SOLO_EXCEL, "Las solicitudes");
        PageResponse<SolicitudResumenResponse> pagina = solicitudService.buscar(texto, estado, solicitanteId,
                centroCostoId, PageRequest.of(0, MAX_FILAS, Sort.by(Sort.Direction.DESC, "creadoEn")));
        exigirTope(pagina.totalElementos());
        List<List<Object>> filas = pagina.contenido().stream().<List<Object>>map(s -> Arrays.asList(s.consecutivo(),
                s.estado().name(), s.centroCostoId(), s.usuarioSolicitanteId(), s.fechaRequerida(), s.montoEstimado(),
                s.montoIncompleto(), s.enviadoEn(), s.creadoEn())).toList();
        byte[] bytes = excel.generar("Solicitudes de compra", empresa(), usuario().nombre(),
                List.of("Consecutivo", "Estado", "Centro de costo (id)", "Solicitante (id)", "Fecha requerida",
                        "Monto estimado", "Monto incompleto", "Enviada", "Creada"),
                filas);
        ArchivoExportado archivo = new ArchivoExportado(nombre("solicitudes", f), f, bytes, filas.size());
        return entregar(archivo, destino, null, "SOLICITUDES; estado=" + estado + "; centroCostoId=" + centroCostoId);
    }

    /**
     * Orden de compra en PDF con el encabezado de la empresa.
     *
     * @param formato {@code pdf}
     * @param id      orden
     * @param destino descarga o correo
     * @return archivo generado
     * @throws FormatoNoSoportadoException si el formato no es pdf
     * @throws com.proyecto.supply_core.common.exception.NoEncontradoException si no existe en la empresa
     */
    public ArchivoExportado orden(String formato, Long id, DestinoExportacion destino) {
        FormatoExportacion f = exigir(formato, SOLO_PDF, "La orden de compra");
        OrdenResponse orden = ordenService.obtener(id);
        byte[] bytes = ordenPdf.generar(empresa(), orden, usuario().nombre());
        ArchivoExportado archivo = new ArchivoExportado(orden.consecutivo() + "-v" + orden.version() + "." + f.extension(),
                f, bytes, orden.lineas().size());
        return entregar(archivo, destino, id, "ORDEN_COMPRA; " + orden.consecutivo() + " v" + orden.version());
    }

    // ----------------------------------------------------------------- apoyo

    /**
     * Audita la descarga o envía el correo y lo audita. El correo va solo al usuario de la sesión, para que nadie
     * pueda mandar datos de la empresa a una dirección cualquiera.
     *
     * @param archivo   archivo generado
     * @param destino   descarga o correo
     * @param entidadId id del dato exportado ({@code null} en listados)
     * @param detalle   qué se exportó y con qué filtros
     * @return el mismo archivo
     * @throws NegocioException {@code CORREO_NO_ENVIADO} si el envío falla (queda el fallo en auditoría)
     */
    private ArchivoExportado entregar(ArchivoExportado archivo, DestinoExportacion destino, Long entidadId,
            String detalle) {
        String resumen = detalle + "; formato=" + archivo.formato() + "; filas=" + archivo.filas() + "; archivo="
                + archivo.nombre();
        if (destino == DestinoExportacion.DESCARGA) {
            auditoriaService.exito(AccionAuditoria.EXPORTAR_ARCHIVO, EntidadAuditada.EXPORTACION, entidadId, resumen);
            return archivo;
        }
        UsuarioAutenticado u = usuario();
        try {
            emailClient.enviarConAdjunto(u.correo(), "Supply-Core: " + archivo.nombre(),
                    "Hola " + u.nombre() + ", adjunto encontrarás " + archivo.nombre()
                            + ", exportado desde Supply-Core.",
                    new Adjunto(archivo.nombre(), archivo.formato().contentType(), archivo.contenido()));
        } catch (RuntimeException e) {
            auditoriaService.fallo(u.id(), AccionAuditoria.EXPORTAR_CORREO, EntidadAuditada.EXPORTACION, entidadId,
                    resumen + "; para=" + u.correo() + "; error=" + e.getClass().getSimpleName());
            throw new NegocioException("CORREO_NO_ENVIADO", "No se pudo enviar el correo; intenta de nuevo más tarde");
        }
        auditoriaService.exito(AccionAuditoria.EXPORTAR_CORREO, EntidadAuditada.EXPORTACION, entidadId,
                resumen + "; para=" + u.correo());
        return archivo;
    }

    /**
     * Interpreta el formato y verifica que este dato lo ofrezca.
     *
     * @param valor      texto recibido
     * @param soportados formatos de este dato
     * @param que        nombre del dato para el mensaje
     * @return formato
     * @throws FormatoNoSoportadoException si no se reconoce o no se ofrece
     */
    private static FormatoExportacion exigir(String valor, Set<FormatoExportacion> soportados, String que) {
        FormatoExportacion f = FormatoExportacion.de(valor);
        if (!soportados.contains(f)) {
            List<String> validos = new ArrayList<>();
            soportados.forEach(s -> validos.add(s.extension()));
            throw new FormatoNoSoportadoException(que + " solo se exporta en " + String.join(", ", validos));
        }
        return f;
    }

    /**
     * Rechaza las exportaciones demasiado grandes.
     *
     * @param total registros que traería el archivo
     * @throws ParametroInvalidoException si supera {@link #MAX_FILAS}
     */
    private static void exigirTope(long total) {
        if (total > MAX_FILAS) {
            throw new ParametroInvalidoException("La exportación tiene " + total + " filas y el máximo es " + MAX_FILAS
                    + ": usa los filtros para reducirla");
        }
    }

    /**
     * Nombre del archivo con fecha y hora.
     *
     * @param base    prefijo
     * @param formato formato
     * @return p. ej. {@code solicitudes-20261028-1530.xlsx}
     */
    private static String nombre(String base, FormatoExportacion formato) {
        return base + "-" + LocalDateTime.now().format(SELLO) + "." + formato.extension();
    }

    /**
     * Datos de la empresa del usuario para el encabezado.
     *
     * @return empresa
     */
    private EmpresaResponse empresa() {
        return empresaService.obtener();
    }

    /**
     * Usuario de la sesión.
     *
     * @return usuario autenticado
     */
    private static UsuarioAutenticado usuario() {
        return SesionActual.usuario();
    }
}
