package com.proyecto.supply_core.factura.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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

import com.proyecto.supply_core.common.exception.NegocioException;
import com.proyecto.supply_core.common.web.WebTestBase;
import com.proyecto.supply_core.factura.dto.FacturaResponse;
import com.proyecto.supply_core.factura.enums.EstadoFactura;
import com.proyecto.supply_core.factura.service.FacturaService;

@WebMvcTest({ FacturaController.class, PortalFacturaController.class })
class FacturaControllerTest extends WebTestBase {

    private static final String VALIDA = """
            {"numero":"FE-100","fechaFactura":"%s","subtotal":1000.00,"impuestos":190.00,"total":1190.00}"""
            .formatted(LocalDate.now());

    @MockitoBean
    private FacturaService facturaService;

    private final FacturaResponse factura = new FacturaResponse(70L, 11L, "OC-000001", 40L, "Tech SAS", 60L, "FE-100",
            LocalDate.now(), new BigDecimal("1000.00"), new BigDecimal("190.00"), new BigDecimal("1190.00"),
            new BigDecimal("1000.00"), new BigDecimal("190.00"), new BigDecimal("1190.00"), BigDecimal.ZERO,
            EstadoFactura.CUADRADA, null, null, null, null, null, null, null, null);

    @Test
    void soloElAdministrativoRegistraLaFactura() throws Exception {
        when(facturaService.registrar(eq(11L), any())).thenReturn(factura);

        mvc.perform(post("/api/ordenes/11/facturas").header(HttpHeaders.AUTHORIZATION, bearer("ADMINISTRATIVO"))
                        .contentType(MediaType.APPLICATION_JSON).content(VALIDA))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("CUADRADA"));
        for (String rol : new String[] { "COMPRADOR", "ADMIN_COMPRAS", "APROBADOR", "AUDITOR" }) {
            mvc.perform(post("/api/ordenes/11/facturas").header(HttpHeaders.AUTHORIZATION, bearer(rol))
                            .contentType(MediaType.APPLICATION_JSON).content(VALIDA))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void laFacturaSeValidaCampoPorCampoYElTotalDebeSumar() throws Exception {
        mvc.perform(post("/api/ordenes/11/facturas").header(HttpHeaders.AUTHORIZATION, bearer("ADMINISTRATIVO"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"numero\":\"\",\"fechaFactura\":\"2999-01-01\",\"subtotal\":-1,\"impuestos\":1,\"total\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detalles", hasItem("numero: es obligatorio")))
                .andExpect(jsonPath("$.detalles", hasItem("fechaFactura: no puede ser una fecha futura")))
                .andExpect(jsonPath("$.detalles", hasItem("subtotal: no puede ser negativo")))
                .andExpect(jsonPath("$.detalles", hasItem("total: debe ser mayor que cero")));

        mvc.perform(post("/api/ordenes/11/facturas").header(HttpHeaders.AUTHORIZATION, bearer("ADMINISTRATIVO"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"numero\":\"FE-1\",\"fechaFactura\":\"%s\",\"subtotal\":1000,\"impuestos\":190,\"total\":1200}"
                                .formatted(LocalDate.now())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detalles[*]", hasItem(containsString("el total debe ser igual al subtotal más los impuestos"))));
        verifyNoInteractions(facturaService);
    }

    @Test
    void laReglaDeNegocioDelServiceSeTraduceAConflicto() throws Exception {
        when(facturaService.registrar(eq(11L), any()))
                .thenThrow(new NegocioException("FACTURA_EXISTENTE", "La orden ya tiene factura"));

        mvc.perform(post("/api/ordenes/11/facturas").header(HttpHeaders.AUTHORIZATION, bearer("ADMINISTRATIVO"))
                        .contentType(MediaType.APPLICATION_JSON).content(VALIDA))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("FACTURA_EXISTENTE"));
    }

    @Test
    void consultasPorRol() throws Exception {
        when(facturaService.deOrden(11L)).thenReturn(List.of(factura));
        when(facturaService.obtener(70L)).thenReturn(factura);

        for (String rol : new String[] { "ADMINISTRATIVO", "ADMIN_COMPRAS", "COMPRADOR", "APROBADOR", "AUDITOR" }) {
            mvc.perform(get("/api/ordenes/11/facturas").header(HttpHeaders.AUTHORIZATION, bearer(rol)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].numero").value("FE-100"));
        }
        mvc.perform(get("/api/facturas/70").header(HttpHeaders.AUTHORIZATION, bearer("AUDITOR"))).andExpect(status().isOk());
        mvc.perform(get("/api/facturas").header(HttpHeaders.AUTHORIZATION, bearer("COMPRADOR"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/ordenes/11/facturas").header(HttpHeaders.AUTHORIZATION, bearer("ALMACENISTA")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/facturas/0").header(HttpHeaders.AUTHORIZATION, bearer("AUDITOR"))).andExpect(status().isBadRequest());
    }

    @Test
    void aprobarLoHaceComprasConMotivoYAnularLoHacenAdministrativoOCompras() throws Exception {
        when(facturaService.aprobar(eq(70L), any())).thenReturn(factura);
        when(facturaService.anular(eq(70L), any())).thenReturn(factura);

        mvc.perform(post("/api/facturas/70/aprobar").header(HttpHeaders.AUTHORIZATION, bearer("ADMIN_COMPRAS"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"motivo\":\"El flete estaba pactado\"}"))
                .andExpect(status().isOk());
        verify(facturaService).aprobar(70L, "El flete estaba pactado");
        mvc.perform(post("/api/facturas/70/aprobar").header(HttpHeaders.AUTHORIZATION, bearer("ADMINISTRATIVO"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"motivo\":\"El flete estaba pactado\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/facturas/70/aprobar").header(HttpHeaders.AUTHORIZATION, bearer("ADMIN_COMPRAS"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"motivo\":\"\"}"))
                .andExpect(status().isBadRequest());

        for (String rol : new String[] { "ADMINISTRATIVO", "ADMIN_COMPRAS" }) {
            mvc.perform(post("/api/facturas/70/anular").header(HttpHeaders.AUTHORIZATION, bearer(rol))
                            .contentType(MediaType.APPLICATION_JSON).content("{\"motivo\":\"Número mal digitado\"}"))
                    .andExpect(status().isOk());
        }
        mvc.perform(post("/api/facturas/70/anular").header(HttpHeaders.AUTHORIZATION, bearer("COMPRADOR"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"motivo\":\"Número mal digitado\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void recruzarYArchivoSonDelAdministrativo() throws Exception {
        when(facturaService.recruzar(70L)).thenReturn(factura);

        mvc.perform(post("/api/facturas/70/recruzar").header(HttpHeaders.AUTHORIZATION, bearer("ADMINISTRATIVO")))
                .andExpect(status().isOk());
        mvc.perform(post("/api/facturas/70/recruzar").header(HttpHeaders.AUTHORIZATION, bearer("ADMIN_COMPRAS")))
                .andExpect(status().isForbidden());
    }

    @Test
    void elProveedorVeLasFacturasDeSusOrdenesYNadieMas() throws Exception {
        when(facturaService.deOrdenDelProveedor(11L)).thenReturn(List.of(factura));

        mvc.perform(get("/api/portal/ordenes/11/facturas").header(HttpHeaders.AUTHORIZATION, bearerProveedor(40L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].estado").value("CUADRADA"));
        mvc.perform(get("/api/portal/ordenes/11/facturas").header(HttpHeaders.AUTHORIZATION, bearer("ADMINISTRATIVO")))
                .andExpect(status().isForbidden());
    }
}
