package com.proyecto.supply_core.inventario.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.proyecto.supply_core.common.web.WebTestBase;
import com.proyecto.supply_core.inventario.dto.CargaMasivaResponse;
import com.proyecto.supply_core.inventario.service.ProductoCargaService;

@WebMvcTest(ProductoCargaController.class)
class ProductoCargaControllerTest extends WebTestBase {

    @MockitoBean
    private ProductoCargaService cargaService;

    private final MockMultipartFile archivo = new MockMultipartFile("archivo", "productos.xlsx",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", new byte[] { 1, 2, 3 });

    @Test
    void soloAdminComprasSubeElArchivoYSePasaSoloValidar() throws Exception {
        when(cargaService.cargar(any(), eq(true))).thenReturn(new CargaMasivaResponse(true, false, 3, 2, 1, 1,
                List.of(new CargaMasivaResponse.ErrorFila(4, "falta el nombre"))));

        mvc.perform(multipart("/api/productos/carga-masiva").file(archivo).param("soloValidar", "true")
                        .header(HttpHeaders.AUTHORIZATION, bearer("ADMIN_COMPRAS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.creados").value(2))
                .andExpect(jsonPath("$.errores[0].fila").value(4));
        verify(cargaService).cargar(any(), eq(true));
        mvc.perform(multipart("/api/productos/carga-masiva").file(archivo)
                        .header(HttpHeaders.AUTHORIZATION, bearer("COMPRADOR")))
                .andExpect(status().isForbidden());
        mvc.perform(multipart("/api/productos/carga-masiva").file(archivo)
                        .header(HttpHeaders.AUTHORIZATION, bearerProveedor(40L)))
                .andExpect(status().isForbidden());
        mvc.perform(multipart("/api/productos/carga-masiva").file(archivo)).andExpect(status().isUnauthorized());
    }

    @Test
    void sinArchivoSeRechazaAntesDelService() throws Exception {
        mvc.perform(multipart("/api/productos/carga-masiva").header(HttpHeaders.AUTHORIZATION, bearer("ADMIN_COMPRAS")))
                .andExpect(status().is4xxClientError());
        verifyNoInteractions(cargaService);
    }

    @Test
    void laPlantillaSeDescargaComoXlsxSoloParaAdminCompras() throws Exception {
        when(cargaService.plantilla()).thenReturn(new byte[] { 9, 8, 7 });

        mvc.perform(get("/api/productos/carga-masiva/plantilla").header(HttpHeaders.AUTHORIZATION, bearer("ADMIN_COMPRAS")))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, org.hamcrest.Matchers.containsString("plantilla-productos.xlsx")))
                .andExpect(content().contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .andExpect(content().bytes(new byte[] { 9, 8, 7 }));
        mvc.perform(get("/api/productos/carga-masiva/plantilla").header(HttpHeaders.AUTHORIZATION, bearer("AUDITOR")))
                .andExpect(status().isForbidden());
    }
}
