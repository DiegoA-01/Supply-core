package com.proyecto.supply_core.catalogo.controller;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.proyecto.supply_core.catalogo.dto.CategoriaResponse;
import com.proyecto.supply_core.catalogo.dto.ConversionResponse;
import com.proyecto.supply_core.catalogo.dto.UnidadMedidaResponse;
import com.proyecto.supply_core.catalogo.enums.Magnitud;
import com.proyecto.supply_core.catalogo.service.CategoriaService;
import com.proyecto.supply_core.catalogo.service.UnidadMedidaService;
import com.proyecto.supply_core.common.exception.NegocioException;
import com.proyecto.supply_core.common.web.WebTestBase;

@WebMvcTest({ CategoriaController.class, UnidadMedidaController.class })
class CatalogoControllerTest extends WebTestBase {

    @MockitoBean
    private CategoriaService categoriaService;
    @MockitoBean
    private UnidadMedidaService unidadService;

    @Test
    void internosConsultanYElProveedorNo() throws Exception {
        when(categoriaService.listar(any())).thenReturn(List.of(new CategoriaResponse(1L, "Tecnología", true)));
        when(unidadService.listar(any())).thenReturn(List.of(
                new UnidadMedidaResponse(2L, "DOC", "Docena", Magnitud.CANTIDAD, new BigDecimal("12"), true)));

        mvc.perform(get("/api/categorias?activo=true").header(HttpHeaders.AUTHORIZATION, bearer("SOLICITANTE")))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].nombre").value("Tecnología"));
        mvc.perform(get("/api/unidades-medida").header(HttpHeaders.AUTHORIZATION, bearer("ALMACENISTA")))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].factorConversionBase").value(12));
        mvc.perform(get("/api/unidades-medida").header(HttpHeaders.AUTHORIZATION, bearerProveedor(4L)))
                .andExpect(status().isForbidden());
    }

    @Test
    void soloAdminComprasYAdminSistemaModifican() throws Exception {
        mvc.perform(post("/api/categorias").header(HttpHeaders.AUTHORIZATION, bearer("COMPRADOR"))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"nombre":"Químicos"}"""))
                .andExpect(status().isForbidden());
        mvc.perform(patch("/api/unidades-medida/2/desactivar").header(HttpHeaders.AUTHORIZATION, bearer("AUDITOR")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(categoriaService, unidadService);

        when(categoriaService.crear(any())).thenReturn(new CategoriaResponse(9L, "Químicos", true));
        mvc.perform(post("/api/categorias").header(HttpHeaders.AUTHORIZATION, bearer("ADMIN_SISTEMA"))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"nombre":"Químicos"}"""))
                .andExpect(status().isCreated());
    }

    @Test
    void categoriaConNombreRepetidoDevuelve400() throws Exception {
        when(categoriaRepository.existsByNombreIgnoreCase("Tecnología")).thenReturn(true);

        mvc.perform(post("/api/categorias").header(HttpHeaders.AUTHORIZATION, bearer("ADMIN_COMPRAS"))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"nombre":"Tecnología"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detalles", hasItem("nombre: ya existe una categoría con ese nombre")));
    }

    @Test
    void unidadValidaCodigoMagnitudYFactor() throws Exception {
        when(unidadMedidaRepository.existsByCodigoIgnoreCase("DOC")).thenReturn(true);

        mvc.perform(post("/api/unidades-medida").header(HttpHeaders.AUTHORIZATION, bearer("ADMIN_COMPRAS"))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"codigo":"doc","nombre":"Otra docena","factorConversionBase":0}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detalles", hasItems(
                        "codigo: ya está registrado por otra unidad",
                        "magnitud: es obligatoria",
                        "factorConversionBase: debe ser mayor que cero")));
        mvc.perform(post("/api/unidades-medida").header(HttpHeaders.AUTHORIZATION, bearer("ADMIN_COMPRAS"))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"codigo":"caja x 6","nombre":"Caja","magnitud":"CANTIDAD","factorConversionBase":6.1234567}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detalles", hasItems(
                        "codigo: de 1 a 20 letras o números, sin espacios",
                        "factorConversionBase: máximo 6 decimales")));
    }

    @Test
    void convertirDevuelveLaEquivalencia() throws Exception {
        when(unidadService.consultarConversion(eq(new BigDecimal("2")), eq(2L), eq(1L)))
                .thenReturn(new ConversionResponse(new BigDecimal("2"), "DOC", new BigDecimal("24.0000"), "UND"));

        mvc.perform(get("/api/unidades-medida/convertir?cantidad=2&desde=2&hacia=1")
                        .header(HttpHeaders.AUTHORIZATION, bearer("COMPRADOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultado").value(24.0))
                .andExpect(jsonPath("$.unidadDestino").value("UND"));
    }

    @Test
    void convertirValidaParametrosYMagnitud() throws Exception {
        mvc.perform(get("/api/unidades-medida/convertir?cantidad=-1&desde=0&hacia=1")
                        .header(HttpHeaders.AUTHORIZATION, bearer("COMPRADOR")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detalles", hasItems("cantidad: no puede ser negativa", "desde: debe ser un id válido")));

        when(unidadService.consultarConversion(any(), eq(4L), eq(6L)))
                .thenThrow(new NegocioException("UNIDADES_INCOMPATIBLES", "No se puede convertir KG (MASA) a LT (VOLUMEN)"));
        mvc.perform(get("/api/unidades-medida/convertir?cantidad=1&desde=4&hacia=6")
                        .header(HttpHeaders.AUTHORIZATION, bearer("COMPRADOR")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("UNIDADES_INCOMPATIBLES"));
    }
}
