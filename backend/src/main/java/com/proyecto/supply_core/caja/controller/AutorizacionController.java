package com.proyecto.supply_core.caja.controller;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.proyecto.supply_core.caja.dto.AutorizacionRequest;
import com.proyecto.supply_core.caja.dto.AutorizacionResponse;
import com.proyecto.supply_core.caja.dto.PinRequest;
import com.proyecto.supply_core.caja.service.AutorizacionService;
import com.proyecto.supply_core.security.annotation.RequiereModulo;
import com.proyecto.supply_core.security.annotation.RequiereRol;
import com.proyecto.supply_core.security.annotation.UsuarioInterno;
import com.proyecto.supply_core.security.context.Modulos;
import com.proyecto.supply_core.security.context.Roles;

import jakarta.validation.Valid;

/**
 * Autorizaciones con PIN de supervisor (spec 003). La pide la sesión del cajero; el supervisor se identifica con su
 * correo y su PIN en esa misma pantalla. La ruta tiene límite de intentos por IP ({@code RateLimitFilter}).
 */
@RequiereModulo(Modulos.CAJA)
@RestController
@RequestMapping("/api/autorizaciones")
@UsuarioInterno
public class AutorizacionController {

    private final AutorizacionService autorizacionService;

    /**
     * @param autorizacionService lógica de autorizaciones y PIN
     */
    public AutorizacionController(AutorizacionService autorizacionService) {
        this.autorizacionService = autorizacionService;
    }

    /**
     * @param request correo y PIN del supervisor, acción, turno y referencia
     * @return permiso de un solo uso (HTTP 201)
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @RequiereRol({ Roles.CAJERO, Roles.SUPERVISOR_VENTAS, Roles.ADMIN_SISTEMA })
    public AutorizacionResponse otorgar(@Valid @RequestBody AutorizacionRequest request) {
        return autorizacionService.otorgar(request);
    }

    /**
     * @param request contraseña actual y PIN nuevo
     */
    @PutMapping("/mi-pin")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @RequiereRol({ Roles.SUPERVISOR_VENTAS, Roles.ADMIN_SISTEMA })
    public void cambiarPin(@Valid @RequestBody PinRequest request) {
        autorizacionService.cambiarPin(request);
    }

    /**
     * @return {@code {"tienePin": true|false}} para la sesión
     */
    @GetMapping("/mi-pin")
    @RequiereRol({ Roles.SUPERVISOR_VENTAS, Roles.ADMIN_SISTEMA })
    public Map<String, Boolean> tienePin() {
        return Map.of("tienePin", autorizacionService.tienePin());
    }
}
