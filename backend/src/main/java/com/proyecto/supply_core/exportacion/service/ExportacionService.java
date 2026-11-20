package com.proyecto.supply_core.exportacion.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import com.proyecto.supply_core.auditoria.enums.AccionAuditoria;
import com.proyecto.supply_core.auditoria.enums.EntidadAuditada;
import com.proyecto.supply_core.auditoria.service.AuditoriaService;
import com.proyecto.supply_core.cliente.dto.ClienteResponse;
import com.proyecto.supply_core.cliente.service.ClienteService;
import com.proyecto.supply_core.common.dto.PageResponse;
import com.proyecto.supply_core.common.exception.FormatoNoSoportadoException;
import com.proyecto.supply_core.common.exception.NegocioException;
import com.proyecto.supply_core.common.exception.NoEncontradoException;
import com.proyecto.supply_core.common.exception.ParametroInvalidoException;
import com.proyecto.supply_core.compra.orden.dto.OrdenResponse;
import com.proyecto.supply_core.compra.orden.service.OrdenCompraService;
import com.proyecto.supply_core.compra.solicitud.dto.SolicitudResumenResponse;
import com.proyecto.supply_core.compra.solicitud.enums.EstadoSolicitud;
import com.proyecto.supply_core.compra.solicitud.service.SolicitudService;
import com.proyecto.supply_core.empresa.dto.CentroCostoResponse;
import com.proyecto.supply_core.empresa.dto.EmpresaResponse;
import com.proyecto.supply_core.empresa.service.CentroCostoService;
import com.proyecto.supply_core.empresa.service.EmpresaService;
import com.proyecto.supply_core.exportacion.dto.ArchivoExportado;
import com.proyecto.supply_core.exportacion.enums.DestinoExportacion;
import com.proyecto.supply_core.exportacion.enums.FormatoExportacion;
import com.proyecto.supply_core.exportacion.generador.Etiquetas;
import com.proyecto.supply_core.exportacion.generador.ExcelGenerador;
import com.proyecto.supply_core.exportacion.generador.ExcelGenerador.Cantidad;
import com.proyecto.supply_core.exportacion.generador.OrdenPdfGenerador;
import com.proyecto.supply_core.exportacion.generador.TablaPdfGenerador;
import com.proyecto.supply_core.inventario.dto.MovimientoFiltro;
import com.proyecto.supply_core.inventario.dto.MovimientoResponse;
import com.proyecto.supply_core.inventario.dto.StockResponse;
import com.proyecto.supply_core.inventario.service.MovimientoInventarioService;
import com.proyecto.supply_core.notificacion.email.Adjunto;
import com.proyecto.supply_core.notificacion.email.EmailClient;
import com.proyecto.supply_core.proveedor.dto.ProveedorResponse;
import com.proyecto.supply_core.proveedor.service.ProveedorService;
import com.proyecto.supply_core.reporte.dto.CajaReporte;
import com.proyecto.supply_core.reporte.dto.VentaReporte;
import com.proyecto.supply_core.reporte.enums.AgrupacionVenta;
import com.proyecto.supply_core.reporte.service.CajaReporteService;
import com.proyecto.supply_core.reporte.service.VentaReporteService;
import com.proyecto.supply_core.security.context.SesionActual;
import com.proyecto.supply_core.security.context.UsuarioAutenticado;
import com.proyecto.supply_core.usuario.service.UsuarioService;

/**
 * Exporta información a Excel o PDF y la entrega como descarga o por correo.
 * <p>No consulta la base de datos por su cuenta: usa los mismos Services que las pantallas, así que cada rol exporta
 * solo lo que ya puede ver (p. ej. el solicitante, solo sus solicitudes) y cada empresa solo sus datos. Tampoco
 * calcula montos: escribe los que esos Services devuelven. Cada exportación queda en la auditoría.</p>
 * <p>Los archivos son para personas: muestran nombres en lugar de ids y estados legibles ({@link Etiquetas}).</p>
 */
@Service
public class ExportacionService {

    /** Tope de filas por archivo; por encima hay que filtrar. */
    public static final int MAX_FILAS = 10_000;

