package com.proyecto.supply_core.plataforma.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.proyecto.supply_core.common.exception.NoAutenticadoException;
import com.proyecto.supply_core.common.web.WebTestBase;
import com.proyecto.supply_core.plataforma.dto.DesafioMfaResponse;
import com.proyecto.supply_core.plataforma.dto.SesionPlataformaResponse;
import com.proyecto.supply_core.plataforma.service.PlataformaAuthService;

@WebMvcTest(PlataformaAuthController.class)
class PlataformaAuthControllerTest extends WebTestBase {

    @MockitoBean
    private PlataformaAuthService authService;

    @Test
    void elLoginEsPublicoYEntregaElDesafio() throws Exception {
        when(authService.login(any())).thenReturn(new DesafioMfaResponse("desafio-jwt", false, "SECRETO", "otpauth://x"));

        mvc.perform(post("/api/plataforma/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"correo\":\"dueno@supplycore.local\",\"password\":\"ClaveSegura1234\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.desafio").value("desafio-jwt"))
                .andExpect(jsonPath("$.mfaConfigurado").value(false))
                .andExpect(jsonPath("$.secretoMfa").value("SECRETO"));
        verify(authService).login(any());
    }

    @Test
    void elCodigoDeMfaEntregaLaSesion() throws Exception {
        when(authService.verificarMfa(any())).thenReturn(
                new SesionPlataformaResponse("jwt", "Bearer", 900, 3L, "dueno@supplycore.local", "Dueño"));

        mvc.perform(post("/api/plataforma/auth/mfa").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"desafio\":\"desafio-jwt\",\"codigo\":\"123456\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("jwt"))
                .andExpect(jsonPath("$.tipo").value("Bearer"));
    }

    @Test
    void unaCredencialInvalidaEsUn401() throws Exception {
        when(authService.login(any())).thenThrow(new NoAutenticadoException("Credenciales inválidas"));

        mvc.perform(post("/api/plataforma/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"correo\":\"dueno@supplycore.local\",\"password\":\"mala\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void sinCuerpoValidoNoSeLlegaAlService() throws Exception {
        String[] malos = { "{}", "{\"correo\":\"\",\"password\":\"x\"}", "{\"correo\":\"a@b.c\"}" };
        for (String malo : malos) {
            mvc.perform(post("/api/plataforma/auth/login").contentType(MediaType.APPLICATION_JSON).content(malo))
                    .andExpect(status().isBadRequest());
        }
        String[] malosMfa = { "{}", "{\"desafio\":\"x\",\"codigo\":\"12345\"}", "{\"desafio\":\"x\",\"codigo\":\"abcdef\"}",
                "{\"codigo\":\"123456\"}" };
        for (String malo : malosMfa) {
            mvc.perform(post("/api/plataforma/auth/mfa").contentType(MediaType.APPLICATION_JSON).content(malo))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(authService);
    }
}
