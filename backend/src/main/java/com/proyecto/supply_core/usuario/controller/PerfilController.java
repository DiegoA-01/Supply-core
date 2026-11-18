package com.proyecto.supply_core.usuario.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.proyecto.supply_core.usuario.dto.CambioPasswordRequest;
import com.proyecto.supply_core.usuario.dto.UsuarioResponse;
import com.proyecto.supply_core.usuario.service.UsuarioService;

import jakarta.validation.Valid;

/** Lo que cualquier usuario autenticado puede hacer sobre su propia cuenta (sin rol específico). */
@RestController
@RequestMapping("/api/perfil")
public class PerfilController {

    private final UsuarioService usuarioService;

    /**
     * @param usuarioService lógica de usuarios
     */
    public PerfilController(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    /**
     * Perfil del usuario logueado.
     *
     * @return datos propios
     */
    @GetMapping
    public UsuarioResponse perfil() {
        return usuarioService.perfil();
    }

    /**
     * Cambia la contraseña propia.
     *
     * @param request contraseña actual y nueva
     */
    @PutMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cambiarPassword(@Valid @RequestBody CambioPasswordRequest request) {
        usuarioService.cambiarPasswordPropia(request);
    }
}
