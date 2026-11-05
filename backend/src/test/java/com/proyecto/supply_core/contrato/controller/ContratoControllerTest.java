package com.proyecto.supply_core.contrato.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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

import com.proyecto.supply_core.common.dto.PageResponse;
import com.proyecto.supply_core.common.web.WebTestBase;
import com.proyecto.supply_core.contrato.dto.ContratoResponse;
import com.proyecto.supply_core.contrato.dto.ContratoResumenResponse;
import com.proyecto.supply_core.contrato.dto.OfertaContratoResponse;
import com.proyecto.supply_core.contrato.enums.EstadoContrato;
import com.proyecto.supply_core.contrato.service.ContratoService;

@WebMvcTest(ContratoController.class)
class ContratoControllerTest extends WebTestBase {

    @MockitoBean
    private ContratoService contratoService;

    private static final LocalDate DESDE = LocalDate.now();
    private static final LocalDate HASTA = DESDE.plusMonths(6);

    private static String cuerpo(String precios) {
        return "{\"proveedorId\":40,\"numero\":\"CT-1\",\"descripcion\":\"Cables\",\"vigenteDesde\":\"" + DESDE
                + "\",\"vigenteHasta\":\"" + HASTA + "\",\"precios\":" + precios + "}";
    }

    private static final String PRECIO_BUENO = "[{\"productoId\":100,\"precioUnitario\":95000,\"impuestoPct\":19,\"cantidadMaxima\":50}]";

    private static ContratoResponse respuesta() {
        return new ContratoResponse(5L, "CT-1", "Cables", 40L, "Tech SAS", DESDE, HASTA, EstadoContrato.ACTIVO, true,
                null, null, List.of());
    }

