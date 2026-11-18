package com.proyecto.supply_core.compra.solicitud.controller;

import static org.hamcrest.Matchers.hasItem;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.proyecto.supply_core.common.dto.PageResponse;
import com.proyecto.supply_core.common.exception.AccesoDenegadoException;
import com.proyecto.supply_core.common.web.WebTestBase;
import com.proyecto.supply_core.compra.solicitud.dto.SolicitudResponse;
import com.proyecto.supply_core.compra.solicitud.enums.EstadoSolicitud;
import com.proyecto.supply_core.compra.solicitud.service.AprobacionSolicitudService;
import com.proyecto.supply_core.compra.solicitud.service.SolicitudService;

@WebMvcTest(SolicitudController.class)
class SolicitudControllerTest extends WebTestBase {

    @MockitoBean
    private SolicitudService solicitudService;

    @MockitoBean
    private AprobacionSolicitudService aprobacionService;

    private final SolicitudResponse borrador = new SolicitudResponse(1L, "SOL-000001", EstadoSolicitud.BORRADOR, 5L,
            10L, "Papelería", LocalDate.now(), null, false, null, false, null, null, null, null, List.of(), List.of(), false);

    @BeforeEach
    void catalogosActivos() {
        when(productoRepository.existsByIdAndActivoTrue(anyLong())).thenAnswer(i -> i.<Long>getArgument(0) == 100L);
        when(unidadMedidaRepository.existsByIdAndActivoTrue(anyLong())).thenAnswer(i -> i.<Long>getArgument(0) == 1L);
    }

    @Test
    void crearSoloSolicitanteYConValidaciones() throws Exception {
        when(solicitudService.crear(any())).thenReturn(borrador);
        String valido = """
                {"motivo":"Papelería","fechaRequerida":"%s","lineas":[{"productoId":100,"unidadMedidaId":1,"cantidad":5}]}"""
                .formatted(LocalDate.now().plusDays(3));

        mvc.perform(post("/api/solicitudes").header(HttpHeaders.AUTHORIZATION, bearer("SOLICITANTE"))
                        .contentType(MediaType.APPLICATION_JSON).content(valido))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.consecutivo").value("SOL-000001"));
        mvc.perform(post("/api/solicitudes").header(HttpHeaders.AUTHORIZATION, bearer("COMPRADOR"))
                        .contentType(MediaType.APPLICATION_JSON).content(valido))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/solicitudes").header(HttpHeaders.AUTHORIZATION, bearerProveedor(40L))
                        .contentType(MediaType.APPLICATION_JSON).content(valido))
                .andExpect(status().isForbidden());
    }

    @Test
    void lineasInvalidasDevuelven400ConElCampo() throws Exception {
        String invalido = """
                {"motivo":"","fechaRequerida":"2000-01-01","lineas":[
                  {"productoId":999,"unidadMedidaId":1,"cantidad":0},
                  {"productoId":100,"unidadMedidaId":7,"cantidad":1},
                  {"productoId":100,"unidadMedidaId":1,"cantidad":2}]}""";

        mvc.perform(post("/api/solicitudes").header(HttpHeaders.AUTHORIZATION, bearer("SOLICITANTE"))
                        .contentType(MediaType.APPLICATION_JSON).content(invalido))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detalles", hasItem("motivo: es obligatorio")))
                .andExpect(jsonPath("$.detalles", hasItem("fechaRequerida: no puede ser una fecha pasada")))
                .andExpect(jsonPath("$.detalles", hasItem("lineas[0].productoId: no existe o está inactivo")))
                .andExpect(jsonPath("$.detalles", hasItem("lineas[0].cantidad: debe ser mayor que cero")))
                .andExpect(jsonPath("$.detalles", hasItem("lineas[1].unidadMedidaId: no existe o está inactiva")))
                .andExpect(jsonPath("$.detalles",
                        hasItem("lineasSinRepetir: no puede repetir un producto; sume la cantidad en una sola línea")));
        verifyNoInteractions(solicitudService);
    }

    @Test
    void decisionValidaObservacionesYErroresDelService() throws Exception {
        mvc.perform(post("/api/solicitudes/1/decision").header(HttpHeaders.AUTHORIZATION, bearer("APROBADOR"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"decision\":\"RECHAZADA\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detalles",
                        hasItem("observacionesPresentes: son obligatorias al rechazar o devolver")));

        when(aprobacionService.decidir(eq(1L), any()))
                .thenThrow(new AccesoDenegadoException("No puede aprobar su propia solicitud (segregación de funciones)"));
        mvc.perform(post("/api/solicitudes/1/decision").header(HttpHeaders.AUTHORIZATION, bearer("APROBADOR"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"decision\":\"APROBADA\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/solicitudes/1/decision").header(HttpHeaders.AUTHORIZATION, bearer("APROBADOR"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"decision\":\"QUIZAS\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void consultasParaInternosYCancelarConRol() throws Exception {
        when(aprobacionService.pendientes(any())).thenReturn(new PageResponse<>(List.of(), 0, 20, 0, 0));
        when(solicitudService.cancelar(1L)).thenReturn(borrador);

        mvc.perform(get("/api/solicitudes/pendientes-aprobacion").header(HttpHeaders.AUTHORIZATION, bearer("APROBADOR")))
                .andExpect(status().isOk());
        mvc.perform(get("/api/solicitudes").header(HttpHeaders.AUTHORIZATION, bearerProveedor(40L)))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/solicitudes/1/cancelar").header(HttpHeaders.AUTHORIZATION, bearer("ADMIN_COMPRAS")))
                .andExpect(status().isOk());
        mvc.perform(post("/api/solicitudes/1/cancelar").header(HttpHeaders.AUTHORIZATION, bearer("AUDITOR")))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/solicitudes/0/enviar").header(HttpHeaders.AUTHORIZATION, bearer("SOLICITANTE")))
                .andExpect(status().isBadRequest());
    }
}
