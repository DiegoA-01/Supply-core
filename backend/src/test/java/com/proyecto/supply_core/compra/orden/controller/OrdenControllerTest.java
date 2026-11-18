package com.proyecto.supply_core.compra.orden.controller;

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
import com.proyecto.supply_core.compra.orden.dto.OrdenResponse;
import com.proyecto.supply_core.compra.orden.enums.EstadoOrden;
import com.proyecto.supply_core.compra.orden.service.OrdenCompraService;
import com.proyecto.supply_core.compra.orden.service.OrdenDirectaService;
import com.proyecto.supply_core.compra.orden.service.OrdenProveedorService;

@WebMvcTest({ OrdenController.class, PortalOrdenController.class })
class OrdenControllerTest extends WebTestBase {

    @MockitoBean
    private OrdenCompraService ordenService;

    @MockitoBean
    private OrdenProveedorService proveedorService;

    @MockitoBean
    private OrdenDirectaService directaService;

    private final OrdenResponse emitida = new OrdenResponse(11L, "OC-000001", 1, null, null, EstadoOrden.EMITIDA, 50L,
            null, null, 40L, "Tech SAS", 1L, "Principal", "Calle 10", LocalDate.now().plusDays(5), new BigDecimal("21600.00"),
            new BigDecimal("4104.00"), new BigDecimal("25704.00"), null, List.of(), List.of());

    @Test
    void emitirSoloCompradorYValidado() throws Exception {
        when(ordenService.emitir(any())).thenReturn(emitida);
        String valido = """
                {"adjudicacionId":50,"bodegaDestinoId":1,"lugarEntrega":"Calle 10","fechaEntregaEsperada":"%s"}"""
                .formatted(LocalDate.now().plusDays(5));

        mvc.perform(post("/api/ordenes").header(HttpHeaders.AUTHORIZATION, bearer("COMPRADOR"))
                        .contentType(MediaType.APPLICATION_JSON).content(valido))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.total").value(25704.0));
        mvc.perform(post("/api/ordenes").header(HttpHeaders.AUTHORIZATION, bearer("ALMACENISTA"))
                        .contentType(MediaType.APPLICATION_JSON).content(valido))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/ordenes").header(HttpHeaders.AUTHORIZATION, bearer("COMPRADOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"adjudicacionId\":50,\"bodegaDestinoId\":1,\"lugarEntrega\":\"\",\"fechaEntregaEsperada\":\"2000-01-01\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detalles", hasItem("fechaEntregaEsperada: debe ser una fecha futura")));
    }

    @Test
    void laOrdenDirectaDeContratoEsSoloDelCompradorYValida() throws Exception {
        when(directaService.emitir(any())).thenReturn(emitida);
        String valido = """
                {"solicitudId":7,"contratoId":5,"bodegaDestinoId":1,"lugarEntrega":"Calle 10","fechaEntregaEsperada":"%s"}"""
                .formatted(LocalDate.now().plusDays(5));

        mvc.perform(post("/api/ordenes/directa").header(HttpHeaders.AUTHORIZATION, bearer("COMPRADOR"))
                        .contentType(MediaType.APPLICATION_JSON).content(valido))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.consecutivo").value("OC-000001"));
        mvc.perform(post("/api/ordenes/directa").header(HttpHeaders.AUTHORIZATION, bearer("ADMIN_COMPRAS"))
                        .contentType(MediaType.APPLICATION_JSON).content(valido))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/ordenes/directa").header(HttpHeaders.AUTHORIZATION, bearer("COMPRADOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"solicitudId\":0,\"bodegaDestinoId\":1,\"lugarEntrega\":\"\",\"fechaEntregaEsperada\":\"2000-01-01\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detalles", hasItem("contratoId: es obligatorio")))
                .andExpect(jsonPath("$.detalles", hasItem("solicitudId: debe ser un id válido")));
    }

    @Test
    void nuevaVersionExigeMotivo() throws Exception {
        mvc.perform(post("/api/ordenes/11/version").header(HttpHeaders.AUTHORIZATION, bearer("COMPRADOR"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"cantidades\":[{\"lineaId\":1,\"cantidad\":-1}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detalles", hasItem("motivo: es obligatorio")))
                .andExpect(jsonPath("$.detalles", hasItem("cantidades[0].cantidad: no puede ser negativa")));
        verifyNoInteractions(ordenService);
    }

    @Test
    void portalDelProveedor() throws Exception {
        when(proveedorService.despachar(eq(11L), any())).thenReturn(emitida);

        mvc.perform(post("/api/portal/ordenes/11/despachar").header(HttpHeaders.AUTHORIZATION, bearerProveedor(40L)))
                .andExpect(status().isOk());
        mvc.perform(post("/api/portal/ordenes/11/aceptar").header(HttpHeaders.AUTHORIZATION, bearer("COMPRADOR")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/ordenes").header(HttpHeaders.AUTHORIZATION, bearerProveedor(40L)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/ordenes/11").header(HttpHeaders.AUTHORIZATION, bearer("SOLICITANTE")))
                .andExpect(status().isForbidden());
    }
}
