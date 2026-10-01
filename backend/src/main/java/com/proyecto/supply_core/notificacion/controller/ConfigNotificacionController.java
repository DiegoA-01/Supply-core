package com.proyecto.supply_core.notificacion.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.proyecto.supply_core.notificacion.dto.ConfigNotificacionRequest;
import com.proyecto.supply_core.notificacion.dto.ConfigNotificacionResponse;
import com.proyecto.supply_core.notificacion.service.NotificacionService;
import com.proyecto.supply_core.security.annotation.RequiereRol;
import com.proyecto.supply_core.security.context.Roles;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;

/**
 * Configuración de canales por evento (ADMIN_SISTEMA). Las filas vienen de las migraciones:
 * solo se activan o desactivan.
 */
@RestController
@RequestMapping("/api/config-notificaciones")
@RequiereRol(Roles.ADMIN_SISTEMA)
public class ConfigNotificacionController {

    private final NotificacionService notificacionService;

    /**
     * @param notificacionService lógica de notificaciones
     */
    public ConfigNotificacionController(NotificacionService notificacionService) {
        this.notificacionService = notificacionService;
    }

    /**
     * Lista la configuración.
     *
     * @return filas por evento y canal
     */
    @GetMapping
    public List<ConfigNotificacionResponse> listar() {
        return notificacionService.configuracion();
    }

    /**
     * Activa o desactiva un canal para un evento.
     *
     * @param id  id positivo
     * @param req nuevo estado
     * @return configuración actualizada
     */
    @PatchMapping("/{id}")
    public ConfigNotificacionResponse cambiar(@PathVariable @Positive(message = "debe ser un id válido") Long id,
            @Valid @RequestBody ConfigNotificacionRequest req) {
        return notificacionService.cambiarConfiguracion(id, req.activo());
    }
}
