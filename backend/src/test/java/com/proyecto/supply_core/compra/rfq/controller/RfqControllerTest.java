package com.proyecto.supply_core.compra.rfq.controller;

import static org.hamcrest.Matchers.hasItem;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.proyecto.supply_core.common.dto.PageResponse;
import com.proyecto.supply_core.common.exception.NoEncontradoException;
import com.proyecto.supply_core.common.web.WebTestBase;
import com.proyecto.supply_core.compra.rfq.dto.RfqPortalResponse;
import com.proyecto.supply_core.compra.rfq.dto.RfqResponse;
import com.proyecto.supply_core.compra.rfq.enums.EstadoRfq;
import com.proyecto.supply_core.compra.rfq.service.RfqService;

@WebMvcTest({ RfqController.class, PortalRfqController.class })
class RfqControllerTest extends WebTestBase {

    @MockitoBean
    private RfqService rfqService;

    private final RfqResponse borrador = new RfqResponse(9L, "RFQ-000001", EstadoRfq.BORRADOR, 5L, "SOL-000005", 30L,
            LocalDateTime.now().plusDays(3), null, null, null, null, List.of(), List.of(),
            List.of("No hay proveedores habilitados para la categoría Papelería"));

    @Test
    void crearSoloCompradorYValidado() throws Exception {
        when(rfqService.crear(any())).thenReturn(borrador);
        String valido = """
                {"solicitudId":5,"datos":{"fechaLimite":"%s","condiciones":"Entrega en bodega"}}"""
                .formatted(LocalDateTime.now().plusDays(3).withNano(0));

        mvc.perform(post("/api/rfqs").header(HttpHeaders.AUTHORIZATION, bearer("COMPRADOR"))
                        .contentType(MediaType.APPLICATION_JSON).content(valido))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.alertas[0]").value("No hay proveedores habilitados para la categoría Papelería"));
        mvc.perform(post("/api/rfqs").header(HttpHeaders.AUTHORIZATION, bearer("AUDITOR"))
                        .contentType(MediaType.APPLICATION_JSON).content(valido))
                .andExpect(status().isForbidden());
    }

    @Test
    void datosInvalidosDevuelven400() throws Exception {
        String invalido = """
                {"solicitudId":0,"datos":{"fechaLimite":"2000-01-01T00:00:00","proveedorIds":[-1],
                 "especificaciones":[{"productoId":1,"texto":""}]}}""";

        mvc.perform(post("/api/rfqs").header(HttpHeaders.AUTHORIZATION, bearer("COMPRADOR"))
                        .contentType(MediaType.APPLICATION_JSON).content(invalido))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detalles", hasItem("solicitudId: debe ser un id válido")))
                .andExpect(jsonPath("$.detalles", hasItem("datos.fechaLimite: debe ser una fecha futura")))
                .andExpect(jsonPath("$.detalles", hasItem("datos.especificaciones[0].texto: es obligatoria")));
        verifyNoInteractions(rfqService);
    }

    @Test
    void lecturaInternaYAccionesDeEstado() throws Exception {
        when(rfqService.buscar(any(), any(), any(), any())).thenReturn(new PageResponse<>(List.of(), 0, 20, 0, 0));
        when(rfqService.abrir(9L)).thenReturn(borrador);

        mvc.perform(get("/api/rfqs?estado=ABIERTA").header(HttpHeaders.AUTHORIZATION, bearer("APROBADOR")))
                .andExpect(status().isOk());
        mvc.perform(get("/api/rfqs").header(HttpHeaders.AUTHORIZATION, bearer("SOLICITANTE")))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/rfqs/9/abrir").header(HttpHeaders.AUTHORIZATION, bearer("COMPRADOR")))
                .andExpect(status().isOk());
        mvc.perform(post("/api/rfqs/9/cerrar").header(HttpHeaders.AUTHORIZATION, bearer("ADMIN_COMPRAS")))
                .andExpect(status().isForbidden());
    }

    @Test
    void portalSoloParaProveedores() throws Exception {
        when(rfqService.obtenerParaProveedor(9L)).thenReturn(new RfqPortalResponse(9L, "RFQ-000001", EstadoRfq.ABIERTA,
                LocalDateTime.now().plusDays(1), null, List.of()));
        when(rfqService.obtenerParaProveedor(8L)).thenThrow(new NoEncontradoException("RFQ", 8L));

        mvc.perform(get("/api/portal/rfqs/9").header(HttpHeaders.AUTHORIZATION, bearerProveedor(40L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.invitados").doesNotExist());
        mvc.perform(get("/api/portal/rfqs/8").header(HttpHeaders.AUTHORIZATION, bearerProveedor(40L)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/portal/rfqs").header(HttpHeaders.AUTHORIZATION, bearer("COMPRADOR")))
                .andExpect(status().isForbidden());
    }
}
