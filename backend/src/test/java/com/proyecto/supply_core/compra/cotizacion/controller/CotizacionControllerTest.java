package com.proyecto.supply_core.compra.cotizacion.controller;

import static org.hamcrest.Matchers.hasItem;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.proyecto.supply_core.common.exception.NegocioException;
import com.proyecto.supply_core.common.web.WebTestBase;
import com.proyecto.supply_core.compra.cotizacion.dto.CotizacionResponse;
import com.proyecto.supply_core.compra.cotizacion.enums.EstadoCotizacion;
import com.proyecto.supply_core.compra.cotizacion.enums.OrigenCotizacion;
import com.proyecto.supply_core.compra.cotizacion.service.CotizacionService;

@WebMvcTest({ PortalCotizacionController.class, CotizacionController.class })
class CotizacionControllerTest extends WebTestBase {

    @MockitoBean
    private CotizacionService cotizacionService;

    private final CotizacionResponse borrador = new CotizacionResponse(77L, 9L, 40L, "Tech SAS",
            EstadoCotizacion.BORRADOR, OrigenCotizacion.FORMULARIO, "COP", 5, 12, null, null, null, null, null, null,
            null, false, List.of());

    @Test
    void guardarValidaLosCamposDelFormulario() throws Exception {
        mvc.perform(put("/api/portal/rfqs/9/cotizacion").header(HttpHeaders.AUTHORIZATION, bearerProveedor(40L))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"moneda":"cop","plazoEntregaDias":-1,"lineas":[
                                  {"rfqLineaId":500,"cantidadOfertada":0,"unidadOfertada":"","factorConversion":0,
                                   "precioUnitarioOfertado":-5,"descuentoPct":150},
                                  {"rfqLineaId":500,"cantidadOfertada":1,"unidadOfertada":"und","factorConversion":1,
                                   "precioUnitarioOfertado":1}]}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detalles", hasItem("moneda: debe ser un código de 3 letras en mayúscula (ej. COP)")))
                .andExpect(jsonPath("$.detalles", hasItem("plazoEntregaDias: no puede ser negativo")))
                .andExpect(jsonPath("$.detalles", hasItem("lineas[0].cantidadOfertada: debe ser mayor que cero")))
                .andExpect(jsonPath("$.detalles", hasItem("lineas[0].factorConversion: debe ser mayor que cero")))
                .andExpect(jsonPath("$.detalles", hasItem("lineas[0].precioUnitarioOfertado: no puede ser negativo")))
                .andExpect(jsonPath("$.detalles", hasItem("lineas[0].descuentoPct: no puede ser mayor que 100")))
                .andExpect(jsonPath("$.detalles",
                        hasItem("lineasSinRepetir: no puede cotizar dos veces la misma línea de la RFQ")));
        verifyNoInteractions(cotizacionService);
    }

    @Test
    void portalSoloParaProveedores() throws Exception {
        when(cotizacionService.guardar(eq(9L), any())).thenReturn(borrador);

        mvc.perform(put("/api/portal/rfqs/9/cotizacion").header(HttpHeaders.AUTHORIZATION, bearerProveedor(40L))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"plazoEntregaDias\":5}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("BORRADOR"));
        mvc.perform(put("/api/portal/rfqs/9/cotizacion").header(HttpHeaders.AUTHORIZATION, bearer("COMPRADOR"))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void subirArchivoYEnviar() throws Exception {
        when(cotizacionService.subirArchivo(eq(9L), any())).thenReturn(borrador);
        when(cotizacionService.enviar(9L))
                .thenThrow(new NegocioException("RFQ_NO_RECIBE_COTIZACIONES", "La RFQ-000009 ya no recibe cotizaciones"));

        mvc.perform(multipart("/api/portal/rfqs/9/cotizacion/archivo")
                        .file(new MockMultipartFile("archivo", "c.pdf", "application/pdf", "%PDF-1.7".getBytes()))
                        .header(HttpHeaders.AUTHORIZATION, bearerProveedor(40L)))
                .andExpect(status().isOk());
        mvc.perform(post("/api/portal/rfqs/9/cotizacion/enviar").header(HttpHeaders.AUTHORIZATION, bearerProveedor(40L)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("RFQ_NO_RECIBE_COTIZACIONES"));
    }

    @Test
    void comprasVeLasCotizacionesDeLaRfq() throws Exception {
        when(cotizacionService.deRfq(9L)).thenReturn(List.of(borrador));

        mvc.perform(get("/api/rfqs/9/cotizaciones").header(HttpHeaders.AUTHORIZATION, bearer("COMPRADOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].proveedorRazonSocial").value("Tech SAS"));
        mvc.perform(get("/api/rfqs/9/cotizaciones").header(HttpHeaders.AUTHORIZATION, bearerProveedor(40L)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/rfqs/9/cotizaciones").header(HttpHeaders.AUTHORIZATION, bearer("SOLICITANTE")))
                .andExpect(status().isForbidden());
    }
}
