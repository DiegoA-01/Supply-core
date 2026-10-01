package com.proyecto.supply_core.empresa.controller;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.proyecto.supply_core.common.web.WebTestBase;
import com.proyecto.supply_core.empresa.dto.CentroCostoResponse;
import com.proyecto.supply_core.empresa.dto.EmpresaResponse;
import com.proyecto.supply_core.empresa.dto.PoliticaAplicableResponse;
import com.proyecto.supply_core.empresa.dto.SedeResponse;
import com.proyecto.supply_core.empresa.enums.TipoPolitica;
import com.proyecto.supply_core.empresa.service.CentroCostoService;
import com.proyecto.supply_core.empresa.service.EmpresaService;
import com.proyecto.supply_core.empresa.service.PoliticaAprobacionService;
import com.proyecto.supply_core.empresa.service.SedeService;
import com.proyecto.supply_core.usuario.entity.Rol;

@WebMvcTest({ EmpresaController.class, SedeController.class, CentroCostoController.class,
        PoliticaAprobacionController.class })
class EmpresaModuloControllerTest extends WebTestBase {

    @MockitoBean
    private EmpresaService empresaService;
    @MockitoBean
    private SedeService sedeService;
    @MockitoBean
    private CentroCostoService centroCostoService;
    @MockitoBean
    private PoliticaAprobacionService politicaService;

    @BeforeEach
    void rolesExistentes() {
        for (String codigo : List.of("APROBADOR", "ADMIN_COMPRAS", "PROVEEDOR")) {
            Rol r = new Rol();
            r.setCodigo(codigo);
            when(rolRepository.findByCodigo(codigo)).thenReturn(Optional.of(r));
        }
    }

    // ------------------------------------------------------------- permisos

