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
import com.proyecto.supply_core.reporte.dto.EstadoCuentaResponse;
import com.proyecto.supply_core.reporte.dto.GastoResponse;
import com.proyecto.supply_core.reporte.dto.InventarioResumen;
import com.proyecto.supply_core.reporte.service.ReporteService;

@WebMvcTest(ReporteController.class)
class ReporteControllerTest extends WebTestBase {

    @MockitoBean
    private ReporteService reporteService;

    private static final BigDecimal CERO = new BigDecimal("0.00");

    @Test
    void gastoPasaLosFiltrosYLoVenLosRolesDeGestion() throws Exception {
        when(reporteService.gasto(any(), any(), any(), any(), any())).thenReturn(new GastoResponse(null, null, 0, CERO, CERO,
                CERO, CERO, List.of(), List.of(), List.of()));

        mvc.perform(get("/api/reportes/gasto?desde=2026-10-01&hasta=2026-10-31&categoriaId=5&proveedorId=7")
                        .header(HttpHeaders.AUTHORIZATION, bearer("AUDITOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ordenes").value(0))
                .andExpect(jsonPath("$.porProveedor").isEmpty());
        verify(reporteService).gasto(eq(LocalDate.of(2026, 10, 1)), eq(LocalDate.of(2026, 10, 31)), eq(5L), eq(7L), isNull());
        mvc.perform(get("/api/reportes/gasto").header(HttpHeaders.AUTHORIZATION, bearer("COMPRADOR")))
                .andExpect(status().isOk());
    }

    @Test
    void gastoNoLoVenSolicitanteAlmacenistaNiProveedor() throws Exception {
        mvc.perform(get("/api/reportes/gasto").header(HttpHeaders.AUTHORIZATION, bearer("SOLICITANTE")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/reportes/gasto").header(HttpHeaders.AUTHORIZATION, bearer("ALMACENISTA")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/reportes/gasto").header(HttpHeaders.AUTHORIZATION, bearerProveedor(40L)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/reportes/gasto")).andExpect(status().isUnauthorized());
        verifyNoInteractions(reporteService);
    }

    @Test
    void filtrosInvalidosSeRechazan() throws Exception {
        mvc.perform(get("/api/reportes/gasto?desde=ayer").header(HttpHeaders.AUTHORIZATION, bearer("AUDITOR")))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/reportes/gasto?categoriaId=0").header(HttpHeaders.AUTHORIZATION, bearer("AUDITOR")))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/reportes/inventario?categoriaId=-1").header(HttpHeaders.AUTHORIZATION, bearer("AUDITOR")))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(reporteService);
    }

    @Test
    void elAlmacenistaVeSoloElInventario() throws Exception {
        when(reporteService.inventario(null, true)).thenReturn(new InventarioResumen(0, 0, CERO, List.of()));

        mvc.perform(get("/api/reportes/inventario?soloBajoMinimo=true").header(HttpHeaders.AUTHORIZATION, bearer("ALMACENISTA")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty());
        mvc.perform(get("/api/reportes/proveedores").header(HttpHeaders.AUTHORIZATION, bearer("ALMACENISTA")))
                .andExpect(status().isForbidden());
    }

    @Test
    void rankingYEstadoDeCuenta() throws Exception {
        when(reporteService.proveedores(any(), any(), any(), any())).thenReturn(List.of());
        when(reporteService.estadoCuenta(eq(9L), any(), any())).thenReturn(new EstadoCuentaResponse(9L, "Tech", "800-1",
                null, null, CERO, CERO, CERO, CERO, List.of()));

        mvc.perform(get("/api/reportes/proveedores").header(HttpHeaders.AUTHORIZATION, bearer("ADMIN_COMPRAS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
        mvc.perform(get("/api/reportes/proveedores/9/estado-cuenta").header(HttpHeaders.AUTHORIZATION, bearer("ADMINISTRATIVO")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.razonSocial").value("Tech"));
        mvc.perform(get("/api/reportes/proveedores/0/estado-cuenta").header(HttpHeaders.AUTHORIZATION, bearer("ADMINISTRATIVO")))
                .andExpect(status().isBadRequest());
    }
}
