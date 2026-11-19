package com.proyecto.supply_core.reporte.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.proyecto.supply_core.common.web.WebTestBase;
import com.proyecto.supply_core.reporte.dto.RotacionResponse;
import com.proyecto.supply_core.reporte.dto.RotacionResumen;
import com.proyecto.supply_core.reporte.service.RotacionService;

@WebMvcTest(RotacionController.class)
class RotacionControllerTest extends WebTestBase {

    @MockitoBean
    private RotacionService rotacionService;

    private static RotacionResponse vacia() {
        BigDecimal cero = new BigDecimal("0.00");
        return new RotacionResponse(LocalDate.of(2026, 8, 20), LocalDate.of(2026, 11, 17), 90,
                new RotacionResumen(0, 0, 0, 0, 0, cero, cero, 30, 90, 15), List.of());
    }

    @Test
    void pasaLosFiltrosYLoVenLosRolesQueAnalizanElInventario() throws Exception {
        when(rotacionService.rotacion(any(), any(), any(), any())).thenReturn(vacia());

        mvc.perform(get("/api/reportes/rotacion?desde=2026-10-01&hasta=2026-10-31&categoriaId=5")
                        .header(HttpHeaders.AUTHORIZATION, bearer("ADMIN_SISTEMA")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dias").value(90))
                .andExpect(jsonPath("$.resumen.diasPorAgotarse").value(15))
                .andExpect(jsonPath("$.items").isEmpty());
        verify(rotacionService).rotacion(eq(LocalDate.of(2026, 10, 1)), eq(LocalDate.of(2026, 10, 31)), eq(5L), isNull());
        for (String rol : List.of("ADMIN_COMPRAS", "COMPRADOR", "APROBADOR", "ADMINISTRATIVO", "AUDITOR", "ALMACENISTA")) {
            mvc.perform(get("/api/reportes/rotacion").header(HttpHeaders.AUTHORIZATION, bearer(rol)))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void noLoVenSolicitanteCajeroNiProveedorYExigeSesion() throws Exception {
        mvc.perform(get("/api/reportes/rotacion").header(HttpHeaders.AUTHORIZATION, bearer("SOLICITANTE")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/reportes/rotacion").header(HttpHeaders.AUTHORIZATION, bearer("CAJERO")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/reportes/rotacion").header(HttpHeaders.AUTHORIZATION, bearerProveedor(40L)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/reportes/rotacion")).andExpect(status().isUnauthorized());
        verifyNoInteractions(rotacionService);
    }

    @Test
    void filtrosInvalidosSeRechazan() throws Exception {
        mvc.perform(get("/api/reportes/rotacion?desde=ayer").header(HttpHeaders.AUTHORIZATION, bearer("AUDITOR")))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/reportes/rotacion?categoriaId=0").header(HttpHeaders.AUTHORIZATION, bearer("AUDITOR")))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(rotacionService);
    }
}
