package com.proyecto.supply_core.security.controller;

import static org.hamcrest.Matchers.hasItems;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.proyecto.supply_core.common.exception.NoAutenticadoException;
import com.proyecto.supply_core.common.web.WebTestBase;
import com.proyecto.supply_core.security.dto.LoginResponse;
import com.proyecto.supply_core.security.dto.UsuarioSesionResponse;
import com.proyecto.supply_core.security.service.AuthService;

@WebMvcTest(AuthController.class)
class AuthControllerTest extends WebTestBase {

    @MockitoBean
    private AuthService authService;

    @Test
    void loginEsPublicoYDevuelveToken() throws Exception {
        when(authService.login(any())).thenReturn(new LoginResponse("tok", "Bearer", 3600,
                new UsuarioSesionResponse(1L, "Ana", "ana@x.com", Set.of("COMPRADOR"), null)));

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"correo":"ana@x.com","password":"Clave1234"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("tok"))
                .andExpect(jsonPath("$.usuario.roles[0]").value("COMPRADOR"));
    }

    @Test
    void loginInvalidoDevuelve400ConCadaCampo() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"correo":"no-es-correo","password":""}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.detalles", hasItems(
                        "correo: no tiene un formato de correo válido", "password: es obligatoria")));
        verifyNoInteractions(authService);
    }

    @Test
    void credencialesMalasDevuelven401() throws Exception {
        when(authService.login(any())).thenThrow(new NoAutenticadoException("Credenciales inválidas"));

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"correo":"ana@x.com","password":"Mala1234"}"""))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"))
                .andExpect(jsonPath("$.mensaje").value("Credenciales inválidas"));
    }

    @Test
    void jsonMalFormadoDevuelve400() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{malo"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("PETICION_INVALIDA"));
    }

    @Test
    void contentTypeNoSoportadoDevuelve415() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.TEXT_PLAIN).content("hola"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.codigo").value("TIPO_NO_SOPORTADO"));
    }

    @Test
    void meSinTokenDevuelve401() throws Exception {
        mvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensaje").value("Debe iniciar sesión"));
    }

    @Test
    void meConTokenAlteradoDevuelve401() throws Exception {
        String token = bearer("COMPRADOR");
        mvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, token.substring(0, token.length() - 2) + "xx"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensaje").value("Token inválido"));
    }

    @Test
    void meConTokenDevuelveElUsuarioDelToken() throws Exception {
        mvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, bearer("COMPRADOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.roles[0]").value("COMPRADOR"));
    }
}
