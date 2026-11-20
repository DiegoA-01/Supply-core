package com.proyecto.supply_core.exportacion.controller;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.proyecto.supply_core.compra.solicitud.enums.EstadoSolicitud;
import com.proyecto.supply_core.exportacion.dto.ArchivoExportado;
import com.proyecto.supply_core.exportacion.dto.CorreoExportacionResponse;
import com.proyecto.supply_core.exportacion.enums.DestinoExportacion;
import com.proyecto.supply_core.exportacion.service.ExportacionService;
import com.proyecto.supply_core.inventario.dto.MovimientoFiltro;
import com.proyecto.supply_core.reporte.enums.AgrupacionVenta;
import com.proyecto.supply_core.security.annotation.RequiereModulo;
import com.proyecto.supply_core.security.annotation.RequiereRol;
import com.proyecto.supply_core.security.annotation.UsuarioInterno;
import com.proyecto.supply_core.security.context.Modulos;
import com.proyecto.supply_core.security.context.Roles;
import com.proyecto.supply_core.security.context.SesionActual;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;

/**
 * Exportación de información a Excel o PDF ({@code ?formato=xlsx|pdf}): cada ruta GET devuelve el archivo como
 * adjunto y su gemela {@code POST …/correo} lo envía al correo del usuario de la sesión.
 * <p>Cada ruta pide el mismo módulo y los mismos roles que la pantalla de donde salen los datos.</p>
 */
@RestController
@RequestMapping("/api/exportaciones")
@UsuarioInterno
public class ExportacionController {

    private final ExportacionService exportacionService;

    /**
     * @param exportacionService generación, entrega y auditoría de las exportaciones
     */
    public ExportacionController(ExportacionService exportacionService) {
        this.exportacionService = exportacionService;
    }