    private static final DateTimeFormatter SELLO = DateTimeFormatter.ofPattern("yyyyMMdd-HHmm");
    private static final Set<FormatoExportacion> SOLO_EXCEL = Set.of(FormatoExportacion.XLSX);
    private static final Set<FormatoExportacion> SOLO_PDF = Set.of(FormatoExportacion.PDF);
    private static final Set<FormatoExportacion> EXCEL_Y_PDF = Set.of(FormatoExportacion.XLSX, FormatoExportacion.PDF);

    private final MovimientoInventarioService movimientoService;
    private final SolicitudService solicitudService;
    private final OrdenCompraService ordenService;
    private final EmpresaService empresaService;
    private final UsuarioService usuarioService;
    private final CentroCostoService centroCostoService;
    private final ProveedorService proveedorService;
    private final ExcelGenerador excel;
    private final OrdenPdfGenerador ordenPdf;
    private final AuditoriaService auditoriaService;
    private final EmailClient emailClient;
    private final ClienteService clienteService;
    private final VentaReporteService ventaReporte;
    private final CajaReporteService cajaReporte;
    private final TablaPdfGenerador tablaPdf;

    /**
     * @param movimientoService  existencias y kardex
     * @param solicitudService   solicitudes de compra (aplica quién ve cuáles)
     * @param ordenService       órdenes de compra
     * @param empresaService     datos de la empresa para el encabezado
     * @param usuarioService     nombres de los usuarios (solicitante, quien hizo el movimiento)
     * @param centroCostoService nombres de los centros de costo
     * @param proveedorService   NIT y contacto del proveedor de la orden
     * @param excel              generador de libros de Excel
     * @param ordenPdf           generador del PDF de la orden
     * @param auditoriaService   registro de cada exportación
     * @param emailClient        envío del adjunto
     * @param clienteService     clientes (spec 002)
     * @param ventaReporte       reporte de ventas netas (spec 009)
     * @param cajaReporte        reporte de caja (spec 009)
     * @param tablaPdf           generador del PDF de los reportes
     */
    public ExportacionService(MovimientoInventarioService movimientoService, SolicitudService solicitudService,
            OrdenCompraService ordenService, EmpresaService empresaService, UsuarioService usuarioService,
            CentroCostoService centroCostoService, ProveedorService proveedorService, ExcelGenerador excel,
            OrdenPdfGenerador ordenPdf, AuditoriaService auditoriaService, EmailClient emailClient,
            ClienteService clienteService, VentaReporteService ventaReporte, CajaReporteService cajaReporte,
            TablaPdfGenerador tablaPdf) {
        this.clienteService = clienteService;
        this.ventaReporte = ventaReporte;
        this.cajaReporte = cajaReporte;
        this.tablaPdf = tablaPdf;
        this.movimientoService = movimientoService;
        this.solicitudService = solicitudService;
        this.ordenService = ordenService;
        this.empresaService = empresaService;
        this.usuarioService = usuarioService;
        this.centroCostoService = centroCostoService;
        this.proveedorService = proveedorService;
        this.excel = excel;
        this.ordenPdf = ordenPdf;
        this.auditoriaService = auditoriaService;
        this.emailClient = emailClient;
    }

    /**
     * Clientes de la empresa en Excel (spec 002). Solo para el dueño y auditoría: son datos personales.
     *
     * @param formato {@code xlsx}
     * @param activo  filtro por estado ({@code null} = todos)
     * @param destino descarga o correo
     * @return archivo generado
     * @throws FormatoNoSoportadoException si el formato no es xlsx
     * @throws ParametroInvalidoException  si supera {@link #MAX_FILAS}
     */
    public ArchivoExportado clientes(String formato, Boolean activo, DestinoExportacion destino) {
        FormatoExportacion f = exigir(formato, SOLO_EXCEL, "Los clientes");
        List<ClienteResponse> clientes = clienteService.todos(activo);
        exigirTope(clientes.size());
        List<List<Object>> filas = clientes.stream().<List<Object>>map(c -> Arrays.asList(c.tipoDocumento().name(),
                c.numeroDocumento(), c.nombre(), c.telefono(), c.correo(), c.direccion(),
                c.listaPrecio() == null ? "General" : c.listaPrecio(), c.cupoCredito(), c.plazoDias(),
                c.aceptaDatosEn(), c.activo() ? "Sí" : "No")).toList();
        byte[] bytes = excel.generar("Clientes", empresa(), usuario().nombre(),
                List.of("Tipo", "Documento", "Nombre", "Teléfono", "Correo", "Dirección", "Lista de precios",
                        "Cupo de crédito", "Plazo (días)", "Autorizó datos", "Activo"), filas);
        ArchivoExportado archivo = new ArchivoExportado(nombre("clientes", f), f, bytes, filas.size());
        return entregar(archivo, destino, null, "CLIENTES; activo=" + activo);
    }

