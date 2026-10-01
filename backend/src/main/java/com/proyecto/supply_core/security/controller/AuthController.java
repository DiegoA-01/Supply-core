package com.proyecto.supply_core.security.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.proyecto.supply_core.security.context.SesionActual;
import com.proyecto.supply_core.security.dto.LoginRequest;
import com.proyecto.supply_core.security.dto.LoginResponse;
import com.proyecto.supply_core.security.dto.UsuarioSesionResponse;
import com.proyecto.supply_core.security.service.AuthService;

import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import jakarta.validation.Valid;

/** Endpoints de autenticación. */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    /**
     * @param authService lógica de login
     */
    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * Inicia sesión (ruta pública).
     *
     * @param request correo y contraseña
     * @return token JWT y datos de sesión
     */
    @PostMapping("/login")
    @SecurityRequirements // Swagger no pide token aquí
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
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
