package com.proyecto.supply_core.security.controller;

import static org.hamcrest.Matchers.hasItems;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.proyecto.supply_core.common.exception.NoAutenticadoException;
import com.proyecto.supply_core.common.web.WebTestBase;
import com.proyecto.supply_core.security.dto.LoginResponse;
import com.proyecto.supply_core.security.dto.UsuarioSesionResponse;
import com.proyecto.supply_core.security.dto.ConfiguracionMfaResponse;
import com.proyecto.supply_core.security.jwt.DesafioMfaEmpresa;
import com.proyecto.supply_core.security.service.AuthService;
import com.proyecto.supply_core.security.service.MfaUsuarioService;
import com.proyecto.supply_core.security.service.RecuperacionPasswordService;

@WebMvcTest(AuthController.class)
class AuthControllerTest extends WebTestBase {

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private RecuperacionPasswordService recuperacionService;

    @MockitoBean
    private MfaUsuarioService mfaService;

    @BeforeEach
    void empresaDemo() {
        when(empresaRepository.idPorCodigoPublico("demo")).thenReturn(Optional.of(1L));
    }

    @Test
    void loginEsPublicoYDevuelveToken() throws Exception {
        when(authService.login(any())).thenReturn(new LoginResponse("tok", "Bearer", 3600,
                new UsuarioSesionResponse(1L, "Ana", "ana@x.com", Set.of("COMPRADOR"), null), "ref"));

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"empresa":"demo","correo":"ana@x.com","password":"Clave1234"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("tok"))
                .andExpect(jsonPath("$.refreshToken").value("ref"))
                .andExpect(jsonPath("$.usuario.roles[0]").value("COMPRADOR"));
    }

    @Test
    void refreshEsPublicoYEntregaTokensNuevos() throws Exception {
        when(authService.refrescar("viejo")).thenReturn(new LoginResponse("tok2", "Bearer", 900,
                new UsuarioSesionResponse(1L, "Ana", "ana@x.com", Set.of("COMPRADOR"), null), "ref2"));

        mvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"viejo"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("tok2"))
                .andExpect(jsonPath("$.refreshToken").value("ref2"));
    }

    @Test
    void refreshSinCuerpoValidoDevuelve400() throws Exception {
        mvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detalles", hasItems("refreshToken: es obligatorio")));
        verifyNoInteractions(authService);
    }

    @Test
    void logoutExigeSesionYCierraLaDelRefreshToken() throws Exception {
        mvc.perform(post("/api/auth/logout").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"abc"}"""))
                .andExpect(status().isUnauthorized());

        mvc.perform(post("/api/auth/logout").header(HttpHeaders.AUTHORIZATION, bearer("COMPRADOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"abc"}"""))
                .andExpect(status().isNoContent());
        verify(authService).cerrarSesion("abc", 1L);
    }

    @Test
    void logoutTodoCierraTodasLasSesionesDelUsuario() throws Exception {
        mvc.perform(post("/api/auth/logout-todo").header(HttpHeaders.AUTHORIZATION, bearer("COMPRADOR")))
                .andExpect(status().isNoContent());
        verify(authService).cerrarTodas(1L);
    }

    @Test
    void loginConEmpresaDesconocidaDevuelve401SinLlamarAlLoginNormal() throws Exception {
        when(authService.loginEmpresaDesconocida(any())).thenThrow(new NoAutenticadoException("Credenciales inválidas"));

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"empresa":"no-existe","correo":"ana@x.com","password":"Clave1234"}"""))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensaje").value("Credenciales inválidas"));
        verify(authService, never()).login(any());
    }

    @Test
    void loginInvalidoDevuelve400ConCadaCampo() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"empresa":"demo","correo":"no-es-correo","password":""}"""))
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
                                {"empresa":"demo","correo":"ana@x.com","password":"Mala1234"}"""))
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

    @Test
    void recuperarEsPublicoYRespondeLoMismoExistaONoLaEmpresa() throws Exception {
        when(empresaRepository.idPorCodigoPublico("no-existe")).thenReturn(Optional.empty());

        mvc.perform(post("/api/auth/recuperar").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"empresa":"demo","correo":"ana@x.com"}"""))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.mensaje").value("Si la cuenta existe, te enviamos un correo con las instrucciones"));
        verify(recuperacionService).solicitar("ana@x.com");

        mvc.perform(post("/api/auth/recuperar").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"empresa":"no-existe","correo":"ana@x.com"}"""))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.mensaje").value("Si la cuenta existe, te enviamos un correo con las instrucciones"));
        verify(recuperacionService, org.mockito.Mockito.times(1)).solicitar(any());
    }

    @Test
    void recuperarConCorreoMalEscritoDevuelve400() throws Exception {
        mvc.perform(post("/api/auth/recuperar").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"empresa":"demo","correo":"no-es-correo"}"""))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(recuperacionService);
    }

    @Test
    void restablecerCambiaLaClaveConElToken() throws Exception {
        mvc.perform(post("/api/auth/restablecer").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"empresa":"demo","token":"abc","passwordNueva":"NuevaClave1"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mensaje").value("Contraseña actualizada: ya puedes iniciar sesión"));
        verify(recuperacionService).restablecer("abc", "NuevaClave1");
    }

    @Test
    void restablecerConTokenOEmpresaInvalidosDevuelve401() throws Exception {
        doThrow(new NoAutenticadoException("El enlace de recuperación no es válido o ya venció"))
                .when(recuperacionService).restablecer(any(), any());
        when(empresaRepository.idPorCodigoPublico("no-existe")).thenReturn(Optional.empty());

        mvc.perform(post("/api/auth/restablecer").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"empresa":"demo","token":"malo","passwordNueva":"NuevaClave1"}"""))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/restablecer").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"empresa":"no-existe","token":"abc","passwordNueva":"NuevaClave1"}"""))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void restablecerConClaveDebilNoLlegaAlService() throws Exception {
        mvc.perform(post("/api/auth/restablecer").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"empresa":"demo","token":"abc","passwordNueva":"corta"}"""))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(recuperacionService);
    }

    @Test
    void elLoginConMfaEntregaElDesafioYElSegundoPasoLaSesion() throws Exception {
        when(authService.login(any())).thenReturn(LoginResponse.conDesafio("desafio-jwt", false, "SECRETO", "otpauth://x"));
        when(authService.leerDesafio("desafio-jwt")).thenReturn(new DesafioMfaEmpresa(5L, 1L));
        when(authService.verificarMfa(5L, "123456")).thenReturn(new LoginResponse("tok", "Bearer", 900,
                new UsuarioSesionResponse(5L, "Ana", "ana@x.com", Set.of("ADMIN_SISTEMA"), null), "ref"));

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"empresa":"demo","correo":"ana@x.com","password":"Clave1234"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.desafioMfa").value("desafio-jwt"))
                .andExpect(jsonPath("$.mfaConfigurado").value(false))
                .andExpect(jsonPath("$.secretoMfa").value("SECRETO"))
                .andExpect(jsonPath("$.token").doesNotExist());

        mvc.perform(post("/api/auth/mfa").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"desafio":"desafio-jwt","codigo":"123456"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("tok"))
                .andExpect(jsonPath("$.refreshToken").value("ref"));
    }

    @Test
    void elSegundoPasoRechazaUnCodigoMalFormadoYUnDesafioQueNoSirve() throws Exception {
        mvc.perform(post("/api/auth/mfa").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"desafio":"d","codigo":"12ab"}"""))
                .andExpect(status().isBadRequest());
        when(authService.leerDesafio("basura")).thenThrow(new NoAutenticadoException("Token inválido"));
        mvc.perform(post("/api/auth/mfa").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"desafio":"basura","codigo":"123456"}"""))
                .andExpect(status().isUnauthorized());
        verify(authService, never()).verificarMfa(any(), any());
    }

    @Test
    void vincularElSegundoFactorExigeSesion() throws Exception {
        when(mfaService.configurar()).thenReturn(new ConfiguracionMfaResponse("SECRETO", "otpauth://x"));

        mvc.perform(post("/api/auth/mfa/configurar")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/mfa/configurar").header(HttpHeaders.AUTHORIZATION, bearer("COMPRADOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.secretoMfa").value("SECRETO"));

        mvc.perform(post("/api/auth/mfa/activar").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"codigo":"123456"}"""))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/mfa/activar").header(HttpHeaders.AUTHORIZATION, bearer("COMPRADOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"codigo":"123456"}"""))
                .andExpect(status().isNoContent());
        verify(mfaService).activar("123456");
    }
}
