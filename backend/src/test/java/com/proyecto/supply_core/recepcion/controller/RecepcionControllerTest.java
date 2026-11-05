package com.proyecto.supply_core.recepcion.controller;

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
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.proyecto.supply_core.common.exception.NegocioException;
import com.proyecto.supply_core.common.web.WebTestBase;
import com.proyecto.supply_core.compra.orden.enums.EstadoOrden;
import com.proyecto.supply_core.recepcion.dto.LineaRecepcionResponse;
import com.proyecto.supply_core.recepcion.dto.RecepcionResponse;
import com.proyecto.supply_core.recepcion.dto.ResumenRecepcionResponse;
import com.proyecto.supply_core.recepcion.enums.DecisionExceso;
import com.proyecto.supply_core.recepcion.enums.EstadoRecepcionLinea;
import com.proyecto.supply_core.recepcion.service.RecepcionService;

@WebMvcTest({ RecepcionController.class, PortalRecepcionController.class })
class RecepcionControllerTest extends WebTestBase {

    private static final String RECEPCION_VALIDA = """
            {"lineas":[{"ordenLineaId":5,"cantidadRecibida":10,"cantidadRechazada":2,"motivoRechazo":"DANADO"}]}""";

    @MockitoBean
    private RecepcionService recepcionService;

    private final LineaRecepcionResponse linea = new LineaRecepcionResponse(1L, 5L, 11L, "CAB-1", "Cable",
            BigDecimal.TEN, BigDecimal.valueOf(2), BigDecimal.valueOf(8), BigDecimal.ZERO, BigDecimal.valueOf(2),
            EstadoRecepcionLinea.ACEPTADA, null, null, null, null, null);
    private final RecepcionResponse recepcion = new RecepcionResponse(9L, 50L, "OC-000050", 3L, "Central", 7L,
            LocalDateTime.of(2026, 10, 23, 10, 0), null, List.of(linea));
    private final ResumenRecepcionResponse resumen = new ResumenRecepcionResponse(50L, "OC-000050",
            EstadoOrden.RECIBIDA_PARCIAL, List.of(), List.of(recepcion));

