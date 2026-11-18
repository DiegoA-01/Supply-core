package com.proyecto.supply_core.reorden.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.proyecto.supply_core.common.web.WebTestBase;
import com.proyecto.supply_core.compra.solicitud.dto.SolicitudResponse;
import com.proyecto.supply_core.compra.solicitud.entity.SolicitudCompra;
import com.proyecto.supply_core.reorden.dto.SugerenciaReorden;
import com.proyecto.supply_core.reorden.service.ReordenService;

@WebMvcTest(ReordenController.class)
class ReordenControllerTest extends WebTestBase {

    @MockitoBean
    private ReordenService reordenService;

    private static SugerenciaReorden sugerencia() {
        return new SugerenciaReorden(100L, "CAB-1", "Cable", "UND", new BigDecimal("20"), new BigDecimal("10"),
                new BigDecimal("100"), new BigDecimal("40"), BigDecimal.ZERO, new BigDecimal("40.0000"), false, false,
                null);
    }

    @Test
    void lasSugerenciasLasVenQuienesGestionanElAbastecimiento() throws Exception {
        when(reordenService.sugerencias()).thenReturn(List.of(sugerencia()));

        for (String rol : new String[] { "ADMIN_COMPRAS", "COMPRADOR", "ALMACENISTA", "SOLICITANTE", "AUDITOR" }) {
            mvc.perform(get("/api/reorden/sugerencias").header(HttpHeaders.AUTHORIZATION, bearer(rol)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].cantidadSugerida").value(40.0));
        }
        mvc.perform(get("/api/reorden/sugerencias").header(HttpHeaders.AUTHORIZATION, bearer("CAJERO")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/reorden/sugerencias").header(HttpHeaders.AUTHORIZATION, bearerProveedor(40L)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/reorden/sugerencias")).andExpect(status().isUnauthorized());
    }

    @Test
    void soloElSolicitanteCreaElBorradorYSeValidaElCuerpo() throws Exception {
        SolicitudCompra s = new SolicitudCompra();
        s.setId(7L);
        s.setConsecutivo("SOL-000001");
        when(reordenService.crearBorrador(any())).thenReturn(SolicitudResponse.of(s, List.of()));
        String valido = "{\"fechaRequerida\":\"" + LocalDate.now().plusDays(3) + "\",\"productoIds\":[100,101]}";

        mvc.perform(post("/api/reorden/borrador").contentType(MediaType.APPLICATION_JSON).content(valido)
                        .header(HttpHeaders.AUTHORIZATION, bearer("SOLICITANTE")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.consecutivo").value("SOL-000001"));
        verify(reordenService).crearBorrador(any());
        mvc.perform(post("/api/reorden/borrador").contentType(MediaType.APPLICATION_JSON).content(valido)
                        .header(HttpHeaders.AUTHORIZATION, bearer("COMPRADOR")))
                .andExpect(status().isForbidden());
    }

    @Test
    void elBorradorRechazaDatosInvalidosAntesDelService() throws Exception {
        String[] malos = {
                "{}",
                "{\"fechaRequerida\":\"2000-01-01\"}",
                "{\"fechaRequerida\":\"" + LocalDate.now() + "\",\"productoIds\":[0]}",
                "{\"fechaRequerida\":\"" + LocalDate.now() + "\",\"productoIds\":[5,5]}" };
        for (String malo : malos) {
            mvc.perform(post("/api/reorden/borrador").contentType(MediaType.APPLICATION_JSON).content(malo)
                            .header(HttpHeaders.AUTHORIZATION, bearer("SOLICITANTE")))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(reordenService);
    }
}