    /**
     * Reporte de ventas netas en Excel o PDF (spec 009), con los mismos datos y el mismo alcance que la pantalla: el
     * cajero y el vendedor exportan solo lo suyo.
     *
     * @param formato {@code xlsx} o {@code pdf}
     * @param desde   primer día (vacío = primer día del mes)
     * @param hasta   último día (vacío = hoy)
     * @param agrupar agrupación (vacío = por día)
     * @param destino descarga o correo
     * @return archivo generado
     * @throws FormatoNoSoportadoException si el formato no es xlsx ni pdf
     */
    public ArchivoExportado ventas(String formato, LocalDate desde, LocalDate hasta, AgrupacionVenta agrupar,
            DestinoExportacion destino) {
        FormatoExportacion f = exigir(formato, EXCEL_Y_PDF, "El reporte de ventas");
        VentaReporte r = ventaReporte.reporte(desde, hasta, agrupar);
        boolean porMedio = r.agrupar() == AgrupacionVenta.MEDIO_PAGO;
        List<String> columnas = porMedio
                ? List.of("Medio de pago", "Ventas", "Cobrado", "Devuelto", "Neto")
                : List.of(titulo(r.agrupar()), "Ventas", "Unidades", "Bruto", "Devoluciones", "Neto", "Base sin IVA",
                        "Costo", "Margen", "Margen %");
        List<List<Object>> filas = new ArrayList<>(r.filas().stream().<List<Object>>map(x -> porMedio
                ? Arrays.asList(x.nombre(), x.ventas(), x.bruto(), x.devoluciones(), x.neto())
                : Arrays.asList(x.nombre(), x.ventas(), Cantidad.de(x.unidades()), x.bruto(), x.devoluciones(), x.neto(),
                        x.baseNeta(), x.costo(), x.margen(), x.margenPct())).toList());
        VentaReporte.Totales tt = r.totales();
        filas.add(porMedio ? Arrays.asList("Total cobrado (con redondeo)", tt.ventas(), null, null, tt.neto())
                : Arrays.asList("Total (redondeo " + tt.redondeo().toPlainString() + ")", tt.ventas(), null, tt.bruto(),
                        tt.devoluciones(), tt.neto(), tt.baseNeta(), tt.costo(), tt.margen(), tt.margenPct()));
        String periodo = "Ventas netas del " + r.desde() + " al " + r.hasta() + (r.soloPropio() ? " (solo las suyas)" : "")
                + ". Ticket promedio: " + (tt.ticketPromedio() == null ? "sin ventas" : tt.ticketPromedio().toPlainString());
        byte[] bytes = f == FormatoExportacion.PDF
                ? tablaPdf.generar("Reporte de ventas", periodo, empresa(), usuario().nombre(),
                        List.of(new TablaPdfGenerador.Seccion("Por " + titulo(r.agrupar()).toLowerCase(), columnas, filas)))
                : excel.generar("Reporte de ventas " + r.desde() + " a " + r.hasta(), empresa(), usuario().nombre(),
                        columnas, filas);
        ArchivoExportado archivo = new ArchivoExportado(nombre("reporte-ventas", f), f, bytes, filas.size());
        return entregar(archivo, destino, null, "REPORTE_VENTAS; desde=" + r.desde() + "; hasta=" + r.hasta()
                + "; agrupar=" + r.agrupar());
    }