    @Test
    void elAlmacenistaRegistraUnaRecepcionYDevuelve201() throws Exception {
        when(recepcionService.registrar(eq(50L), any())).thenReturn(recepcion);

        mvc.perform(post("/api/ordenes/50/recepciones").header(HttpHeaders.AUTHORIZATION, bearer("ALMACENISTA"))
                        .contentType(MediaType.APPLICATION_JSON).content(RECEPCION_VALIDA))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(9))
                .andExpect(jsonPath("$.lineas[0].cantidadRechazada").value(2));
    }

    @Test
    void soloElAlmacenistaRegistraRecepciones() throws Exception {
        for (String rol : new String[] { "COMPRADOR", "ADMIN_COMPRAS", "ADMINISTRATIVO", "AUDITOR" }) {
            mvc.perform(post("/api/ordenes/50/recepciones").header(HttpHeaders.AUTHORIZATION, bearer(rol))
                            .contentType(MediaType.APPLICATION_JSON).content(RECEPCION_VALIDA))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(post("/api/ordenes/50/recepciones").contentType(MediaType.APPLICATION_JSON).content(RECEPCION_VALIDA))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(recepcionService);
    }

    @Test
    void sinLineasOConRechazoSinMotivoDevuelve400() throws Exception {
        mvc.perform(post("/api/ordenes/50/recepciones").header(HttpHeaders.AUTHORIZATION, bearer("ALMACENISTA"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"lineas\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"));

        mvc.perform(post("/api/ordenes/50/recepciones").header(HttpHeaders.AUTHORIZATION, bearer("ALMACENISTA"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"lineas":[{"ordenLineaId":5,"cantidadRecibida":10,"cantidadRechazada":2}]}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detalles[*]", hasItem(org.hamcrest.Matchers.containsString("debe indicar el motivo"))));
        verifyNoInteractions(recepcionService);
    }

    @Test
    void rechazarMasDeLoRecibidoOLineasRepetidasDevuelve400() throws Exception {
        mvc.perform(post("/api/ordenes/50/recepciones").header(HttpHeaders.AUTHORIZATION, bearer("ALMACENISTA"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"lineas":[{"ordenLineaId":5,"cantidadRecibida":2,"cantidadRechazada":3,"motivoRechazo":"VENCIDO"}]}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detalles[*]", hasItem(org.hamcrest.Matchers.containsString("no puede superar lo recibido"))));

        mvc.perform(post("/api/ordenes/50/recepciones").header(HttpHeaders.AUTHORIZATION, bearer("ALMACENISTA"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"lineas":[{"ordenLineaId":5,"cantidadRecibida":1},{"ordenLineaId":5,"cantidadRecibida":2}]}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detalles[*]", hasItem(org.hamcrest.Matchers.containsString("no puede repetir"))));
    }

    @Test
    void laReglaDeNegocioDelServiceSeTraduceAConflicto() throws Exception {
        when(recepcionService.registrar(eq(50L), any()))
                .thenThrow(new NegocioException("ORDEN_NO_RECIBIBLE", "La orden está EMITIDA"));

        mvc.perform(post("/api/ordenes/50/recepciones").header(HttpHeaders.AUTHORIZATION, bearer("ALMACENISTA"))
                        .contentType(MediaType.APPLICATION_JSON).content(RECEPCION_VALIDA))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("ORDEN_NO_RECIBIBLE"));
    }

    @Test
    void internosConsultanElResumenYElDetalle() throws Exception {
        when(recepcionService.deOrden(50L)).thenReturn(resumen);
        when(recepcionService.obtener(9L)).thenReturn(recepcion);

        for (String rol : new String[] { "ALMACENISTA", "COMPRADOR", "ADMIN_COMPRAS", "ADMINISTRATIVO", "APROBADOR", "AUDITOR" }) {
            mvc.perform(get("/api/ordenes/50/recepciones").header(HttpHeaders.AUTHORIZATION, bearer(rol)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.ordenConsecutivo").value("OC-000050"));
        }
        mvc.perform(get("/api/recepciones/9").header(HttpHeaders.AUTHORIZATION, bearer("AUDITOR")))
                .andExpect(status().isOk());
        mvc.perform(get("/api/ordenes/50/recepciones").header(HttpHeaders.AUTHORIZATION, bearer("SOLICITANTE")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/recepciones/0").header(HttpHeaders.AUTHORIZATION, bearer("AUDITOR")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void cerrarLoHaceAlmacenOCompras() throws Exception {
        when(recepcionService.cerrar(50L, "Sin más stock")).thenReturn(resumen);

        mvc.perform(post("/api/ordenes/50/recepciones/cerrar").header(HttpHeaders.AUTHORIZATION, bearer("ADMIN_COMPRAS"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"motivo\":\"Sin más stock\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/ordenes/50/recepciones/cerrar").header(HttpHeaders.AUTHORIZATION, bearer("COMPRADOR"))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void elExcedenteLoResuelveCompra() throws Exception {
        when(recepcionService.resolverExceso(eq(1L), eq(DecisionExceso.ACEPTAR), any())).thenReturn(linea);

        mvc.perform(post("/api/recepciones/lineas/1/resolver-exceso").header(HttpHeaders.AUTHORIZATION, bearer("COMPRADOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"ACEPTAR\",\"motivo\":\"Nos sirve para la obra\"}"))
                .andExpect(status().isOk());
        verify(recepcionService).resolverExceso(1L, DecisionExceso.ACEPTAR, "Nos sirve para la obra");

        mvc.perform(post("/api/recepciones/lineas/1/resolver-exceso").header(HttpHeaders.AUTHORIZATION, bearer("ALMACENISTA"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"ACEPTAR\",\"motivo\":\"Nos sirve para la obra\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/recepciones/lineas/1/resolver-exceso").header(HttpHeaders.AUTHORIZATION, bearer("COMPRADOR"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"decision\":\"ACEPTAR\",\"motivo\":\"x\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void elProveedorVeLasRecepcionesDeSusOrdenesYNadieMas() throws Exception {
        when(recepcionService.deOrdenDelProveedor(50L)).thenReturn(resumen);

        mvc.perform(get("/api/portal/ordenes/50/recepciones").header(HttpHeaders.AUTHORIZATION, bearerProveedor(40L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estadoOrden").value("RECIBIDA_PARCIAL"));
        mvc.perform(get("/api/portal/ordenes/50/recepciones").header(HttpHeaders.AUTHORIZATION, bearer("COMPRADOR")))
                .andExpect(status().isForbidden());
    }
}
