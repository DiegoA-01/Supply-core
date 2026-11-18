package com.proyecto.supply_core.despacho.controller;

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

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.proyecto.supply_core.common.dto.PageResponse;
import com.proyecto.supply_core.common.exception.NegocioException;
import com.proyecto.supply_core.common.web.WebTestBase;
import com.proyecto.supply_core.despacho.dto.DespachoResponse;
import com.proyecto.supply_core.despacho.enums.EstadoDespacho;
import com.proyecto.supply_core.despacho.service.DespachoService;

@WebMvcTest(DespachoController.class)
class DespachoControllerTest extends WebTestBase {

    @MockitoBean
    private DespachoService despachoService;

    private final DespachoResponse borrador = new DespachoResponse(5L, "DES-000001", EstadoDespacho.BORRADOR, 1L,
            "Principal", 7L, null, null, null, null, null, null, null, List.of(), List.of());

    @BeforeEach
    void catalogosActivos() {
        when(productoRepository.existsByIdAndActivoTrue(anyLong())).thenAnswer(i -> i.<Long>getArgument(0) == 200L);
        when(unidadMedidaRepository.existsByIdAndActivoTrue(anyLong())).thenReturn(true);
    }

    @Test
    void crearSoloAlmacenistaYValidado() throws Exception {
        when(despachoService.crear(any())).thenReturn(borrador);
        String valido = """
                {"bodegaId":1,"cliente":"Obra calle 10","lineas":[{"productoId":200,"unidadMedidaId":12,"cantidad":5}]}""";

        mvc.perform(post("/api/despachos").header(HttpHeaders.AUTHORIZATION, bearer("ALMACENISTA"))
                        .contentType(MediaType.APPLICATION_JSON).content(valido))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.consecutivo").value("DES-000001"));
        mvc.perform(post("/api/despachos").header(HttpHeaders.AUTHORIZATION, bearer("COMPRADOR"))
                        .contentType(MediaType.APPLICATION_JSON).content(valido))
                .andExpect(status().isForbidden());

        mvc.perform(post("/api/despachos").header(HttpHeaders.AUTHORIZATION, bearer("ALMACENISTA"))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"lineas":[{"productoId":999,"unidadMedidaId":12,"cantidad":-1},
                                           {"productoId":200,"unidadMedidaId":12,"cantidad":1},
                                           {"productoId":200,"unidadMedidaId":12,"cantidad":2}]}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detalles", hasItem("bodegaId: es obligatoria")))
                .andExpect(jsonPath("$.detalles", hasItem("lineas[0].productoId: no existe o está inactivo")))
                .andExpect(jsonPath("$.detalles", hasItem("lineas[0].cantidad: debe ser mayor que cero")))
                .andExpect(jsonPath("$.detalles",
                        hasItem("lineasSinRepetir: no puede repetir un producto; sume la cantidad en una sola línea")));
    }

    @Test
    void confirmarSinStockDevuelve409() throws Exception {
        when(despachoService.confirmar(5L))
                .thenThrow(new NegocioException("STOCK_INSUFICIENTE", "Stock insuficiente de CEM-50 en Principal"));

        mvc.perform(post("/api/despachos/5/confirmar").header(HttpHeaders.AUTHORIZATION, bearer("ALMACENISTA")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("STOCK_INSUFICIENTE"));
    }

    @Test
    void anularExigeMotivoYRol() throws Exception {
        mvc.perform(post("/api/despachos/5/anular").header(HttpHeaders.AUTHORIZATION, bearer("ALMACENISTA"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"motivo\":\"no\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detalles", hasItem("motivo: entre 5 y 500 caracteres")));
        mvc.perform(post("/api/despachos/5/anular").header(HttpHeaders.AUTHORIZATION, bearer("AUDITOR"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"motivo\":\"Devolución del cliente\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(despachoService);

        when(despachoService.anular(eq(5L), any())).thenReturn(borrador);
        mvc.perform(post("/api/despachos/5/anular").header(HttpHeaders.AUTHORIZATION, bearer("ADMIN_COMPRAS"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"motivo\":\"Devolución del cliente\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void consultaParaInternosNoParaProveedor() throws Exception {
        when(despachoService.buscar(any(), any(), any(), any())).thenReturn(new PageResponse<>(List.of(), 0, 20, 0, 0));

        mvc.perform(get("/api/despachos?estado=CONFIRMADO").header(HttpHeaders.AUTHORIZATION, bearer("AUDITOR")))
                .andExpect(status().isOk());
        mvc.perform(get("/api/despachos").header(HttpHeaders.AUTHORIZATION, bearerProveedor(40L)))
                .andExpect(status().isForbidden());
    }
}
