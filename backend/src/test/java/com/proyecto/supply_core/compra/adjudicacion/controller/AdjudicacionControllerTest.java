package com.proyecto.supply_core.compra.adjudicacion.controller;

import static org.hamcrest.Matchers.hasItem;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.proyecto.supply_core.common.exception.ParametroInvalidoException;
import com.proyecto.supply_core.common.web.WebTestBase;
import com.proyecto.supply_core.compra.adjudicacion.dto.AdjudicacionResponse;
import com.proyecto.supply_core.compra.adjudicacion.enums.EstadoAdjudicacion;
import com.proyecto.supply_core.compra.adjudicacion.service.AdjudicacionService;

@WebMvcTest(AdjudicacionController.class)
class AdjudicacionControllerTest extends WebTestBase {

    @MockitoBean
    private AdjudicacionService adjudicacionService;

    private final AdjudicacionResponse aprobada = new AdjudicacionResponse(50L, 9L, "RFQ-000009", 70L, 1L, 41L,
            "Proveedor 1", new BigDecimal("25704.00"), true, null, EstadoAdjudicacion.APROBADA, null, 30L, null, null,
            null, null);

    @Test
    void adjudicarSoloCompradorYValidado() throws Exception {
        when(adjudicacionService.adjudicar(eq(9L), any())).thenReturn(aprobada);

        mvc.perform(post("/api/rfqs/9/adjudicacion").header(HttpHeaders.AUTHORIZATION, bearer("COMPRADOR"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"cotizacionId\":1}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("APROBADA"));
        mvc.perform(post("/api/rfqs/9/adjudicacion").header(HttpHeaders.AUTHORIZATION, bearer("APROBADOR"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"cotizacionId\":1}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/rfqs/9/adjudicacion").header(HttpHeaders.AUTHORIZATION, bearer("COMPRADOR"))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detalles", hasItem("cotizacionId: es obligatoria")));
    }

    @Test
    void justificacionFaltanteDevuelve400() throws Exception {
        when(adjudicacionService.adjudicar(eq(9L), any())).thenThrow(new ParametroInvalidoException(
                "justificacion: es obligatoria (mínimo 10 caracteres) cuando no se elige la cotización recomendada"));
        mvc.perform(post("/api/rfqs/9/adjudicacion").header(HttpHeaders.AUTHORIZATION, bearer("COMPRADOR"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"cotizacionId\":2}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void decisionExigeObservacionesAlRechazarYNoEsParaProveedores() throws Exception {
        mvc.perform(post("/api/adjudicaciones/50/decision").header(HttpHeaders.AUTHORIZATION, bearer("APROBADOR"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"aprobar\":false}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detalles", hasItem("observacionesPresentes: son obligatorias al rechazar")));
        mvc.perform(post("/api/adjudicaciones/50/decision").header(HttpHeaders.AUTHORIZATION, bearerProveedor(40L))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"aprobar\":true}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(adjudicacionService);
        mvc.perform(get("/api/adjudicaciones/50").header(HttpHeaders.AUTHORIZATION, bearer("SOLICITANTE")))
                .andExpect(status().isForbidden());
    }
}
