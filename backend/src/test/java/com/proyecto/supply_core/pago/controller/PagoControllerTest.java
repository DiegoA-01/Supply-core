package com.proyecto.supply_core.pago.controller;

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
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.proyecto.supply_core.common.web.WebTestBase;
import com.proyecto.supply_core.pago.dto.PagoResponse;
import com.proyecto.supply_core.pago.dto.PagosOrdenResponse;
import com.proyecto.supply_core.pago.enums.EstadoPago;
import com.proyecto.supply_core.pago.enums.MetodoPago;
import com.proyecto.supply_core.pago.enums.TipoPago;
import com.proyecto.supply_core.pago.service.PagoService;

@WebMvcTest({ PagoController.class, PortalPagoController.class })
class PagoControllerTest extends WebTestBase {

    @MockitoBean
    private PagoService pagoService;

    private final PagoResponse pago = new PagoResponse(900L, 11L, "OC-000001", 1, "Tech SAS", 60L,
            MetodoPago.TRANSFERENCIA, TipoPago.ANTICIPO, new BigDecimal("400.00"), LocalDate.now(), "TR-123", EstadoPago.REGISTRADO, true,
            null, null, null, null, null);

    @Test
    void registrarSoloAdministrativoYValidado() throws Exception {
        when(pagoService.registrar(eq(11L), any())).thenReturn(pago);
        String valido = """
                {"metodo":"TRANSFERENCIA","tipo":"ANTICIPO","valor":400,"fechaPago":"%s","referencia":"TR-123"}""".formatted(LocalDate.now());

        mvc.perform(post("/api/ordenes/11/pagos").header(HttpHeaders.AUTHORIZATION, bearer("ADMINISTRATIVO"))
                        .contentType(MediaType.APPLICATION_JSON).content(valido))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.evidenciaPendiente").value(true));
        mvc.perform(post("/api/ordenes/11/pagos").header(HttpHeaders.AUTHORIZATION, bearer("COMPRADOR"))
                        .contentType(MediaType.APPLICATION_JSON).content(valido))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/ordenes/11/pagos").header(HttpHeaders.AUTHORIZATION, bearer("ADMINISTRATIVO"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"valor\":0,\"fechaPago\":\"2999-01-01\",\"referencia\":\"x\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detalles", hasItem("metodo: es obligatorio")))
                .andExpect(jsonPath("$.detalles", hasItem("tipo: es obligatorio")))
                .andExpect(jsonPath("$.detalles", hasItem("valor: debe ser mayor que cero")))
                .andExpect(jsonPath("$.detalles", hasItem("fechaPago: no puede ser una fecha futura")));
    }

    @Test
    void anularExigeMotivo() throws Exception {
        mvc.perform(post("/api/pagos/900/anular").header(HttpHeaders.AUTHORIZATION, bearer("ADMINISTRATIVO"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"motivo\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detalles", hasItem("motivo: es obligatorio")));
        verifyNoInteractions(pagoService);
    }

    @Test
    void consultasPorRol() throws Exception {
        when(pagoService.deOrden(11L)).thenReturn(new PagosOrdenResponse("OC-000001", 11L, new BigDecimal("1000.00"),
                new BigDecimal("400.00"), new BigDecimal("600.00"), List.of(pago)));
        when(pagoService.deOrdenDelProveedor(11L)).thenReturn(new PagosOrdenResponse("OC-000001", 11L,
                new BigDecimal("1000.00"), new BigDecimal("400.00"), new BigDecimal("600.00"), List.of(pago)));

        mvc.perform(get("/api/ordenes/11/pagos").header(HttpHeaders.AUTHORIZATION, bearer("AUDITOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.saldo").value(600.0));
        mvc.perform(get("/api/ordenes/11/pagos").header(HttpHeaders.AUTHORIZATION, bearer("ALMACENISTA")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/pagos").header(HttpHeaders.AUTHORIZATION, bearer("COMPRADOR")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/pagos?desde=ayer").header(HttpHeaders.AUTHORIZATION, bearer("ADMINISTRATIVO")))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/portal/ordenes/11/pagos").header(HttpHeaders.AUTHORIZATION, bearerProveedor(40L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pagado").value(400.0));
        mvc.perform(get("/api/portal/ordenes/11/pagos").header(HttpHeaders.AUTHORIZATION, bearer("ADMINISTRATIVO")))
                .andExpect(status().isForbidden());
    }
}
