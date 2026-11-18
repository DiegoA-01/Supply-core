package com.proyecto.supply_core.auditoria.controller;

import static org.hamcrest.Matchers.hasItem;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.proyecto.supply_core.auditoria.dto.AuditoriaFiltro;
import com.proyecto.supply_core.auditoria.dto.AuditoriaResponse;
import com.proyecto.supply_core.auditoria.enums.AccionAuditoria;
import com.proyecto.supply_core.auditoria.enums.ResultadoAuditoria;
import com.proyecto.supply_core.auditoria.service.AuditoriaService;
import com.proyecto.supply_core.common.dto.PageResponse;
import com.proyecto.supply_core.common.web.WebTestBase;

@WebMvcTest(AuditoriaController.class)
class AuditoriaControllerTest extends WebTestBase {

    @MockitoBean
    private AuditoriaService auditoriaService;

    @Test
    void soloAuditorYAdminSistema() throws Exception {
        when(auditoriaService.buscar(any(), any())).thenReturn(new PageResponse<>(List.of(), 0, 50, 0, 0));

        mvc.perform(get("/api/auditoria").header(HttpHeaders.AUTHORIZATION, bearer("AUDITOR"))).andExpect(status().isOk());
        mvc.perform(get("/api/auditoria").header(HttpHeaders.AUTHORIZATION, bearer("ADMIN_SISTEMA"))).andExpect(status().isOk());
        mvc.perform(get("/api/auditoria").header(HttpHeaders.AUTHORIZATION, bearer("COMPRADOR"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/auditoria").header(HttpHeaders.AUTHORIZATION, bearerProveedor(4L))).andExpect(status().isForbidden());
    }

    @Test
    void filtrosLleganConvertidosYOrdenPorDefectoEsMasRecientePrimero() throws Exception {
        when(auditoriaService.buscar(any(), any())).thenReturn(new PageResponse<>(List.of(
                new AuditoriaResponse(1L, 3L, "Ana", "a@x.com", "USUARIO", 3L, "LOGIN", ResultadoAuditoria.FALLO,
                        "10.0.0.1", "motivo=contraseña incorrecta", LocalDateTime.of(2026, 10, 1, 9, 0))),
                0, 50, 1, 1));

        mvc.perform(get("/api/auditoria?accion=LOGIN&resultado=FALLO&usuarioId=3&desde=2026-10-01T00:00:00")
                        .header(HttpHeaders.AUTHORIZATION, bearer("AUDITOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido[0].usuarioNombre").value("Ana"))
                .andExpect(jsonPath("$.contenido[0].resultado").value("FALLO"));

        ArgumentCaptor<AuditoriaFiltro> filtro = ArgumentCaptor.forClass(AuditoriaFiltro.class);
        ArgumentCaptor<Pageable> pagina = ArgumentCaptor.forClass(Pageable.class);
        verify(auditoriaService).buscar(filtro.capture(), pagina.capture());
        assertEquals(AccionAuditoria.LOGIN, filtro.getValue().accion());
        assertEquals(3L, filtro.getValue().usuarioId());
        assertEquals(LocalDateTime.of(2026, 10, 1, 0, 0), filtro.getValue().desde());
        assertEquals("creadoEn: DESC", pagina.getValue().getSort().toString());
    }

    @Test
    void valoresInvalidosEnFiltrosDevuelven400() throws Exception {
        mvc.perform(get("/api/auditoria?accion=BORRAR_TODO").header(HttpHeaders.AUTHORIZATION, bearer("AUDITOR")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detalles", hasItem("accion: tiene un valor inválido")));
        mvc.perform(get("/api/auditoria?desde=ayer").header(HttpHeaders.AUTHORIZATION, bearer("AUDITOR")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detalles", hasItem("desde: tiene un valor inválido")));
        mvc.perform(get("/api/auditoria?desde=2026-10-02T00:00:00&hasta=2026-10-01T00:00:00")
                        .header(HttpHeaders.AUTHORIZATION, bearer("AUDITOR")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detalles", hasItem("rangoValido: la fecha 'desde' no puede ser posterior a 'hasta'")));
        verifyNoInteractions(auditoriaService);
    }

    @Test
    void laAuditoriaNoSePuedeModificarPorApi() throws Exception {
        mvc.perform(post("/api/auditoria").header(HttpHeaders.AUTHORIZATION, bearer("ADMIN_SISTEMA")))
                .andExpect(status().isMethodNotAllowed());
    }
}
