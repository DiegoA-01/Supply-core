package com.proyecto.supply_core.plataforma.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.proyecto.supply_core.plataforma.dto.DesafioMfaResponse;
import com.proyecto.supply_core.plataforma.dto.LoginPlataformaRequest;
import com.proyecto.supply_core.plataforma.dto.MfaRequest;
import com.proyecto.supply_core.plataforma.dto.SesionPlataformaResponse;
import com.proyecto.supply_core.plataforma.service.PlataformaAuthService;

import jakarta.validation.Valid;

/**
 * Login del dueño de la plataforma, en dos pasos y con MFA obligatorio. Rutas públicas con límite de intentos por IP.
 */
@RestController
@RequestMapping("/api/plataforma/auth")
public class PlataformaAuthController {

    private final PlataformaAuthService authService;

    /**
     * @param authService lógica del login de plataforma
     */
    public PlataformaAuthController(PlataformaAuthService authService) {
        this.authService = authService;
    }

    /**
     * Paso 1: contraseña. Entrega el desafío de MFA (y el secreto para vincular la app la primera vez).
     *
     * @param request correo y contraseña
     * @return desafío de MFA
     */
    @PostMapping("/login")
    public DesafioMfaResponse login(@Valid @RequestBody LoginPlataformaRequest request) {
        return authService.login(request);
    }

    /**
     * Paso 2: código de la app de autenticación. Entrega la sesión.
     *
     * @param request desafío y código de 6 dígitos
     * @return sesión de plataforma
     */
    @PostMapping("/mfa")
    public SesionPlataformaResponse mfa(@Valid @RequestBody MfaRequest request) {
        return authService.verificarMfa(request);
    }
}