    @Test
    void soloAdminComprasCreaYElProductoDebeEstarActivo() throws Exception {
        when(contratoService.crear(any())).thenReturn(respuesta());
        when(productoRepository.existsByIdAndActivoTrue(100L)).thenReturn(true);

        mvc.perform(post("/api/contratos").contentType(MediaType.APPLICATION_JSON).content(cuerpo(PRECIO_BUENO))
                        .header(HttpHeaders.AUTHORIZATION, bearer("ADMIN_COMPRAS")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.numero").value("CT-1"));
        mvc.perform(post("/api/contratos").contentType(MediaType.APPLICATION_JSON).content(cuerpo(PRECIO_BUENO))
                        .header(HttpHeaders.AUTHORIZATION, bearer("COMPRADOR")))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/contratos").contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(PRECIO_BUENO.replace("100", "999")))
                        .header(HttpHeaders.AUTHORIZATION, bearer("ADMIN_COMPRAS")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void datosInvalidosSeRechazanAntesDelService() throws Exception {
        when(productoRepository.existsByIdAndActivoTrue(100L)).thenReturn(true);
        String[] malos = {
                cuerpo("[]"),
                cuerpo("[{\"productoId\":100,\"precioUnitario\":0,\"impuestoPct\":19}]"),
                cuerpo("[{\"productoId\":100,\"precioUnitario\":10,\"impuestoPct\":101}]"),
                cuerpo("[{\"productoId\":100,\"precioUnitario\":10,\"impuestoPct\":19,\"cantidadMaxima\":-1}]"),
                cuerpo("[{\"productoId\":100,\"precioUnitario\":10,\"impuestoPct\":19},"
                        + "{\"productoId\":100,\"precioUnitario\":12,\"impuestoPct\":19}]"),
                cuerpo(PRECIO_BUENO).replace("\"numero\":\"CT-1\"", "\"numero\":\" \""),
                cuerpo(PRECIO_BUENO).replace(HASTA.toString(), DESDE.minusDays(1).toString()),
                cuerpo(PRECIO_BUENO).replace("\"proveedorId\":40", "\"proveedorId\":0") };
        for (String malo : malos) {
            mvc.perform(post("/api/contratos").contentType(MediaType.APPLICATION_JSON).content(malo)
                            .header(HttpHeaders.AUTHORIZATION, bearer("ADMIN_COMPRAS")))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(contratoService);
    }

    @Test
    void vigenciaYAnulacionSonDeAdminComprasYValidan() throws Exception {
        when(contratoService.cambiarVigencia(eq(5L), any())).thenReturn(respuesta());
        when(contratoService.anular(eq(5L), any())).thenReturn(respuesta());

        mvc.perform(patch("/api/contratos/5/vigencia").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"vigenteHasta\":\"" + HASTA.plusDays(30) + "\"}")
                        .header(HttpHeaders.AUTHORIZATION, bearer("ADMIN_COMPRAS")))
                .andExpect(status().isOk());
        verify(contratoService).cambiarVigencia(5L, HASTA.plusDays(30));
        mvc.perform(patch("/api/contratos/5/anular").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"motivo\":\"Cambió el proveedor\"}")
                        .header(HttpHeaders.AUTHORIZATION, bearer("ADMIN_COMPRAS")))
                .andExpect(status().isOk());
        mvc.perform(patch("/api/contratos/5/anular").contentType(MediaType.APPLICATION_JSON).content("{\"motivo\":\"no\"}")
                        .header(HttpHeaders.AUTHORIZATION, bearer("ADMIN_COMPRAS")))
                .andExpect(status().isBadRequest());
        mvc.perform(patch("/api/contratos/5/vigencia").contentType(MediaType.APPLICATION_JSON).content("{}")
                        .header(HttpHeaders.AUTHORIZATION, bearer("ADMIN_COMPRAS")))
                .andExpect(status().isBadRequest());
        mvc.perform(patch("/api/contratos/0/anular").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"motivo\":\"Cambió el proveedor\"}")
                        .header(HttpHeaders.AUTHORIZATION, bearer("ADMIN_COMPRAS")))
                .andExpect(status().isBadRequest());
        mvc.perform(patch("/api/contratos/5/anular").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"motivo\":\"Cambió el proveedor\"}")
                        .header(HttpHeaders.AUTHORIZATION, bearer("COMPRADOR")))
                .andExpect(status().isForbidden());
    }

    @Test
    void lasConsultasLasVenLosRolesDeGestionYNoElSolicitanteNiElProveedor() throws Exception {
        when(contratoService.obtener(5L)).thenReturn(respuesta());
        when(contratoService.buscar(eq(40L), eq(EstadoContrato.ACTIVO), eq(true), any()))
                .thenReturn(new PageResponse<>(List.of(new ContratoResumenResponse(5L, "CT-1", 40L, "Tech SAS", DESDE,
                        HASTA, EstadoContrato.ACTIVO, true)), 0, 20, 1L, 1));

        mvc.perform(get("/api/contratos/5").header(HttpHeaders.AUTHORIZATION, bearer("AUDITOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.proveedorRazonSocial").value("Tech SAS"));
        mvc.perform(get("/api/contratos?proveedorId=40&estado=ACTIVO&vigente=true")
                        .header(HttpHeaders.AUTHORIZATION, bearer("COMPRADOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido[0].numero").value("CT-1"));
        mvc.perform(get("/api/contratos").header(HttpHeaders.AUTHORIZATION, bearer("SOLICITANTE")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/contratos/5").header(HttpHeaders.AUTHORIZATION, bearerProveedor(40L)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/contratos/5")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/contratos/0").header(HttpHeaders.AUTHORIZATION, bearer("AUDITOR")))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/contratos?estado=OTRO").header(HttpHeaders.AUTHORIZATION, bearer("AUDITOR")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void lasOfertasSonDeCompradorYAdminComprasYPidenLaSolicitud() throws Exception {
        when(contratoService.ofertas(7L)).thenReturn(List.of(new OfertaContratoResponse(5L, "CT-1", 40L, "Tech SAS", HASTA,
                true, List.of(), true, true, true, new BigDecimal("11000.00"), new BigDecimal("1900.00"),
                new BigDecimal("12900.00"), new BigDecimal("20000.00"), List.of())));

        mvc.perform(get("/api/contratos/ofertas?solicitudId=7").header(HttpHeaders.AUTHORIZATION, bearer("COMPRADOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].puedeEmitirse").value(true))
                .andExpect(jsonPath("$[0].total").value(12900.00));
        mvc.perform(get("/api/contratos/ofertas?solicitudId=7").header(HttpHeaders.AUTHORIZATION, bearer("AUDITOR")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/contratos/ofertas").header(HttpHeaders.AUTHORIZATION, bearer("COMPRADOR")))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/contratos/ofertas?solicitudId=0").header(HttpHeaders.AUTHORIZATION, bearer("COMPRADOR")))
                .andExpect(status().isBadRequest());
    }
}