    /**
     * Reporte de caja en Excel (la lista de turnos) o PDF (todas las secciones), spec 009.
     *
     * @param formato {@code xlsx} o {@code pdf}
     * @param desde   primer día (vacío = primer día del mes)
     * @param hasta   último día (vacío = hoy)
     * @param destino descarga o correo
     * @return archivo generado
     * @throws FormatoNoSoportadoException si el formato no es xlsx ni pdf
     */
    public ArchivoExportado caja(String formato, LocalDate desde, LocalDate hasta, DestinoExportacion destino) {
        FormatoExportacion f = exigir(formato, EXCEL_Y_PDF, "El reporte de caja");
        CajaReporte r = cajaReporte.reporte(desde, hasta);
        List<String> colTurnos = List.of("Turno", "Caja", "Cajero", "Estado", "Abierto", "Cerrado", "Esperado efectivo",
                "Contado efectivo", "Diferencia", "Por aprobar", "Aprobó");
        List<List<Object>> turnos = r.turnos().stream().<List<Object>>map(x -> Arrays.asList(x.id(), x.caja(), x.cajero(),
                x.estado(), x.abiertoEn(), x.cerradoEn(), x.esperadoEfectivo(), x.contadoEfectivo(), x.diferencia(),
                x.porAprobar() ? "Sí" : "No", x.aprobadoPor())).toList();
        byte[] bytes;
        if (f == FormatoExportacion.PDF) {
            List<TablaPdfGenerador.Seccion> secciones = List.of(
                    new TablaPdfGenerador.Seccion("Cierres por turno", colTurnos, turnos),
                    new TablaPdfGenerador.Seccion("Diferencias por cajero",
                            List.of("Cajero", "Turnos", "Cerrados", "Faltantes", "Sobrantes", "Diferencia", "Por aprobar"),
                            r.porCajero().stream().<List<Object>>map(x -> Arrays.asList(x.cajero(), x.turnos(), x.cerrados(),
                                    x.faltantes(), x.sobrantes(), x.diferencia(), x.porAprobar())).toList()),
                    new TablaPdfGenerador.Seccion("Retiros y gastos",
                            List.of("Tipo", "Caja", "Medio", "Monto", "Motivo", "Registró", "Autorizó", "Fecha"),
                            r.movimientos().stream().<List<Object>>map(x -> Arrays.asList(x.tipo(), x.caja(), x.medio(),
                                    x.monto(), x.motivo(), x.registradoPor(), x.autorizadoPor(), x.creadoEn())).toList()),
                    new TablaPdfGenerador.Seccion("Anulaciones",
                            List.of("Venta", "Total", "Motivo", "Anuló", "Autorizó", "Fecha"),
                            r.anulaciones().stream().<List<Object>>map(x -> Arrays.asList(x.consecutivo(), x.total(),
                                    x.motivo(), x.cajero(), x.autorizadoPor(), x.anuladaEn())).toList()),
                    new TablaPdfGenerador.Seccion("Descuentos y precios autorizados",
                            List.of("Venta", "Producto", "Tipo", "Descuento %", "Descuento", "Cajero", "Autorizó", "Fecha"),
                            r.autorizados().stream().<List<Object>>map(x -> Arrays.asList(x.consecutivo(), x.producto(),
                                    x.tipo(), x.descuentoPct(), x.descuentoValor(), x.cajero(), x.autorizadoPor(),
                                    x.confirmadaEn())).toList()));
            CajaReporte.Totales tt = r.totales();
            String periodo = "Del " + r.desde() + " al " + r.hasta() + ". Diferencia " + tt.diferencia().toPlainString()
                    + " (faltantes " + tt.faltantes().toPlainString() + ", sobrantes " + tt.sobrantes().toPlainString()
                    + "); retiros " + tt.retiros().toPlainString() + "; gastos " + tt.gastos().toPlainString()
                    + "; anulado " + tt.anulaciones().toPlainString() + "; descuentos " + tt.descuentos().toPlainString() + ".";
            bytes = tablaPdf.generar("Reporte de caja", periodo, empresa(), usuario().nombre(), secciones);
        } else {
            bytes = excel.generar("Cierres de caja " + r.desde() + " a " + r.hasta(), empresa(), usuario().nombre(),
                    colTurnos, turnos);
        }
        ArchivoExportado archivo = new ArchivoExportado(nombre("reporte-caja", f), f, bytes, turnos.size());
        return entregar(archivo, destino, null, "REPORTE_CAJA; desde=" + r.desde() + "; hasta=" + r.hasta());
    }

