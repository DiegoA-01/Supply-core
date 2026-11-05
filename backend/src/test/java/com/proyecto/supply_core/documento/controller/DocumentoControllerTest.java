package com.proyecto.supply_core.documento.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.proyecto.supply_core.common.exception.AccesoDenegadoException;
import com.proyecto.supply_core.common.exception.ParametroInvalidoException;
import com.proyecto.supply_core.common.web.WebTestBase;
import com.proyecto.supply_core.documento.dto.ArchivoDescarga;
import com.proyecto.supply_core.documento.dto.DocumentoResponse;
import com.proyecto.supply_core.documento.enums.EntidadDocumento;
import com.proyecto.supply_core.documento.service.DocumentoService;

@WebMvcTest(DocumentoController.class)
class DocumentoControllerTest extends WebTestBase {

    private static final byte[] PDF = "%PDF-1.7".getBytes();

    @MockitoBean
    private DocumentoService documentoService;

    @Test
    void subirMultipartDevuelve201SinRutaInterna() throws Exception {
        when(documentoService.subir(eq(EntidadDocumento.PROVEEDOR), eq(40L), any())).thenReturn(
                new DocumentoResponse(5L, "rut.pdf", "application/pdf", 8, "PROVEEDOR", 40L, 9L, null));

        mvc.perform(multipart("/api/documentos").file(new MockMultipartFile("archivo", "rut.pdf", "application/pdf", PDF))
                        .param("entidad", "PROVEEDOR").param("entidadId", "40")
                        .header(HttpHeaders.AUTHORIZATION, bearerProveedor(40L)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(5))
                .andExpect(jsonPath("$.rutaStorage").doesNotExist());
    }

    @Test
    void sinArchivoOEntidadInvalidaDevuelve400() throws Exception {
        mvc.perform(multipart("/api/documentos").param("entidad", "PROVEEDOR").param("entidadId", "40")
                        .header(HttpHeaders.AUTHORIZATION, bearerProveedor(40L)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("Debe enviar el archivo en el campo 'archivo' (multipart/form-data)"));
        mvc.perform(multipart("/api/documentos").file(new MockMultipartFile("archivo", "rut.pdf", "application/pdf", PDF))
                        .param("entidad", "FACTURA_FALSA").param("entidadId", "40")
                        .header(HttpHeaders.AUTHORIZATION, bearerProveedor(40L)))
                .andExpect(status().isBadRequest());
        mvc.perform(multipart("/api/documentos").file(new MockMultipartFile("archivo", "rut.pdf", "application/pdf", PDF))
                        .param("entidad", "PROVEEDOR").param("entidadId", "-1")
                        .header(HttpHeaders.AUTHORIZATION, bearerProveedor(40L)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detalles", hasItem("entidadId: debe ser un id válido")));
        verifyNoInteractions(documentoService);
    }

    @Test
    void erroresDelServiceLleganConSuCodigo() throws Exception {
        when(documentoService.subir(any(), any(), any()))
                .thenThrow(new ParametroInvalidoException("El contenido del archivo no corresponde a un PDF"));
        mvc.perform(multipart("/api/documentos").file(new MockMultipartFile("archivo", "f.pdf", "application/pdf", PDF))
                        .param("entidad", "PROVEEDOR").param("entidadId", "40")
                        .header(HttpHeaders.AUTHORIZATION, bearerProveedor(40L)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("El contenido del archivo no corresponde a un PDF"));

        when(documentoService.listar(EntidadDocumento.PROVEEDOR, 41L))
                .thenThrow(new AccesoDenegadoException("No puede ver los documentos de este proveedor"));
        mvc.perform(get("/api/documentos?entidad=PROVEEDOR&entidadId=41").header(HttpHeaders.AUTHORIZATION, bearerProveedor(40L)))
                .andExpect(status().isForbidden());
    }

    @Test
    void descargaComoAdjuntoConNosniffYNombreConTildes() throws Exception {
        when(documentoService.descargar(5L)).thenReturn(
                new ArchivoDescarga(new ByteArrayResource(PDF), "Cotización 2026.pdf", "application/pdf", PDF.length));

        mvc.perform(get("/api/documentos/5/contenido").header(HttpHeaders.AUTHORIZATION, bearer("COMPRADOR")))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "application/pdf"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("attachment")))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("filename*=UTF-8''Cotizaci%C3%B3n%202026.pdf")))
                .andExpect(content().bytes(PDF));
    }

    @Test
    void requiereSesionYNoSePuedeBorrar() throws Exception {
        mvc.perform(get("/api/documentos/5")).andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/documentos/5").header(HttpHeaders.AUTHORIZATION, bearer("ADMIN_SISTEMA")))
                .andExpect(status().isMethodNotAllowed());
    }
}
