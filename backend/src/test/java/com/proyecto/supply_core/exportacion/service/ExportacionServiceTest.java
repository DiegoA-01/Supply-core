package com.proyecto.supply_core.exportacion.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.proyecto.supply_core.auditoria.enums.AccionAuditoria;
import com.proyecto.supply_core.auditoria.enums.EntidadAuditada;
import com.proyecto.supply_core.auditoria.service.AuditoriaService;
import com.proyecto.supply_core.common.dto.PageResponse;
import com.proyecto.supply_core.common.exception.FormatoNoSoportadoException;
import com.proyecto.supply_core.common.exception.NegocioException;
import com.proyecto.supply_core.common.exception.NoEncontradoException;
import com.proyecto.supply_core.common.exception.ParametroInvalidoException;
import com.proyecto.supply_core.compra.orden.dto.OrdenResponse;
import com.proyecto.supply_core.compra.orden.enums.EstadoOrden;
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
import com.proyecto.supply_core.inventario.enums.TipoMovimiento;
import com.proyecto.supply_core.inventario.service.MovimientoInventarioService;
import com.proyecto.supply_core.notificacion.email.Adjunto;
import com.proyecto.supply_core.notificacion.email.EmailClient;
import com.proyecto.supply_core.security.context.SesionActual;
import com.proyecto.supply_core.security.context.UsuarioAutenticado;

class ExportacionServiceTest {

    private final MovimientoInventarioService movimientos = mock(MovimientoInventarioService.class);
    private final SolicitudService solicitudes = mock(SolicitudService.class);
    private final OrdenCompraService ordenes = mock(OrdenCompraService.class);
    private final EmpresaService empresas = mock(EmpresaService.class);
    private final AuditoriaService auditoria = mock(AuditoriaService.class);
    private final EmailClient email = mock(EmailClient.class);
    private final ExportacionService service = new ExportacionService(movimientos, solicitudes, ordenes, empresas,
            new ExcelGenerador(), new OrdenPdfGenerador(), auditoria, email);

