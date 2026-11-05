package com.proyecto.supply_core.presupuesto.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

import com.proyecto.supply_core.common.web.WebTestBase;
import com.proyecto.supply_core.presupuesto.dto.PresupuestoResponse;
import com.proyecto.supply_core.presupuesto.service.PresupuestoService;

@WebMvcTest(PresupuestoController.class)
class PresupuestoControllerTest extends WebTestBase {

    @MockitoBean
    private PresupuestoService presupuestoService;

    private static final String VALIDO = """
            {"centroCostoId":5,"anio":2026,"mes":11,"monto":10000}""";

    private static PresupuestoResponse respuesta() {
        return new PresupuestoResponse(1L, 5L, 2026, 11, new BigDecimal("10000.00"), new BigDecimal("2500.00"),
                new BigDecimal("7500.00"), new BigDecimal("25.00"), null, null);
    }

    @Test
    void soloAdminComprasDefineYCorrigeElPresupuesto() throws Exception {
        when(presupuestoService.crear(any())).thenReturn(respuesta());

        mvc.perform(post("/api/presupuestos").contentType(MediaType.APPLICATION_JSON).content(VALIDO)
                        .header(HttpHeaders.AUTHORIZATION, bearer("ADMIN_COMPRAS")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.disponible").value(7500.00));
        mvc.perform(post("/api/presupuestos").contentType(MediaType.APPLICATION_JSON).content(VALIDO)
                        .header(HttpHeaders.AUTHORIZATION, bearer("COMPRADOR")))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/presupuestos/1").contentType(MediaType.APPLICATION_JSON).content("{\"monto\":5}")
                        .header(HttpHeaders.AUTHORIZATION, bearer("APROBADOR")))
                .andExpect(status().isForbidden());
    }

    @Test
    void datosInvalidosSeRechazanAntesDelService() throws Exception {
        String[] malos = {
                "{\"centroCostoId\":5,\"anio\":2026,\"mes\":13,\"monto\":10}",
                "{\"centroCostoId\":5,\"anio\":2026,\"mes\":0,\"monto\":10}",
                "{\"centroCostoId\":5,\"anio\":1999,\"mes\":5,\"monto\":10}",
                "{\"centroCostoId\":5,\"anio\":2026,\"mes\":5,\"monto\":0}",
                "{\"centroCostoId\":5,\"anio\":2026,\"mes\":5,\"monto\":-3}",
                "{\"centroCostoId\":5,\"anio\":2026,\"mes\":5,\"monto\":1.234}",
                "{\"anio\":2026,\"mes\":5,\"monto\":10}" };
        for (String cuerpo : malos) {
            mvc.perform(post("/api/presupuestos").contentType(MediaType.APPLICATION_JSON).content(cuerpo)
                            .header(HttpHeaders.AUTHORIZATION, bearer("ADMIN_COMPRAS")))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(put("/api/presupuestos/0").contentType(MediaType.APPLICATION_JSON).content("{\"monto\":5}")
                        .header(HttpHeaders.AUTHORIZATION, bearer("ADMIN_COMPRAS")))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(presupuestoService);
    }

    @Test
    void lasConsultasLasVenLosRolesDeGestionYNoElSolicitanteNiElProveedor() throws Exception {
        when(presupuestoService.listar(5L, 2026)).thenReturn(List.of(respuesta()));

        mvc.perform(get("/api/presupuestos?centroCostoId=5&anio=2026")
                        .header(HttpHeaders.AUTHORIZATION, bearer("AUDITOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].usoPct").value(25.00));
        verify(presupuestoService).listar(5L, 2026);
        mvc.perform(get("/api/presupuestos").header(HttpHeaders.AUTHORIZATION, bearer("SOLICITANTE")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/presupuestos").header(HttpHeaders.AUTHORIZATION, bearerProveedor(40L)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/presupuestos")).andExpect(status().isUnauthorized());
    }

    @Test
    void filtrosYIdsInvalidosSeRechazan() throws Exception {
        mvc.perform(get("/api/presupuestos?anio=1900").header(HttpHeaders.AUTHORIZATION, bearer("AUDITOR")))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/presupuestos?centroCostoId=0").header(HttpHeaders.AUTHORIZATION, bearer("AUDITOR")))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/presupuestos/0").header(HttpHeaders.AUTHORIZATION, bearer("AUDITOR")))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(presupuestoService);
    }
}