    /**
     * @param a agrupación
     * @return encabezado de la primera columna
     */
    private static String titulo(AgrupacionVenta a) {
        return switch (a) {
            case DIA -> "Día";
            case SEMANA -> "Semana";
            case MES -> "Mes";
            case PRODUCTO -> "Producto";
            case CATEGORIA -> "Categoría";
            case CLIENTE -> "Cliente";
            case VENDEDOR -> "Vendedor";
            case CAJERO -> "Cajero";
            case CAJA -> "Caja";
            case MEDIO_PAGO -> "Medio de pago";
        };
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
                s.productoNombre(), s.unidad(), s.bodegaNombre(), Cantidad.de(s.cantidad()))).toList();
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
        Map<Long, String> usuarios = nombresUsuarios(
                pagina.contenido().stream().map(MovimientoResponse::usuarioId).toList());
        List<List<Object>> filas = pagina.contenido().stream().<List<Object>>map(m -> Arrays.asList(m.creadoEn(),
                Etiquetas.de(m.tipo()), m.bodegaOrigenNombre(), m.bodegaDestinoNombre(), Cantidad.de(m.cantidad()),
                Cantidad.de(m.stockResultante()), Cantidad.de(m.stockResultanteDestino()), m.motivo(),
                Etiquetas.referencia(m.referenciaTipo(), m.referenciaId()), usuarios.get(m.usuarioId())))
                .toList();
        String titulo = pagina.contenido().isEmpty() ? "Kardex producto " + filtro.productoId()
                : "Kardex " + pagina.contenido().getFirst().productoCodigo() + " - "
                        + pagina.contenido().getFirst().productoNombre();
        byte[] bytes = excel.generar(titulo, empresa(), usuario().nombre(),
                List.of("Fecha", "Tipo", "Bodega origen", "Bodega destino", "Cantidad", "Stock resultante",
                        "Stock resultante destino", "Motivo", "Referencia", "Usuario"),
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
        Map<Long, String> centros = nombresCentrosCosto();
        Map<Long, String> usuarios = nombresUsuarios(
                pagina.contenido().stream().map(SolicitudResumenResponse::usuarioSolicitanteId).toList());
        List<List<Object>> filas = pagina.contenido().stream().<List<Object>>map(s -> Arrays.asList(s.consecutivo(),
                Etiquetas.de(s.estado()), centros.getOrDefault(s.centroCostoId(), sinNombre(s.centroCostoId())),
                usuarios.get(s.usuarioSolicitanteId()), s.fechaRequerida(), s.montoEstimado(), s.montoIncompleto(),
                s.enviadoEn(), s.creadoEn())).toList();
        byte[] bytes = excel.generar("Solicitudes de compra", empresa(), usuario().nombre(),
                List.of("Consecutivo", "Estado", "Centro de costo", "Solicitante", "Fecha requerida",
                        "Monto estimado", "Monto incompleto", "Enviada", "Creada"),
                filas);
        ArchivoExportado archivo = new ArchivoExportado(nombre("solicitudes", f), f, bytes, filas.size());
        return entregar(archivo, destino, null, "SOLICITUDES; estado=" + estado + "; centroCostoId=" + centroCostoId);
    }

    /**
     * Orden de compra en PDF con el encabezado de la empresa y el NIT y contacto del proveedor.
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
        ProveedorResponse proveedor = proveedorService.obtener(orden.proveedorId());
        byte[] bytes = ordenPdf.generar(empresa(), orden, proveedor, usuario().nombre());
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
     * Nombre de cada usuario, consultado una vez por id con el mismo Service de la pantalla de usuarios (solo ve los
     * de la empresa de la sesión). Uno que ya no exista sale como {@code #id}.
     *
     * @param ids ids de usuario (puede repetir y traer {@code null})
     * @return nombre por id
     */
    private Map<Long, String> nombresUsuarios(Collection<Long> ids) {
        Map<Long, String> nombres = new HashMap<>();
        for (Long id : ids.stream().filter(Objects::nonNull).collect(Collectors.toSet())) {
            String nombre;
            try {
                nombre = usuarioService.obtener(id).nombreCompleto();
            } catch (NoEncontradoException e) {
                nombre = sinNombre(id);
            }
            nombres.put(id, nombre);
        }
        return nombres;
    }

    /**
     * Código y nombre de cada centro de costo de la empresa, p. ej. "CC-01 - Administración".
     *
     * @return texto por id
     */
    private Map<Long, String> nombresCentrosCosto() {
        return centroCostoService.listar(null).stream()
                .collect(Collectors.toMap(CentroCostoResponse::id, c -> c.codigo() + " - " + c.nombre()));
    }

    /**
     * Texto para un id cuyo nombre no se encontró.
     *
     * @param id id ({@code null} = vacío)
     * @return p. ej. {@code #7}
     */
    private static String sinNombre(Long id) {
        return id == null ? null : "#" + id;
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