    @BeforeEach
    void sesion() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(SesionActual.ATRIBUTO,
                new UsuarioAutenticado(3L, "ana@ferreteria.com", "Ana", Set.of("COMPRADOR"), null, 1L));
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        when(empresas.obtener()).thenReturn(new EmpresaResponse(1L, "Ferretería El Tornillo", "900123456-7",
                "Calle 10 # 5-20", "COP", null, null, null, 24, null));
    }

    @AfterEach
    void limpiar() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void stockSaleEnExcelConEncabezadoYFilasYQuedaAuditado() throws Exception {
        when(movimientos.existencias(null, 4L)).thenReturn(List.of(
                new StockResponse(1L, "CAB-12", "Cable 12 AWG", "M", 4L, "Principal", new BigDecimal("150.50")),
                new StockResponse(2L, "=CMD()", "Producto con fórmula", "UND", 4L, "Principal", BigDecimal.ONE)));

        ArchivoExportado a = service.stock("xlsx", null, 4L, DestinoExportacion.DESCARGA);

        assertEquals(FormatoExportacion.XLSX, a.formato());
        assertEquals(2, a.filas());
        assertTrue(a.nombre().startsWith("inventario-stock-") && a.nombre().endsWith(".xlsx"));
        try (XSSFWorkbook libro = new XSSFWorkbook(new ByteArrayInputStream(a.contenido()))) {
            Sheet hoja = libro.getSheetAt(0);
            assertTrue(hoja.getRow(0).getCell(0).getStringCellValue().contains("Ferretería El Tornillo"));
            assertEquals("Código", hoja.getRow(4).getCell(0).getStringCellValue());
            assertEquals("CAB-12", hoja.getRow(5).getCell(0).getStringCellValue());
            assertEquals(150.5, hoja.getRow(5).getCell(4).getNumericCellValue());
            // un texto que parece fórmula se guarda como texto, no se ejecuta
            assertEquals("=CMD()", hoja.getRow(6).getCell(0).getStringCellValue());
            // la columna alcanza para el dato más largo, no solo para su título
            assertTrue(hoja.getColumnWidth(1) >= "Producto con fórmula".length() * 256);
        }
        verify(auditoria).exito(eq(AccionAuditoria.EXPORTAR_ARCHIVO), eq(EntidadAuditada.EXPORTACION), eq(4L),
                contains("formato=XLSX; filas=2"));
        verifyNoInteractions(email);
    }

    @Test
    void formatoQueElDatoNoOfreceODesconocidoSeRechazaSinConsultarNiAuditar() {
        assertThrows(FormatoNoSoportadoException.class, () -> service.stock("pdf", null, null, DestinoExportacion.DESCARGA));
        assertThrows(FormatoNoSoportadoException.class, () -> service.stock("docx", null, null, DestinoExportacion.DESCARGA));
        assertThrows(FormatoNoSoportadoException.class, () -> service.orden(null, 1L, DestinoExportacion.DESCARGA));
        assertThrows(FormatoNoSoportadoException.class, () -> service.orden("xlsx", 1L, DestinoExportacion.DESCARGA));
        verifyNoInteractions(movimientos, ordenes, auditoria);
    }

    @Test
    void elFormatoNoDistingueMayusculas() {
        when(movimientos.existencias(null, null)).thenReturn(List.of());
        assertEquals(FormatoExportacion.XLSX, service.stock("XLSX", null, null, DestinoExportacion.DESCARGA).formato());
    }

    @Test
    void kardexExigeProductoYVaDelMasAntiguoAlMasReciente() throws Exception {
        assertThrows(ParametroInvalidoException.class,
                () -> service.kardex("xlsx", new MovimientoFiltro(null, null, null, null, null), DestinoExportacion.DESCARGA));
        MovimientoFiltro filtro = new MovimientoFiltro(7L, null, null, null, null);
        MovimientoResponse m = new MovimientoResponse(1L, TipoMovimiento.ENTRADA_COMPRA, 7L, "CAB-12", "Cable", null, null,
                2L, "Principal", BigDecimal.TEN, BigDecimal.TEN, null, "Recepción", "RECEPCION", 5L, 3L,
                LocalDateTime.of(2026, 10, 1, 8, 0));
        ArgumentCaptor<Pageable> pagina = ArgumentCaptor.forClass(Pageable.class);
        when(movimientos.buscar(eq(filtro), pagina.capture())).thenReturn(new PageResponse<>(List.of(m), 0, 1, 1, 1));

        ArchivoExportado a = service.kardex("xlsx", filtro, DestinoExportacion.DESCARGA);

        assertEquals(PageRequest.of(0, ExportacionService.MAX_FILAS, Sort.by(Sort.Direction.ASC, "creadoEn")),
                pagina.getValue());
        try (XSSFWorkbook libro = new XSSFWorkbook(new ByteArrayInputStream(a.contenido()))) {
            Sheet hoja = libro.getSheetAt(0);
            assertEquals("Kardex CAB-12 - Cable", hoja.getRow(1).getCell(0).getStringCellValue());
            assertEquals("ENTRADA_COMPRA", hoja.getRow(5).getCell(1).getStringCellValue());
            assertEquals("RECEPCION #5", hoja.getRow(5).getCell(8).getStringCellValue());
            // una fecha en una columna angosta se vería como ####
            assertTrue(hoja.getColumnWidth(0) >= "yyyy-mm-dd hh:mm".length() * 256);
        }
        verify(auditoria).exito(eq(AccionAuditoria.EXPORTAR_ARCHIVO), eq(EntidadAuditada.EXPORTACION), eq(7L), anyString());
    }

    @Test
    void solicitudesUsanLaBusquedaQueYaLimitaLoQueVeCadaRolYRespetanElTope() throws Exception {
        SolicitudResumenResponse s = new SolicitudResumenResponse(9L, "SOL-000009", EstadoSolicitud.APROBADA, 2L, 3L,
                LocalDate.of(2026, 11, 1), new BigDecimal("1200000.00"), false, LocalDateTime.of(2026, 10, 20, 9, 0),
                LocalDateTime.of(2026, 10, 19, 9, 0));
        when(solicitudes.buscar(isNull(), eq(EstadoSolicitud.APROBADA), isNull(), isNull(), any()))
                .thenReturn(new PageResponse<>(List.of(s), 0, 1, 1, 1));

        ArchivoExportado a = service.solicitudes("xlsx", null, EstadoSolicitud.APROBADA, null, null,
                DestinoExportacion.DESCARGA);
        try (XSSFWorkbook libro = new XSSFWorkbook(new ByteArrayInputStream(a.contenido()))) {
            Sheet hoja = libro.getSheetAt(0);
            assertEquals("SOL-000009", hoja.getRow(5).getCell(0).getStringCellValue());
            assertEquals("No", hoja.getRow(5).getCell(6).getStringCellValue());
        }

        when(solicitudes.buscar(isNull(), isNull(), isNull(), isNull(), any()))
                .thenReturn(new PageResponse<>(List.of(), 0, 0, ExportacionService.MAX_FILAS + 1L, 2));
        assertThrows(ParametroInvalidoException.class,
                () -> service.solicitudes("xlsx", null, null, null, null, DestinoExportacion.DESCARGA));
    }

    @Test
    void ordenSaleEnPdfConEncabezadoDeLaEmpresaYLosTotalesGuardados() throws Exception {
        when(ordenes.obtener(5L)).thenReturn(orden());

        ArchivoExportado a = service.orden("pdf", 5L, DestinoExportacion.DESCARGA);

        assertEquals("OC-000005-v2.pdf", a.nombre());
        assertEquals(1, a.filas());
        String texto;
        try (PDDocument pdf = Loader.loadPDF(a.contenido())) {
            texto = new PDFTextStripper().getText(pdf);
        }
        assertTrue(texto.contains("Ferretería El Tornillo"));
        assertTrue(texto.contains("NIT 900123456-7"));
        assertTrue(texto.contains("OC-000005"));
        assertTrue(texto.contains("Eléctricos del Norte"));
        assertTrue(texto.contains("Cambio de cantidad"));
        assertTrue(texto.contains("1.190.000,00 COP"), texto);
        verify(auditoria).exito(eq(AccionAuditoria.EXPORTAR_ARCHIVO), eq(EntidadAuditada.EXPORTACION), eq(5L),
                contains("OC-000005 v2"));
    }

    @Test
    void ordenDeOtraEmpresaONoVisibleNoSeExportaNiSeAudita() {
        when(ordenes.obtener(99L)).thenThrow(new NoEncontradoException("Orden", 99L));
        assertThrows(NoEncontradoException.class, () -> service.orden("pdf", 99L, DestinoExportacion.DESCARGA));
        verifyNoInteractions(auditoria, email);
    }

    @Test
    void elCorreoVaSoloAlUsuarioDeLaSesionConElAdjuntoYQuedaAuditado() {
        when(ordenes.obtener(5L)).thenReturn(orden());

        ArchivoExportado a = service.orden("pdf", 5L, DestinoExportacion.CORREO);

        ArgumentCaptor<Adjunto> adjunto = ArgumentCaptor.forClass(Adjunto.class);
        verify(email).enviarConAdjunto(eq("ana@ferreteria.com"), contains("OC-000005-v2.pdf"), anyString(),
                adjunto.capture());
        assertEquals("application/pdf", adjunto.getValue().contentType());
        assertEquals(a.contenido().length, adjunto.getValue().contenido().length);
        verify(auditoria).exito(eq(AccionAuditoria.EXPORTAR_CORREO), eq(EntidadAuditada.EXPORTACION), eq(5L),
                contains("para=ana@ferreteria.com"));
        verify(auditoria, never()).exito(eq(AccionAuditoria.EXPORTAR_ARCHIVO), any(), any(), any());
    }

    @Test
    void siElCorreoFallaQuedaElFalloEnAuditoriaYSeAvisa() {
        when(movimientos.existencias(null, null)).thenReturn(List.of());
        doThrow(new IllegalStateException("SMTP caído")).when(email).enviarConAdjunto(any(), any(), any(), any());

        NegocioException ex = assertThrows(NegocioException.class,
                () -> service.stock("xlsx", null, null, DestinoExportacion.CORREO));

        assertEquals("CORREO_NO_ENVIADO", ex.getCodigo());
        verify(auditoria).fallo(eq(3L), eq(AccionAuditoria.EXPORTAR_CORREO), eq(EntidadAuditada.EXPORTACION), isNull(),
                contains("IllegalStateException"));
        verify(auditoria, never()).exito(any(), any(), any(), any());
    }

    @Test
    void unTextoLarguisimoNoEnsanchaLaColumnaSinLimite() throws Exception {
        when(movimientos.existencias(null, null)).thenReturn(List.of(
                new StockResponse(1L, "C-1", "x".repeat(300), "M", 4L, "Principal", BigDecimal.ONE)));

        ArchivoExportado a = service.stock("xlsx", null, null, DestinoExportacion.DESCARGA);

        try (XSSFWorkbook libro = new XSSFWorkbook(new ByteArrayInputStream(a.contenido()))) {
            assertEquals(ExcelGenerador.ANCHO_MAXIMO * 256, libro.getSheetAt(0).getColumnWidth(1));
        }
    }

    private static OrdenResponse orden() {
        return new OrdenResponse(5L, "OC-000005", 2, 4L, "Cambio de cantidad", EstadoOrden.EMITIDA, 8L, null, 6L, 11L,
                "Eléctricos del Norte", 2L, "Principal", "Calle 10 # 5-20", LocalDate.of(2026, 11, 5),
                new BigDecimal("1000000.00"), new BigDecimal("190000.00"), new BigDecimal("1190000.00"),
                LocalDateTime.of(2026, 10, 28, 10, 0),
                List.of(new OrdenResponse.Linea(1L, 7L, "CAB-12", "Cable 12 AWG", "M", new BigDecimal("100"),
                        new BigDecimal("10000.00"), new BigDecimal("19.00"), new BigDecimal("1000000.00"))),
                List.of());
    }
}
