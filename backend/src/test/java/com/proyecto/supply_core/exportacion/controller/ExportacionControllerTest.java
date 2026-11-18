package com.proyecto.supply_core.exportacion.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.proyecto.supply_core.common.exception.FormatoNoSoportadoException;
import com.proyecto.supply_core.common.exception.NoEncontradoException;
import com.proyecto.supply_core.common.web.WebTestBase;
import com.proyecto.supply_core.compra.solicitud.enums.EstadoSolicitud;
import com.proyecto.supply_core.exportacion.dto.ArchivoExportado;
import com.proyecto.supply_core.exportacion.enums.DestinoExportacion;
import com.proyecto.supply_core.exportacion.enums.FormatoExportacion;
import com.proyecto.supply_core.exportacion.service.ExportacionService;

@WebMvcTest(ExportacionController.class)
class ExportacionControllerTest extends WebTestBase {

    @MockitoBean
    private ExportacionService exportacionService;

    private static final byte[] BYTES = { 1, 2, 3 };

    @Test
    void elStockSeDescargaComoAdjuntoParaCualquierInterno() throws Exception {
        when(exportacionService.stock("xlsx", null, 4L, DestinoExportacion.DESCARGA))
                .thenReturn(new ArchivoExportado("inventario-stock-20261028-1530.xlsx", FormatoExportacion.XLSX, BYTES, 2));

        mvc.perform(get("/api/exportaciones/inventario/stock?formato=xlsx&bodegaId=4")
                        .header(HttpHeaders.AUTHORIZATION, bearer("ALMACENISTA")))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, FormatoExportacion.XLSX.contentType()))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        Matchers.allOf(Matchers.startsWith("attachment"),
                                Matchers.containsString("inventario-stock-20261028-1530.xlsx"))))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(content().bytes(BYTES));
    }

    @Test
    void elProveedorNoExportaNadaYSinTokenEs401() throws Exception {
        mvc.perform(get("/api/exportaciones/inventario/stock?formato=xlsx")
                        .header(HttpHeaders.AUTHORIZATION, bearerProveedor(40L)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/exportaciones/ordenes/5?formato=pdf")
                        .header(HttpHeaders.AUTHORIZATION, bearerProveedor(40L)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/exportaciones/solicitudes?formato=xlsx")).andExpect(status().isUnauthorized());
        verifyNoInteractions(exportacionService);
    }

    @Test
    void laOrdenEnPdfPideLosRolesDeOrdenes() throws Exception {
        when(exportacionService.orden("pdf", 5L, DestinoExportacion.DESCARGA))
                .thenReturn(new ArchivoExportado("OC-000005-v1.pdf", FormatoExportacion.PDF, BYTES, 1));

        mvc.perform(get("/api/exportaciones/ordenes/5?formato=pdf").header(HttpHeaders.AUTHORIZATION, bearer("AUDITOR")))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "application/pdf"));
        mvc.perform(get("/api/exportaciones/ordenes/5?formato=pdf")
                        .header(HttpHeaders.AUTHORIZATION, bearer("SOLICITANTE")))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/exportaciones/ordenes/5/correo?formato=pdf")
                        .header(HttpHeaders.AUTHORIZATION, bearer("SOLICITANTE")))
                .andExpect(status().isForbidden());
    }

    @Test
    void ordenAjenaEs404() throws Exception {
        when(exportacionService.orden("pdf", 77L, DestinoExportacion.DESCARGA))
                .thenThrow(new NoEncontradoException("Orden", 77L));

        mvc.perform(get("/api/exportaciones/ordenes/77?formato=pdf").header(HttpHeaders.AUTHORIZATION, bearer("COMPRADOR")))
                .andExpect(status().isNotFound());
    }

    @Test
    void formatoNoSoportadoEs400ConSuCodigo() throws Exception {
        when(exportacionService.stock("pdf", null, null, DestinoExportacion.DESCARGA))
                .thenThrow(new FormatoNoSoportadoException("El stock solo se exporta en xlsx"));

        mvc.perform(get("/api/exportaciones/inventario/stock?formato=pdf")
                        .header(HttpHeaders.AUTHORIZATION, bearer("COMPRADOR")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("FORMATO_NO_SOPORTADO"));
    }

    @Test
    void idsYFiltrosInvalidosSeRechazanSinLlamarAlServicio() throws Exception {
        mvc.perform(get("/api/exportaciones/ordenes/0?formato=pdf").header(HttpHeaders.AUTHORIZATION, bearer("COMPRADOR")))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/exportaciones/inventario/kardex?formato=xlsx&productoId=-1")
                        .header(HttpHeaders.AUTHORIZATION, bearer("COMPRADOR")))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/exportaciones/solicitudes?formato=xlsx&estado=INVENTADO")
                        .header(HttpHeaders.AUTHORIZATION, bearer("COMPRADOR")))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(exportacionService);
    }

    @Test
    void lasSolicitudesPasanLosFiltrosYElSolicitantePuedeExportar() throws Exception {
        when(exportacionService.solicitudes(eq("xlsx"), isNull(), eq(EstadoSolicitud.APROBADA), isNull(), eq(2L),
                eq(DestinoExportacion.DESCARGA)))
                .thenReturn(new ArchivoExportado("solicitudes.xlsx", FormatoExportacion.XLSX, BYTES, 1));

        mvc.perform(get("/api/exportaciones/solicitudes?formato=xlsx&estado=APROBADA&centroCostoId=2")
                        .header(HttpHeaders.AUTHORIZATION, bearer("SOLICITANTE")))
                .andExpect(status().isOk());
    }

    @Test
    void elCorreoRespondeAQuienSeEnvioYQueArchivo() throws Exception {
        when(exportacionService.kardex(eq("xlsx"), any(), eq(DestinoExportacion.CORREO)))
                .thenReturn(new ArchivoExportado("kardex-producto-7.xlsx", FormatoExportacion.XLSX, BYTES, 4));

        mvc.perform(post("/api/exportaciones/inventario/kardex/correo?formato=xlsx&productoId=7")
                        .header(HttpHeaders.AUTHORIZATION, bearer("ALMACENISTA")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.para").value("usuario@prueba.com"))
                .andExpect(jsonPath("$.archivo").value("kardex-producto-7.xlsx"))
                .andExpect(jsonPath("$.bytes").value(3));
        verify(exportacionService).kardex(eq("xlsx"), any(), eq(DestinoExportacion.CORREO));
    }
}