    @Test
    void cualquierInternoConsultaPeroElProveedorNo() throws Exception {
        when(empresaService.obtener()).thenReturn(new EmpresaResponse(1L, "Demo", "900000000-1", null, "COP", null));
        when(sedeService.listar(any())).thenReturn(List.of(new SedeResponse(1L, "Principal", null, null, null, true)));

        mvc.perform(get("/api/empresa").header(HttpHeaders.AUTHORIZATION, bearer("SOLICITANTE")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.monedaBase").value("COP"));
        mvc.perform(get("/api/sedes?activo=true").header(HttpHeaders.AUTHORIZATION, bearer("ALMACENISTA")))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].nombre").value("Principal"));
        mvc.perform(get("/api/centros-costo").header(HttpHeaders.AUTHORIZATION, bearerProveedor(4L)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.mensaje").value("Esta información es solo para usuarios internos"));
        mvc.perform(get("/api/politicas-aprobacion").header(HttpHeaders.AUTHORIZATION, bearerProveedor(4L)))
                .andExpect(status().isForbidden());
    }

    @Test
    void soloAdminSistemaEditaEmpresaSedesYCentros() throws Exception {
        mvc.perform(put("/api/empresa").header(HttpHeaders.AUTHORIZATION, bearer("ADMIN_COMPRAS"))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"razonSocial":"X","nit":"900000000-1","monedaBase":"COP"}"""))
                .andExpect(status().isForbidden());
        mvc.perform(patch("/api/sedes/1/desactivar").header(HttpHeaders.AUTHORIZATION, bearer("COMPRADOR")))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/centros-costo").header(HttpHeaders.AUTHORIZATION, bearer("AUDITOR"))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"codigo":"ADM","nombre":"Adm"}"""))
                .andExpect(status().isForbidden());
        verifyNoInteractions(empresaService, sedeService, centroCostoService);
    }

    @Test
    void soloAdminComprasDefinePoliticas() throws Exception {
        mvc.perform(post("/api/politicas-aprobacion").header(HttpHeaders.AUTHORIZATION, bearer("ADMIN_SISTEMA"))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"tipo":"SOLICITUD","rolAprobador":"APROBADOR","montoDesde":0}"""))
                .andExpect(status().isForbidden());
        verifyNoInteractions(politicaService);
    }

    // ------------------------------------------------------------- validaciones

    @Test
    void empresaValidaNitYMoneda() throws Exception {
        mvc.perform(put("/api/empresa").header(HttpHeaders.AUTHORIZATION, bearer("ADMIN_SISTEMA"))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"razonSocial":"","nit":"90.000","monedaBase":"PESOS"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detalles", hasItems("razonSocial: es obligatoria",
                        "monedaBase: debe ser un código de 3 letras (ej. COP)")));
    }

    @Test
    void sedeExigeCoordenadasJuntasYEnRango() throws Exception {
        mvc.perform(post("/api/sedes").header(HttpHeaders.AUTHORIZATION, bearer("ADMIN_SISTEMA"))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"nombre":"Norte","latitud":95.5}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detalles", hasItems("latitud: debe estar entre -90 y 90",
                        "coordenadasCompletas: latitud y longitud deben enviarse juntas")));
    }

    @Test
    void centroCostoConCodigoRepetidoOFormatoMalo() throws Exception {
        when(centroCostoRepository.existsByCodigoIgnoreCase("ADM-01")).thenReturn(true);

        mvc.perform(post("/api/centros-costo").header(HttpHeaders.AUTHORIZATION, bearer("ADMIN_SISTEMA"))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"codigo":"adm-01","nombre":"Administración"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detalles", hasItem("codigo: ya está registrado por otro centro de costo")));
        mvc.perform(post("/api/centros-costo").header(HttpHeaders.AUTHORIZATION, bearer("ADMIN_SISTEMA"))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"codigo":"con espacios","nombre":"X"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detalles", hasItem("codigo: de 2 a 30 caracteres: letras, números, '-' o '_'")));
    }

    @Test
    void centroCostoValidoDevuelve201() throws Exception {
        when(centroCostoService.crear(any())).thenReturn(new CentroCostoResponse(1L, "ADM-01", "Administración", true));

        mvc.perform(post("/api/centros-costo").header(HttpHeaders.AUTHORIZATION, bearer("ADMIN_SISTEMA"))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"codigo":"adm-01","nombre":"Administración"}"""))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.codigo").value("ADM-01"));
    }

    @Test
    void politicaValidaRangoYRolInterno() throws Exception {
        mvc.perform(post("/api/politicas-aprobacion").header(HttpHeaders.AUTHORIZATION, bearer("ADMIN_COMPRAS"))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"tipo":"SOLICITUD","rolAprobador":"PROVEEDOR","montoDesde":500,"montoHasta":100}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detalles", hasItems(
                        "rolAprobador: debe ser un rol interno existente (no PROVEEDOR)",
                        "rangoValido: montoHasta debe ser mayor que montoDesde")));
        mvc.perform(post("/api/politicas-aprobacion").header(HttpHeaders.AUTHORIZATION, bearer("ADMIN_COMPRAS"))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"tipo":"INVENTADO","rolAprobador":"APROBADOR","montoDesde":0}"""))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(politicaService);
    }

    @Test
    void consultarPoliticaAplicable() throws Exception {
        when(politicaService.consultarAplicable(eq(TipoPolitica.SOLICITUD), any()))
                .thenReturn(new PoliticaAplicableResponse(false, null));

        mvc.perform(get("/api/politicas-aprobacion/aplicable?tipo=SOLICITUD&monto=250000.50")
                        .header(HttpHeaders.AUTHORIZATION, bearer("SOLICITANTE")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.requiereAprobacion").value(false));
        mvc.perform(get("/api/politicas-aprobacion/aplicable?tipo=SOLICITUD&monto=-5")
                        .header(HttpHeaders.AUTHORIZATION, bearer("SOLICITANTE")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detalles", hasItem("monto: no puede ser negativo")));
        mvc.perform(get("/api/politicas-aprobacion/aplicable?tipo=SOLICITUD")
                        .header(HttpHeaders.AUTHORIZATION, bearer("SOLICITANTE")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void montosSeRecibenComoDecimalesExactos() throws Exception {
        when(politicaService.consultarAplicable(eq(TipoPolitica.ADJUDICACION), eq(new BigDecimal("1000000.10"))))
                .thenReturn(new PoliticaAplicableResponse(false, null));

        mvc.perform(get("/api/politicas-aprobacion/aplicable?tipo=ADJUDICACION&monto=1000000.10")
                        .header(HttpHeaders.AUTHORIZATION, bearer("COMPRADOR")))
                .andExpect(status().isOk());
    }
}
