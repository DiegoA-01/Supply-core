package com.proyecto.supply_core.compra.analisis.controller;

import static org.hamcrest.Matchers.hasItem;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.proyecto.supply_core.common.exception.NegocioException;
import com.proyecto.supply_core.common.web.WebTestBase;
import com.proyecto.supply_core.compra.analisis.dto.AnalisisResponse;
import com.proyecto.supply_core.compra.analisis.dto.PesosResponse;
import com.proyecto.supply_core.compra.analisis.enums.EstadoAnalisis;
import com.proyecto.supply_core.compra.analisis.service.AnalisisService;
import com.proyecto.supply_core.compra.analisis.service.PesosService;
import com.proyecto.supply_core.ia.controller.ExtraccionController;
import com.proyecto.supply_core.ia.dto.ProcesamientoResponse;
import com.proyecto.supply_core.ia.enums.EstadoProcesamiento;
import com.proyecto.supply_core.ia.service.ExtraccionService;

@WebMvcTest({ AnalisisController.class, PesosController.class, ExtraccionController.class })
class AnalisisIAControllerTest extends WebTestBase {

    @MockitoBean
    private AnalisisService analisisService;

    @MockitoBean
    private PesosService pesosService;

    @MockitoBean
    private ExtraccionService extraccionService;

    private final AnalisisResponse borrador = new AnalisisResponse(70L, 9L, EstadoAnalisis.BORRADOR, 1L, null, null,
            null, List.of(), List.of(new AnalisisResponse.Resultado(1L, "A", new BigDecimal("25704.00"),
                    new BigDecimal("87.00"), 1, "Puesto 1 de 2", List.of(), List.of())));

    @Test
    void analisisGeneraYPublicaSoloComprador() throws Exception {
        when(analisisService.generar(9L)).thenReturn(borrador);
        when(analisisService.publicar(9L)).thenThrow(new NegocioException("EXTRACCION_PENDIENTE",
                "No se puede publicar: B: lectura REQUIERE_REVISION"));

        mvc.perform(post("/api/rfqs/9/analisis").header(HttpHeaders.AUTHORIZATION, bearer("COMPRADOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultados[0].scoreFinal").value(87.0));
        mvc.perform(post("/api/rfqs/9/analisis/publicar").header(HttpHeaders.AUTHORIZATION, bearer("COMPRADOR")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("EXTRACCION_PENDIENTE"));
        mvc.perform(post("/api/rfqs/9/analisis").header(HttpHeaders.AUTHORIZATION, bearer("APROBADOR")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/rfqs/9/analisis").header(HttpHeaders.AUTHORIZATION, bearerProveedor(40L)))
                .andExpect(status().isForbidden());
    }

    @Test
    void pesosLosCambiaAdminComprasYSeValidan() throws Exception {
        when(pesosService.cambiar(anyMap())).thenReturn(new PesosResponse(2L, null, null, List.of()));

        mvc.perform(post("/api/config-pesos").header(HttpHeaders.AUTHORIZATION, bearer("ADMIN_COMPRAS"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"pesos\":{\"PRECIO\":150}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detalles[0]").value(org.hamcrest.Matchers.containsString("no puede ser mayor que 100")));
        mvc.perform(post("/api/config-pesos").header(HttpHeaders.AUTHORIZATION, bearer("COMPRADOR"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"pesos\":{\"PRECIO\":100}}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/config-pesos").header(HttpHeaders.AUTHORIZATION, bearer("ADMIN_COMPRAS"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"pesos\":{\"PRECIO\":100}}"))
                .andExpect(status().isCreated());
    }

    @Test
    void extraccionConfirmarYDigitarValidados() throws Exception {
        ProcesamientoResponse completo = new ProcesamientoResponse(5L, 77L, EstadoProcesamiento.COMPLETADO, "manual",
                0, null, null, null, new BigDecimal("0.80"), List.of());
        when(extraccionService.confirmar(eq(77L), eq(1L), any())).thenReturn(completo);

        mvc.perform(patch("/api/cotizaciones/77/extraccion/campos/1").header(HttpHeaders.AUTHORIZATION, bearer("COMPRADOR"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"valor\":\"11.500\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("COMPLETADO"));
        mvc.perform(put("/api/cotizaciones/77/extraccion/lineas").header(HttpHeaders.AUTHORIZATION, bearer("COMPRADOR"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"lineas\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detalles", hasItem("lineas: debe tener al menos una línea")));
        mvc.perform(get("/api/cotizaciones/77/extraccion").header(HttpHeaders.AUTHORIZATION, bearerProveedor(40L)))
                .andExpect(status().isForbidden());
    }

    @Test
    void auditorSoloConsulta() throws Exception {
        mvc.perform(patch("/api/cotizaciones/77/extraccion/campos/1").header(HttpHeaders.AUTHORIZATION, bearer("AUDITOR"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"valor\":\"1\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(extraccionService);
    }
}
