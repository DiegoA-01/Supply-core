package com.proyecto.supply_core.security.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.proyecto.supply_core.common.tenant.EmpresaContexto;
import com.proyecto.supply_core.empresa.repository.EmpresaRepository;
import com.proyecto.supply_core.security.context.SesionActual;
import com.proyecto.supply_core.security.dto.LoginRequest;
import com.proyecto.supply_core.security.dto.LoginResponse;
import com.proyecto.supply_core.security.dto.RefreshRequest;
import com.proyecto.supply_core.security.dto.UsuarioSesionResponse;
import com.proyecto.supply_core.security.service.AuthService;

import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import jakarta.validation.Valid;

/** Endpoints de autenticación. */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final EmpresaRepository empresaRepository;

    /**
     * @param authService       lógica de login
     * @param empresaRepository resuelve la empresa por su código público
     */
    public AuthController(AuthService authService, EmpresaRepository empresaRepository) {
        this.authService = authService;
        this.empresaRepository = empresaRepository;
    }

    /**
     * Inicia sesión (ruta pública). Primero se resuelve la empresa por su código (esa consulta corre sin
     * empresa) y el login completo corre dentro de ella: así solo se busca al usuario entre los de esa empresa.
     * Un código inexistente responde igual que una clave mala.
     *
     * @param request código de empresa, correo y contraseña
     * @return token JWT y datos de sesión
     */
    @PostMapping("/login")
    @SecurityRequirements // Swagger no pide token aquí
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        Long empresaId = empresaRepository.idPorCodigoPublico(request.empresa().trim()).orElse(null);
        if (empresaId == null) {
            return authService.loginEmpresaDesconocida(request);
        }
        return EmpresaContexto.como(empresaId, () -> authService.login(request));
    }

    /**
     * Renueva la sesión (ruta pública: el refresh token es la credencial). Cada token sirve una sola vez.
     *
     * @param request refresh token vigente
     * @return JWT nuevo y refresh token nuevo
     */
    @PostMapping("/refresh")
    @SecurityRequirements
    public LoginResponse refrescar(@Valid @RequestBody RefreshRequest request) {
        return authService.refrescar(request.refreshToken());
    }

    /**
     * Cierra la sesión del dispositivo (revoca su refresh token).
     *
     * @param request refresh token de esa sesión
     */
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cerrarSesion(@Valid @RequestBody RefreshRequest request) {
        authService.cerrarSesion(request.refreshToken(), SesionActual.usuario().id());
    }

    /** Cierra todas las sesiones del usuario en todos sus dispositivos. */
    @PostMapping("/logout-todo")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cerrarTodas() {
        authService.cerrarTodas(SesionActual.usuario().id());
    }

    /**
     * Devuelve el dueño del token (Angular lo usa para recuperar la sesión al recargar).
     *
     * @return datos de sesión
     */
    @GetMapping("/me")
    public UsuarioSesionResponse me() {
        return UsuarioSesionResponse.of(SesionActual.usuario());
    }
}
