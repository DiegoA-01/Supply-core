package com.proyecto.supply_core.plataforma.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.proyecto.supply_core.common.dto.PageResponse;
import com.proyecto.supply_core.common.web.WebTestBase;
import com.proyecto.supply_core.plataforma.dto.AuditoriaPlataformaResponse;
import com.proyecto.supply_core.plataforma.dto.EmpresaDetalleResponse;
import com.proyecto.supply_core.plataforma.dto.EmpresaPlataformaResponse;
import com.proyecto.supply_core.plataforma.dto.PlanDtos;
import com.proyecto.supply_core.plataforma.enums.AccionPlataforma;
import com.proyecto.supply_core.plataforma.enums.EstadoSuscripcion;
import com.proyecto.supply_core.plataforma.service.EmpresaPlataformaService;
import com.proyecto.supply_core.plataforma.service.PlanPlataformaService;
import com.proyecto.supply_core.plataforma.service.PlataformaAuditoriaService;

@WebMvcTest({ PlataformaEmpresaController.class, PlataformaPlanController.class, PlataformaAuditoriaController.class })
class PlataformaConsolaControllerTest extends WebTestBase {

    @MockitoBean
    private EmpresaPlataformaService empresaService;

    @MockitoBean
    private PlanPlataformaService planService;

    @MockitoBean
    private PlataformaAuditoriaService auditoriaService;

    private static EmpresaPlataformaResponse empresa() {
        return new EmpresaPlataformaResponse(8L, "Ferretería El Tornillo", "900456789-1", "el-tornillo", "BASICO",
                "Básico", EstadoSuscripcion.ACTIVA, null, LocalDateTime.now());
    }

    private static EmpresaDetalleResponse detalle() {
        return new EmpresaDetalleResponse(empresa(),
                List.of(new EmpresaDetalleResponse.ModuloEmpresa("COMPRAS", "Compras", true, null, true)));
    }

    private static final String ALTA = """
            {"razonSocial":"Ferrecentro","nit":"901223114-5","codigoPublico":"ferrecentro","planCodigo":"BASICO",
             "adminNombre":"Laura Gómez","adminCorreo":"laura@ferrecentro.co","adminPassword":"Clave12345"}""";