    /**
     * Descarga el reporte de ventas netas (spec 009) con los mismos datos de la pantalla: el cajero y el vendedor,
     * solo lo suyo.
     *
     * @param formato {@code xlsx} o {@code pdf}
     * @param desde   primer día (vacío = primer día del mes)
     * @param hasta   último día (vacío = hoy)
     * @param agrupar agrupación (vacío = por día)
     * @return archivo adjunto
     */
    @GetMapping("/reportes/ventas")
    @RequiereModulo(Modulos.VENTAS)
    @RequiereRol({ Roles.SUPERVISOR_VENTAS, Roles.ADMIN_SISTEMA, Roles.AUDITOR, Roles.CAJERO, Roles.VENDEDOR })
    public ResponseEntity<byte[]> reporteVentas(@RequestParam(required = false) String formato,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) AgrupacionVenta agrupar) {
        return descarga(exportacionService.ventas(formato, desde, hasta, agrupar, DestinoExportacion.DESCARGA));
    }

    /**
     * Descarga el reporte de caja (spec 009): Excel con los cierres o PDF con todas las secciones.
     *
     * @param formato {@code xlsx} o {@code pdf}
     * @param desde   primer día (vacío = primer día del mes)
     * @param hasta   último día (vacío = hoy)
     * @return archivo adjunto
     */
    @GetMapping("/reportes/caja")
    @RequiereModulo(Modulos.VENTAS)
    @RequiereRol({ Roles.SUPERVISOR_VENTAS, Roles.ADMIN_SISTEMA, Roles.AUDITOR })
    public ResponseEntity<byte[]> reporteCaja(@RequestParam(required = false) String formato,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return descarga(exportacionService.caja(formato, desde, hasta, DestinoExportacion.DESCARGA));
    }

    /**
     * Descarga los clientes de la empresa (spec 002). Solo el dueño y auditoría: son datos personales.
     *
     * @param formato {@code xlsx}
     * @param activo  filtro por estado (vacío = todos)
     * @return archivo adjunto
     */
    @GetMapping("/clientes")
    @RequiereModulo(Modulos.VENTAS)
    @RequiereRol({ Roles.ADMIN_SISTEMA, Roles.AUDITOR })
    public ResponseEntity<byte[]> clientes(@RequestParam(required = false) String formato,
            @RequestParam(required = false) Boolean activo) {
        return descarga(exportacionService.clientes(formato, activo, DestinoExportacion.DESCARGA));
    }

    /**
     * Descarga las existencias por producto y bodega (cualquier interno, como {@code /api/inventario/stock}).
     *
     * @param formato    {@code xlsx}
     * @param productoId filtro opcional
     * @param bodegaId   filtro opcional
     * @return archivo adjunto
     */
    @GetMapping("/inventario/stock")
    @RequiereModulo(Modulos.INVENTARIO)
    public ResponseEntity<byte[]> stock(@RequestParam(required = false) String formato,
            @RequestParam(required = false) @Positive(message = "debe ser un id válido") Long productoId,
            @RequestParam(required = false) @Positive(message = "debe ser un id válido") Long bodegaId) {
        return descarga(exportacionService.stock(formato, productoId, bodegaId, DestinoExportacion.DESCARGA));
    }

    /**
     * Envía por correo las existencias por producto y bodega.
     *
     * @param formato    {@code xlsx}
     * @param productoId filtro opcional
     * @param bodegaId   filtro opcional
     * @return a quién se envió y qué archivo
     */
    @PostMapping("/inventario/stock/correo")
    @RequiereModulo(Modulos.INVENTARIO)
    public CorreoExportacionResponse stockPorCorreo(@RequestParam(required = false) String formato,
            @RequestParam(required = false) @Positive(message = "debe ser un id válido") Long productoId,
            @RequestParam(required = false) @Positive(message = "debe ser un id válido") Long bodegaId) {
        return correo(exportacionService.stock(formato, productoId, bodegaId, DestinoExportacion.CORREO));
    }

    /**
     * Descarga el kardex de un producto (cualquier interno, como {@code /api/inventario/movimientos}).
     *
     * @param formato {@code xlsx}
     * @param filtro  producto (obligatorio), bodega, tipo y rango de fechas
     * @return archivo adjunto
     */
    @GetMapping("/inventario/kardex")
    @RequiereModulo(Modulos.INVENTARIO)
    public ResponseEntity<byte[]> kardex(@RequestParam(required = false) String formato,
            @Valid @ModelAttribute MovimientoFiltro filtro) {
        return descarga(exportacionService.kardex(formato, filtro, DestinoExportacion.DESCARGA));
    }

    /**
     * Envía por correo el kardex de un producto.
     *
     * @param formato {@code xlsx}
     * @param filtro  producto (obligatorio), bodega, tipo y rango de fechas
     * @return a quién se envió y qué archivo
     */
    @PostMapping("/inventario/kardex/correo")
    @RequiereModulo(Modulos.INVENTARIO)
    public CorreoExportacionResponse kardexPorCorreo(@RequestParam(required = false) String formato,
            @Valid @ModelAttribute MovimientoFiltro filtro) {
        return correo(exportacionService.kardex(formato, filtro, DestinoExportacion.CORREO));
    }

    /**
     * Descarga las solicitudes de compra; el solicitante solo obtiene las suyas.
     *
     * @param formato       {@code xlsx}
     * @param texto         coincidencia en el consecutivo
     * @param estado        estado
     * @param solicitanteId solicitante
     * @param centroCostoId centro de costo
     * @return archivo adjunto
     */
    @GetMapping("/solicitudes")
    @RequiereModulo(Modulos.COMPRAS)
    public ResponseEntity<byte[]> solicitudes(@RequestParam(required = false) String formato,
            @RequestParam(required = false) String texto,
            @RequestParam(required = false) EstadoSolicitud estado,
            @RequestParam(required = false) @Positive(message = "debe ser un id válido") Long solicitanteId,
            @RequestParam(required = false) @Positive(message = "debe ser un id válido") Long centroCostoId) {
        return descarga(exportacionService.solicitudes(formato, texto, estado, solicitanteId, centroCostoId,
                DestinoExportacion.DESCARGA));
    }

    /**
     * Envía por correo las solicitudes de compra; el solicitante solo obtiene las suyas.
     *
     * @param formato       {@code xlsx}
     * @param texto         coincidencia en el consecutivo
     * @param estado        estado
     * @param solicitanteId solicitante
     * @param centroCostoId centro de costo
     * @return a quién se envió y qué archivo
     */
    @PostMapping("/solicitudes/correo")
    @RequiereModulo(Modulos.COMPRAS)
    public CorreoExportacionResponse solicitudesPorCorreo(@RequestParam(required = false) String formato,
            @RequestParam(required = false) String texto,
            @RequestParam(required = false) EstadoSolicitud estado,
            @RequestParam(required = false) @Positive(message = "debe ser un id válido") Long solicitanteId,
            @RequestParam(required = false) @Positive(message = "debe ser un id válido") Long centroCostoId) {
        return correo(exportacionService.solicitudes(formato, texto, estado, solicitanteId, centroCostoId,
                DestinoExportacion.CORREO));
    }

    /**
     * Descarga el PDF de una orden de compra (mismos roles que {@code /api/ordenes}).
     *
     * @param id      orden
     * @param formato {@code pdf}
     * @return archivo adjunto
     */
    @GetMapping("/ordenes/{id}")
    @RequiereModulo(Modulos.ORDENES_PAGOS)
    @RequiereRol({ Roles.COMPRADOR, Roles.ADMIN_COMPRAS, Roles.ALMACENISTA, Roles.ADMINISTRATIVO, Roles.APROBADOR,
            Roles.AUDITOR })
    public ResponseEntity<byte[]> orden(@PathVariable @Positive(message = "debe ser un id válido") Long id,
            @RequestParam(required = false) String formato) {
        return descarga(exportacionService.orden(formato, id, DestinoExportacion.DESCARGA));
    }

    /**
     * Envía por correo el PDF de una orden de compra.
     *
     * @param id      orden
     * @param formato {@code pdf}
     * @return a quién se envió y qué archivo
     */
    @PostMapping("/ordenes/{id}/correo")
    @RequiereModulo(Modulos.ORDENES_PAGOS)
    @RequiereRol({ Roles.COMPRADOR, Roles.ADMIN_COMPRAS, Roles.ALMACENISTA, Roles.ADMINISTRATIVO, Roles.APROBADOR,
            Roles.AUDITOR })
    public CorreoExportacionResponse ordenPorCorreo(@PathVariable @Positive(message = "debe ser un id válido") Long id,
            @RequestParam(required = false) String formato) {
        return correo(exportacionService.orden(formato, id, DestinoExportacion.CORREO));
    }

    /**
     * Respuesta de descarga: adjunto con nombre, tipo MIME y {@code nosniff}.
     *
     * @param a archivo
     * @return 200 con los bytes
     */
    private static ResponseEntity<byte[]> descarga(ArchivoExportado a) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(a.formato().contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(a.nombre(), StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(a.contenido());
    }

    /**
     * Resumen del envío por correo.
     *
     * @param a archivo enviado
     * @return destinatario, nombre y tamaño
     */
    private static CorreoExportacionResponse correo(ArchivoExportado a) {
        return new CorreoExportacionResponse(SesionActual.usuario().correo(), a.nombre(), a.contenido().length);
    }
}