    @Test
    void sinTokenDeLaPlataformaNadaDeLaConsolaFunciona() throws Exception {
        mvc.perform(get("/api/plataforma/empresas")).andExpect(status().isUnauthorized());
        // el token de una empresa, aunque sea de un administrador, no abre la consola
        mvc.perform(get("/api/plataforma/empresas").header(HttpHeaders.AUTHORIZATION, bearer("ADMIN_SISTEMA")))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/plataforma/planes").header(HttpHeaders.AUTHORIZATION, bearer("ADMIN_COMPRAS")))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/plataforma/auditoria").header(HttpHeaders.AUTHORIZATION, bearerProveedor(40L)))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/plataforma/empresas").contentType(MediaType.APPLICATION_JSON).content(ALTA)
                        .header(HttpHeaders.AUTHORIZATION, bearer("ADMIN_SISTEMA")))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(empresaService, planService, auditoriaService);
    }

    @Test
    void elDuenoListaYVeElDetalleDeLasEmpresas() throws Exception {
        when(empresaService.listar()).thenReturn(List.of(empresa()));
        when(empresaService.obtener(8L)).thenReturn(detalle());

        mvc.perform(get("/api/plataforma/empresas").header(HttpHeaders.AUTHORIZATION, bearerPlataforma()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].codigoPublico").value("el-tornillo"))
                .andExpect(jsonPath("$[0].estado").value("ACTIVA"));
        mvc.perform(get("/api/plataforma/empresas/8").header(HttpHeaders.AUTHORIZATION, bearerPlataforma()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modulos[0].codigo").value("COMPRAS"));
        mvc.perform(get("/api/plataforma/empresas/0").header(HttpHeaders.AUTHORIZATION, bearerPlataforma()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void elAltaSeValidaAntesDelService() throws Exception {
        when(empresaService.crear(any())).thenReturn(detalle());

        mvc.perform(post("/api/plataforma/empresas").contentType(MediaType.APPLICATION_JSON).content(ALTA)
                        .header(HttpHeaders.AUTHORIZATION, bearerPlataforma()))
                .andExpect(status().isCreated());
        verify(empresaService).crear(any());

        // el código de ingreso es texto libre: espacios, mayúsculas, tildes y números
        mvc.perform(post("/api/plataforma/empresas").contentType(MediaType.APPLICATION_JSON)
                        .content(ALTA.replace("\"ferrecentro\"", "\"Ferretería Don Martín 2\""))
                        .header(HttpHeaders.AUTHORIZATION, bearerPlataforma()))
                .andExpect(status().isCreated());

        String[] malos = {
                ALTA.replace("901223114-5", "12"),
                ALTA.replace("\"ferrecentro\"", "\"Ferre/Centro\""),
                ALTA.replace("Clave12345", "corta1"),
                ALTA.replace("Clave12345", "sinnumeroaqui"),
                ALTA.replace("laura@ferrecentro.co", "no-es-correo"),
                ALTA.replace("\"planCodigo\":\"BASICO\",", ""),
                ALTA.replace("\"BASICO\"", "\"BASICO\",\"estadoInicial\":\"INVENTADO\"") };
        for (String malo : malos) {
            mvc.perform(post("/api/plataforma/empresas").contentType(MediaType.APPLICATION_JSON).content(malo)
                            .header(HttpHeaders.AUTHORIZATION, bearerPlataforma()))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void planModulosYEstadoDeUnaEmpresa() throws Exception {
        when(empresaService.cambiarPlan(eq(8L), any())).thenReturn(detalle());
        when(empresaService.excepcionDeModulo(eq(8L), any(), any())).thenReturn(detalle());
        when(empresaService.cambiarEstado(eq(8L), any(), any())).thenReturn(detalle());
        String auth = bearerPlataforma();

        mvc.perform(put("/api/plataforma/empresas/8/plan").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"planCodigo\":\"PROFESIONAL\"}").header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(status().isOk());
        verify(empresaService).cambiarPlan(8L, "PROFESIONAL");
        mvc.perform(put("/api/plataforma/empresas/8/modulos/REPORTES").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"habilitado\":true}").header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(status().isOk());
        verify(empresaService).excepcionDeModulo(8L, "REPORTES", true);
        mvc.perform(put("/api/plataforma/empresas/8/modulos/REPORTES").contentType(MediaType.APPLICATION_JSON)
                        .content("{}").header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(status().isOk());
        verify(empresaService).excepcionDeModulo(eq(8L), eq("REPORTES"), isNull());
        mvc.perform(patch("/api/plataforma/empresas/8/estado").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estado\":\"SUSPENDIDA\",\"motivo\":\"No pagó\"}").header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(status().isOk());
        verify(empresaService).cambiarEstado(8L, EstadoSuscripcion.SUSPENDIDA, "No pagó");

        mvc.perform(put("/api/plataforma/empresas/8/plan").contentType(MediaType.APPLICATION_JSON).content("{}")
                        .header(HttpHeaders.AUTHORIZATION, auth)).andExpect(status().isBadRequest());
        mvc.perform(patch("/api/plataforma/empresas/8/estado").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estado\":\"INVENTADO\"}").header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(status().isBadRequest());
        mvc.perform(patch("/api/plataforma/empresas/8/estado").contentType(MediaType.APPLICATION_JSON).content("{}")
                        .header(HttpHeaders.AUTHORIZATION, auth)).andExpect(status().isBadRequest());
        mvc.perform(put("/api/plataforma/empresas/0/plan").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"planCodigo\":\"BASICO\"}").header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(status().isBadRequest());
    }

    @Test
    void planesYModulos() throws Exception {
        PlanDtos.Respuesta plan = new PlanDtos.Respuesta(1L, "BASICO", "Básico", true, List.of("COMPRAS"), 3);
        when(planService.listar()).thenReturn(List.of(plan));
        when(planService.modulos()).thenReturn(List.of(new PlanDtos.ModuloRespuesta("COMPRAS", "Compras", "desc")));
        when(planService.crear(any())).thenReturn(plan);
        when(planService.editar(eq(1L), any())).thenReturn(plan);
        String auth = bearerPlataforma();

        mvc.perform(get("/api/plataforma/planes").header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].empresas").value(3));
        mvc.perform(get("/api/plataforma/modulos").header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].codigo").value("COMPRAS"));
        mvc.perform(post("/api/plataforma/planes").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"codigo\":\"NUEVO_1\",\"nombre\":\"Nuevo\",\"modulos\":[\"COMPRAS\"]}")
                        .header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(status().isCreated());
        mvc.perform(put("/api/plataforma/planes/1").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"modulos\":[\"COMPRAS\",\"REPORTES\"],\"activo\":true}")
                        .header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(status().isOk());

        String[] malos = { "{}", "{\"codigo\":\"con espacio\",\"nombre\":\"x\",\"modulos\":[\"A\"]}",
                "{\"codigo\":\"OK\",\"nombre\":\"x\",\"modulos\":[]}", "{\"codigo\":\"OK\",\"modulos\":[\"A\"]}" };
        for (String malo : malos) {
            mvc.perform(post("/api/plataforma/planes").contentType(MediaType.APPLICATION_JSON).content(malo)
                            .header(HttpHeaders.AUTHORIZATION, auth))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void laAuditoriaGlobalSeConsultaPaginadaYFiltrada() throws Exception {
        when(auditoriaService.buscar(eq(8L), any())).thenReturn(new PageResponse<>(
                List.of(new AuditoriaPlataformaResponse(1L, 3L, AccionPlataforma.EMPRESA_ESTADO, 8L, "EXITO", "127.0.0.1",
                        "ACTIVA -> SUSPENDIDA", LocalDateTime.now())), 0, 20, 1L, 1));

        mvc.perform(get("/api/plataforma/auditoria?empresaId=8").header(HttpHeaders.AUTHORIZATION, bearerPlataforma()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido[0].accion").value("EMPRESA_ESTADO"));
        verify(auditoriaService).buscar(eq(8L), any());
        mvc.perform(get("/api/plataforma/auditoria?empresaId=0").header(HttpHeaders.AUTHORIZATION, bearerPlataforma()))
                .andExpect(status().isBadRequest());
    }
}
